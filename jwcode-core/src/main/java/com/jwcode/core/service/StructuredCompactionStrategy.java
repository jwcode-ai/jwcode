package com.jwcode.core.service;

import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.Message;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StructuredCompactionStrategy — 强制 XML 结构化输出的上下文压缩策略。
 *
 * <p>对标 Claude Code 9 段结构化摘要 + drafting scratchpad 模式。
 * 两阶段输出：&lt;analysis&gt; 草稿纸（后处理删除）+ &lt;summary&gt; 正式摘要。</p>
 *
 * <pre>
 * &lt;analysis&gt;[草稿纸 — 将被删除]&lt;/analysis&gt;
 * &lt;summary&gt;
 *   &lt;primary_request&gt;...&lt;/primary_request&gt;
 *   &lt;technical_concepts&gt;...&lt;/technical_concepts&gt;
 *   &lt;files_and_code&gt;...&lt;/files_and_code&gt;
 *   &lt;errors_and_fixes&gt;...&lt;/errors_and_fixes&gt;
 *   &lt;problem_solving&gt;...&lt;/problem_solving&gt;
 *   &lt;all_user_messages&gt;...&lt;/all_user_messages&gt;
 *   &lt;pending_tasks&gt;...&lt;/pending_tasks&gt;
 *   &lt;current_work&gt;...&lt;/current_work&gt;
 *   &lt;optional_next_step&gt;...&lt;/optional_next_step&gt;
 * &lt;/summary&gt;
 * </pre>
 *
 * <p><b>优先级体系（从高到低）</b>：</p>
 * <ol>
 *   <li>primary_request (6) — 用户核心需求，不可丢失</li>
 *   <li>errors_and_fixes (5) — 错误与修复方案，避免重复犯错</li>
 *   <li>files_and_code (4) — 文件修改和代码变更</li>
 *   <li>current_work (3) — 压缩前正在做什么</li>
 *   <li>technical_concepts (2) — 技术概念和框架</li>
 *   <li>all_user_messages (2) — 用户反馈和意图变化</li>
 *   <li>pending_tasks (1) — 待办事项</li>
 * </ol>
 */
public class StructuredCompactionStrategy {

    private static final Logger logger = Logger.getLogger(StructuredCompactionStrategy.class.getName());

    // 保留尾部最近的消息数
    private static final int DEFAULT_TAIL_SIZE = 8;
    // 摘要 Prompt 最大消息数
    private static final int MAX_HEAD_MESSAGES = 40;
    // 摘要目标长度（9 段结构化 XML 需要更多空间）
    private static final int SUMMARY_TARGET_CHARS = 3000;
    // 预留 token 安全余量
    private static final int RESERVED_TOKENS = 4096;

    // 禁止工具调用声明 — 防止压缩 agent 尝试调用工具
    private static final String NO_TOOLS_PREAMBLE =
        "CRITICAL: Respond with TEXT ONLY. Do NOT call any tools.\n\n" +
        "- Do NOT use Read, Bash, Grep, Glob, Edit, Write, or ANY other tool.\n" +
        "- You already have all the context you need in the conversation above.\n" +
        "- Tool calls will be REJECTED and will waste your only turn — you will fail the task.\n" +
        "- Your entire response must be plain text: an <analysis> block followed by a <summary> block.\n";

    private static final String NO_TOOLS_TRAILER =
        "\n\nREMINDER: Do NOT call any tools. Respond with plain text only — " +
        "an <analysis> block followed by a <summary> block. " +
        "Tool calls will be rejected and you will fail the task.";

    // XML 标签的优先级（数值越高优先级越高）— 对标 Claude Code 9 段结构
    private static final Map<String, Integer> TAG_PRIORITY = new LinkedHashMap<>();
    static {
        TAG_PRIORITY.put("primary_request", 6);
        TAG_PRIORITY.put("errors_and_fixes", 5);
        TAG_PRIORITY.put("files_and_code", 4);
        TAG_PRIORITY.put("current_work", 3);
        TAG_PRIORITY.put("technical_concepts", 2);
        TAG_PRIORITY.put("all_user_messages", 2);
        TAG_PRIORITY.put("problem_solving", 2);
        TAG_PRIORITY.put("pending_tasks", 1);
        TAG_PRIORITY.put("optional_next_step", 1);
    }

    private static final List<String> GREETING_PATTERNS = List.of(
        "你好", "hi", "hello", "在吗", "请问", "谢谢", "感谢"
    );
    private static final List<String> TASK_KEYWORDS = List.of(
        "修复", "添加", "实现", "优化", "重构", "分析", "审计", "检查", "完成", "设计", "编写"
    );
    private static final List<String> CODE_INDICATORS = List.of(
        "/", ".java", ".py", ".js", ".ts", ".go", ".rs", ".cpp", ".c", ".h",
        "模块", "文件", "类", "方法", "函数", "包", "pom.xml", "build.gradle"
    );

    private final LLMService llmService;
    private final int tailSize;

    public StructuredCompactionStrategy(LLMService llmService) {
        this(llmService, DEFAULT_TAIL_SIZE);
    }

    public StructuredCompactionStrategy(LLMService llmService, int tailSize) {
        this.llmService = llmService;
        this.tailSize = Math.max(tailSize, 2);
    }

    /**
     * 同步压缩消息列表。
     */
    public List<Message> compact(List<Message> messages) {
        if (messages == null || messages.size() <= tailSize + 2) {
            return messages;
        }

        try {
            return compactAsync(messages).get();
        } catch (Exception e) {
            logger.warning("[StructuredCompaction] 同步压缩失败: " + e.getMessage());
            return messages;
        }
    }

    /**
     * 异步压缩消息列表。
     */
    public CompletableFuture<List<Message>> compactAsync(List<Message> messages) {
        if (messages == null || messages.size() <= tailSize + 2) {
            return CompletableFuture.completedFuture(messages);
        }

        int originalSize = messages.size();
        logger.info("[StructuredCompaction] 开始结构化压缩: original=" + originalSize);

        // 1. 保护前 2 条用户消息
        List<Message> preserved = extractFirstUserMessages(messages, 2);

        // 2. 分离尾部和头部
        List<Message> tail = extractTail(messages);
        List<Message> head = extractHead(messages, tail.size() + preserved.size());
        head.removeAll(preserved);

        // 额外保护任务目标消息
        Message taskGoalMsg = findTaskGoalMessage(messages);
        if (taskGoalMsg != null && !preserved.contains(taskGoalMsg) && !tail.contains(taskGoalMsg)) {
            preserved.add(taskGoalMsg);
            head.remove(taskGoalMsg);
        }
        tail.removeAll(preserved);

        // 3. 构建结构化压缩 Prompt
        String taskGoal = extractTaskGoal(messages);
        String compactionPrompt = buildStructuredPrompt(head, taskGoal);
        if (compactionPrompt == null || compactionPrompt.isBlank()) {
            logger.info("[StructuredCompaction] 头部无可压缩内容，仅截断");
            List<Message> fallback = new ArrayList<>(preserved);
            fallback.addAll(tail);
            return CompletableFuture.completedFuture(fallback);
        }

        // 4. 调用 LLM 生成结构化 XML 摘要
        List<LLMMessage> promptMessages = List.of(
            LLMMessage.system(compactionPrompt)
        );

        return llmService.chat(promptMessages).thenApply(response -> {
            String summary;
            if (response == null || response.hasError()) {
                String err = response != null ? response.getErrorMessage() : "null response";
                logger.warning("[StructuredCompaction] LLM 调用失败: " + err);
                summary = generateFallbackSummary(head, taskGoal);
            } else {
                summary = response.getContent();
                if (summary == null || summary.isBlank()) {
                    summary = generateFallbackSummary(head, taskGoal);
                } else {
                    // 后处理：删除 <analysis> 草稿纸块
                    summary = formatCompactSummary(summary);
                    // 验证并修复 XML 格式
                    summary = validateAndFixXml(summary, head, taskGoal);
                }
            }

            // 截断过长摘要
            if (summary.length() > SUMMARY_TARGET_CHARS * 2) {
                summary = priorityTruncate(summary);
            }

            // 5. 组装结果
            List<Message> result = new ArrayList<>(preserved);
            result.add(Message.createSystemMessage(
                "[历史对话摘要] 此次对话来自之前超出上下文的延续会话：\n\n" + summary.trim() + "\n\n"
                    + "[注意] 以上为此前 " + head.size()
                    + " 轮对话的压缩摘要，后续请基于摘要和最近对话继续。"
            ));
            result.addAll(tail);

            logger.info("[StructuredCompaction] 压缩完成: " + originalSize + " -> "
                + result.size() + " messages, summary=" + summary.length() + " chars");
            return result;
        }).exceptionally(e -> {
            logger.warning("[StructuredCompaction] 压缩异常: " + e.getMessage());
            List<Message> result = new ArrayList<>(preserved);
            result.add(Message.createSystemMessage(
                "[历史对话摘要] 此次对话来自之前超出上下文的延续会话。\n\n"
                    + generateFallbackSummary(head, taskGoal) + "\n\n"
                    + "[注意] 以上为此前 " + head.size()
                    + " 轮对话的压缩摘要（fallback），后续请基于摘要和最近对话继续。"
            ));
            result.addAll(tail);
            return result;
        });
    }

    // ==================== Prompt 构建 ====================

    /**
     * 构建强制 XML 结构化输出的压缩 Prompt — 对标 Claude Code 9 段结构 + drafting scratchpad。
     */
    private String buildStructuredPrompt(List<Message> headMessages, String taskGoal) {
        StringBuilder sb = new StringBuilder();

        // NO_TOOLS_PREAMBLE
        sb.append(NO_TOOLS_PREAMBLE).append("\n");

        // 压缩任务说明
        sb.append("You are summarizing a conversation between an AI agent and a user.\n");
        sb.append("All tasks described below are already completed.\n");
        sb.append("**DO NOT re-run, re-do or re-execute any of the tasks mentioned!**\n");
        sb.append("Use this summary only for context understanding.\n\n");

        // 用户任务目标
        if (taskGoal != null && !taskGoal.isBlank()) {
            sb.append("The user's primary request was: ").append(truncate(taskGoal, 300)).append("\n\n");
        }

        // 两阶段输出格式
        sb.append("Your response MUST consist of exactly two blocks:\n\n");
        sb.append("1. <analysis> — Scratchpad. Analyze each message chronologically:\n");
        sb.append("   - User intent and any shifts in requirements\n");
        sb.append("   - Technical decisions and rationale\n");
        sb.append("   - Code changes and file modifications\n");
        sb.append("   - Errors encountered and fixes applied\n");
        sb.append("   This block will be DISCARDED after processing.\n\n");
        sb.append("2. <summary> — The structured summary with these 9 sections:\n\n");

        sb.append("   <primary_request>\n");
        sb.append("   The user's core request. If intent shifted, note each shift.\n");
        sb.append("   Quote key phrases from the user.\n");
        sb.append("   </primary_request>\n\n");

        sb.append("   <technical_concepts>\n");
        sb.append("   Frameworks, libraries, APIs, patterns. Include version numbers.\n");
        sb.append("   </technical_concepts>\n\n");

        sb.append("   <files_and_code>\n");
        sb.append("   All files examined/modified/created with full paths.\n");
        sb.append("   What was changed and why. Key function signatures.\n");
        sb.append("   </files_and_code>\n\n");

        sb.append("   <errors_and_fixes>\n");
        sb.append("   Every error: exact message, root cause, fix, rejected approaches.\n");
        sb.append("   </errors_and_fixes>\n\n");

        sb.append("   <problem_solving>\n");
        sb.append("   Problems solved and those under investigation. Reasoning for decisions.\n");
        sb.append("   </problem_solving>\n\n");

        sb.append("   <all_user_messages>\n");
        sb.append("   ALL user messages that are not tool results, VERBATIM.\n");
        sb.append("   Do NOT paraphrase or omit any. Format: [Msg N] user: text\n");
        sb.append("   This is CRITICAL for preserving the user's feedback and changing intent.\n");
        sb.append("   </all_user_messages>\n\n");

        sb.append("   <pending_tasks>\n");
        sb.append("   All tasks not yet completed. TODO items, planned work.\n");
        sb.append("   </pending_tasks>\n\n");

        sb.append("   <current_work>\n");
        sb.append("   What was being worked on immediately before this summary.\n");
        sb.append("   Provide enough detail that work can continue seamlessly.\n");
        sb.append("   </current_work>\n\n");

        sb.append("   <optional_next_step>\n");
        sb.append("   Obvious next action if any. Include verbatim quotes from the\n");
        sb.append("   original conversation to prevent task drift. If none: 'No specific next step.'\n");
        sb.append("   </optional_next_step>\n\n");

        // 内容约束
        sb.append("--- CONTENT RULES ---\n");
        sb.append("- Primary language: Match the conversation language preference\n");
        sb.append("- Do NOT fabricate information — if unknown write \"无\" or \"N/A\"\n");
        sb.append("- Do NOT explain the XML structure, output XML directly\n");
        sb.append("- Ensure all XML tags are properly closed\n");
        sb.append("- all_user_messages MUST contain verbatim user messages\n\n");

        // 对话历史
        sb.append("--- CONVERSATION HISTORY TO SUMMARIZE ---\n\n");
        sb.append("(").append(headMessages.size()).append(" messages)\n\n");

        int validCount = 0;
        for (int i = 0; i < headMessages.size(); i++) {
            Message msg = headMessages.get(i);
            String content = extractCompactContent(msg);
            if (content == null || content.isBlank()) {
                continue;
            }
            validCount++;
            String role = msg.getRole().name().toLowerCase();
            if (content.length() > 600) {
                content = content.substring(0, 600) + "\n...[truncated]";
            }
            sb.append("[").append(role).append("] ").append(content).append("\n\n");
        }

        if (validCount == 0) {
            return null;
        }

        sb.append(NO_TOOLS_TRAILER);
        return sb.toString();
    }

    // ==================== XML 验证与修复 ====================

    /**
     * 验证 LLM 输出的 XML 结构，修复常见问题。
     * 对标 Claude Code 9 段标签体系。
     */
    private String validateAndFixXml(String raw, List<Message> headMessages, String taskGoal) {
        // 去除 Markdown 代码块包裹
        String cleaned = raw
            .replaceAll("```xml\\s*", "")
            .replaceAll("```\\s*", "")
            .trim();

        // 检查是否包含 summary 根标签
        if (!cleaned.contains("<summary>")) {
            logger.warning("[StructuredCompaction] LLM未输出summary标签，尝试包装");
            StringBuilder wrapped = new StringBuilder();
            wrapped.append("<summary>\n");
            wrapped.append("  <primary_request>").append(escapeXml(extractFirstSentence(cleaned))).append("</primary_request>\n");
            wrapped.append("  <technical_concepts>N/A</technical_concepts>\n");
            wrapped.append("  <files_and_code></files_and_code>\n");
            wrapped.append("  <errors_and_fixes></errors_and_fixes>\n");
            wrapped.append("  <problem_solving></problem_solving>\n");
            wrapped.append("  <all_user_messages>").append(escapeXml(truncate(cleaned, 300))).append("</all_user_messages>\n");
            wrapped.append("  <pending_tasks></pending_tasks>\n");
            wrapped.append("  <current_work></current_work>\n");
            wrapped.append("  <optional_next_step></optional_next_step>\n");
            wrapped.append("</summary>");
            return wrapped.toString();
        }

        // 确保必要的子标签存在
        String[] requiredTags = {"primary_request", "technical_concepts", "files_and_code",
            "errors_and_fixes", "problem_solving", "all_user_messages",
            "pending_tasks", "current_work", "optional_next_step"};
        for (String tag : requiredTags) {
            if (!cleaned.contains("<" + tag + ">") && !cleaned.contains("<" + tag + " ")) {
                cleaned = cleaned.replace("</summary>",
                    "  <" + tag + "></" + tag + ">\n</summary>");
            }
        }

        return cleaned;
    }

    // ==================== 优先级截断 ====================

    /**
     * 按优先级截断过长的 XML 摘要。
     * 低优先级标签的内容会被逐步截断或移除。
     */
    private String priorityTruncate(String xml) {
        if (xml.length() <= SUMMARY_TARGET_CHARS * 2) {
            return xml;
        }

        // 按优先级从低到高截断 (对标 9 段优先级)
        List<String> truncationOrder = List.of(
            "optional_next_step", "pending_tasks", "problem_solving",
            "technical_concepts", "all_user_messages", "current_work"
        );

        String result = xml;
        for (String tag : truncationOrder) {
            if (result.length() <= SUMMARY_TARGET_CHARS * 2) break;
            // 截断低优先级标签的内容：保留第一个子元素，其余替换为省略标记
            result = truncateTagContent(result, tag);
        }

        // 仍超长：全局截断
        if (result.length() > SUMMARY_TARGET_CHARS * 2) {
            result = result.substring(0, SUMMARY_TARGET_CHARS * 2) + "\n<!-- [摘要被截断，低优先级内容已省略] -->\n</summary>";
        }

        return result;
    }

    /**
     * 截断指定标签内的内容，仅保留第一个子元素。
     */
    private String truncateTagContent(String xml, String tagName) {
        Pattern pattern = Pattern.compile(
            "(<" + tagName + ">)(.*?)(</" + tagName + ">)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(xml);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(2).trim();
            if (content.length() > 100) {
                // 保留第一个子元素
                int firstClose = content.indexOf(">");
                if (firstClose > 0) {
                    int firstTagEnd = content.indexOf("</", firstClose);
                    if (firstTagEnd > 0) {
                        content = content.substring(0, firstTagEnd + content.substring(firstTagEnd).indexOf(">") + 1)
                            + "\n  <!-- [" + tagName + " 其余内容因优先级低被截断] -->";
                    } else {
                        content = content.substring(0, Math.min(100, content.length()))
                            + "...<!-- [截断] -->";
                    }
                } else {
                    content = content.substring(0, Math.min(100, content.length()))
                        + "...<!-- [截断] -->";
                }
            }
            matcher.appendReplacement(sb,
                "<" + tagName + ">\n  " + Matcher.quoteReplacement(content) + "\n</" + tagName + ">");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ==================== Fallback 摘要 ====================

    /**
     * 当 LLM 调用失败时，生成降级的结构化摘要 — 9 段对齐。
     */
    private String generateFallbackSummary(List<Message> headMessages, String taskGoal) {
        StringBuilder xml = new StringBuilder();
        xml.append("<summary>\n");

        xml.append("  <primary_request>")
           .append(escapeXml(taskGoal != null ? truncate(taskGoal, 200) : "未知任务"))
           .append("</primary_request>\n");

        xml.append("  <technical_concepts>N/A</technical_concepts>\n");

        // files_and_code — 从 assistant 消息中提取文件路径
        xml.append("  <files_and_code>\n");
        for (Message msg : headMessages) {
            if (msg.getRole() == Message.Role.ASSISTANT) {
                String text = msg.getTextContent();
                if (text != null && !text.isBlank()) {
                    xml.append("    <file path=\"unknown\">")
                       .append(escapeXml(truncate(text, 100)))
                       .append("</file>\n");
                }
            }
        }
        xml.append("  </files_and_code>\n");

        xml.append("  <errors_and_fixes></errors_and_fixes>\n");
        xml.append("  <problem_solving></problem_solving>\n");

        // all_user_messages — 从 head 中提取用户消息原文
        xml.append("  <all_user_messages>\n");
        int msgIdx = 1;
        for (Message msg : headMessages) {
            if (msg.getRole() == Message.Role.USER) {
                String text = msg.getTextContent();
                if (text != null && !text.isBlank()) {
                    xml.append("    [Msg ").append(msgIdx).append("] user: ")
                       .append(escapeXml(truncate(text, 150))).append("\n");
                    msgIdx++;
                }
            }
        }
        xml.append("  </all_user_messages>\n");

        xml.append("  <pending_tasks>请检查最近对话中未完成的任务</pending_tasks>\n");
        xml.append("  <current_work>压缩失败，请参考最近消息中的工作内容</current_work>\n");
        xml.append("  <optional_next_step></optional_next_step>\n");

        xml.append("  <!-- 此摘要由 fallback 机制自动生成，LLM 压缩失败 -->\n");
        xml.append("</summary>");
        return xml.toString();
    }

    // ==================== 辅助方法 ====================

    private List<Message> extractFirstUserMessages(List<Message> messages, int count) {
        List<Message> result = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.USER && result.size() < count) {
                result.add(msg);
            }
            if (result.size() >= count) break;
        }
        return result;
    }

    private List<Message> extractTail(List<Message> messages) {
        int startIndex = Math.max(0, messages.size() - tailSize);
        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }

    private List<Message> extractHead(List<Message> messages, int tailCount) {
        int headEnd = messages.size() - tailCount;
        if (headEnd <= 0) return List.of();
        int headStart = Math.max(0, headEnd - MAX_HEAD_MESSAGES);
        return new ArrayList<>(messages.subList(headStart, headEnd));
    }

    private Message findTaskGoalMessage(List<Message> allMessages) {
        Message best = null;
        int bestScore = -1;
        for (Message msg : allMessages) {
            if (msg.getRole() != Message.Role.USER) continue;
            String text = msg.getTextContent();
            if (text == null || text.isBlank()) continue;

            String trimmed = text.trim();
            String lower = trimmed.toLowerCase();
            boolean isGreeting = false;
            for (String p : GREETING_PATTERNS) {
                String pl = p.toLowerCase();
                if (lower.equals(pl) || lower.startsWith(pl + " ") || lower.startsWith(pl + "，")) {
                    if (trimmed.length() < 15) { isGreeting = true; break; }
                }
            }
            if (isGreeting || trimmed.length() < 10) continue;

            int score = trimmed.length() > 20 ? trimmed.length() : 0;
            for (String kw : TASK_KEYWORDS) { if (trimmed.contains(kw)) score += 50; }
            for (String ind : CODE_INDICATORS) { if (trimmed.contains(ind)) score += 30; }
            if (score > bestScore || (score == bestScore && best != null
                && trimmed.length() > best.getTextContent().trim().length())) {
                bestScore = score;
                best = msg;
            }
        }
        return best;
    }

    private String extractTaskGoal(List<Message> allMessages) {
        Message m = findTaskGoalMessage(allMessages);
        return m != null ? m.getTextContent().trim() : null;
    }

    private String extractCompactContent(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return msg.getTextContent();
        }
        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.TextContent tc) {
                sb.append(tc.getText()).append(" ");
            } else if (block instanceof Message.ToolResultContent trc) {
                String toolName = trc.getToolName();
                if ("AgentTool".equals(toolName)) {
                    String result = trc.getResult() != null ? trc.getResult().toString() : "";
                    if (result.length() > 300) result = result.substring(0, 300) + "...";
                    sb.append("[AgentTool: ").append(result).append("] ");
                } else {
                    sb.append("[工具:").append(toolName).append("] ");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 后处理压缩摘要 — 删除 &lt;analysis&gt; 草稿纸块（drafting scratchpad pattern）。
     */
    private String formatCompactSummary(String rawSummary) {
        if (rawSummary == null || rawSummary.isBlank()) {
            return rawSummary;
        }
        String formatted = rawSummary.replaceAll(
            "(?i)<analysis>[\\s\\S]*?</analysis>", "");
        formatted = formatted.replaceAll("\\n{3,}", "\n\n");
        return formatted.trim();
    }

    private String extractFirstSentence(String text) {
        if (text == null || text.isEmpty()) return "未知";
        for (char c : new char[]{'\n', '。', '！', '？', '.', '!', '?'}) {
            int idx = text.indexOf(c);
            if (idx > 0) return text.substring(0, idx).trim();
        }
        return truncate(text, 100);
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    public int getTailSize() {
        return tailSize;
    }
}
