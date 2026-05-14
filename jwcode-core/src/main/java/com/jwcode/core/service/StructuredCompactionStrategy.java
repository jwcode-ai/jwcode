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
 * <p>当对话过长时触发压缩，要求 LLM 按固定 XML 标签输出摘要。
 * 输出格式对标 KimiCode 的 compaction prompt：</p>
 *
 * <pre>
 * &lt;current_focus&gt;[正在做什么]&lt;/current_focus&gt;
 * &lt;environment&gt;[关键配置]&lt;/environment&gt;
 * &lt;completed_tasks&gt;
 *   &lt;task&gt;[描述]: [结果]&lt;/task&gt;
 * &lt;/completed_tasks&gt;
 * &lt;active_issues&gt;
 *   &lt;issue&gt;[问题]: [状态]&lt;/issue&gt;
 * &lt;/active_issues&gt;
 * &lt;code_state&gt;...&lt;/code_state&gt;
 * &lt;design_decisions&gt;...&lt;/design_decisions&gt;
 * &lt;todo_items&gt;...&lt;/todo_items&gt;
 * </pre>
 *
 * <p><b>优先级体系（从高到低）</b>：</p>
 * <ol>
 *   <li>当前任务状态 (current_focus) — 最高优先级，不可丢失</li>
 *   <li>错误与解决方案 (active_issues) — 避免重复犯错</li>
 *   <li>代码最终版本 (code_state) — 已修改的文件和关键代码</li>
 *   <li>系统上下文 (environment) — 项目配置、依赖版本</li>
 *   <li>设计决策 (design_decisions) — 架构选择及理由</li>
 *   <li>TODO 项 (todo_items) — 最低优先级，可截断</li>
 * </ol>
 */
public class StructuredCompactionStrategy {

    private static final Logger logger = Logger.getLogger(StructuredCompactionStrategy.class.getName());

    // 保留尾部最近的消息数
    private static final int DEFAULT_TAIL_SIZE = 8;
    // 摘要 Prompt 最大消息数
    private static final int MAX_HEAD_MESSAGES = 40;
    // 摘要目标长度（结构化 XML 需要更多空间）
    private static final int SUMMARY_TARGET_CHARS = 1200;
    // 预留 token 安全余量
    private static final int RESERVED_TOKENS = 4096;

    // XML 标签的优先级（数值越高优先级越高）
    private static final Map<String, Integer> TAG_PRIORITY = new LinkedHashMap<>();
    static {
        TAG_PRIORITY.put("current_focus", 6);
        TAG_PRIORITY.put("active_issues", 5);
        TAG_PRIORITY.put("code_state", 4);
        TAG_PRIORITY.put("environment", 3);
        TAG_PRIORITY.put("design_decisions", 2);
        TAG_PRIORITY.put("completed_tasks", 2);
        TAG_PRIORITY.put("todo_items", 1);
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
                "[结构化压缩摘要]\n" + summary.trim() + "\n\n"
                    + "[注意] 以上为此前 " + head.size() + " 轮对话的结构化摘要，"
                    + "请基于摘要和最近对话继续。"
            ));
            result.addAll(tail);

            logger.info("[StructuredCompaction] 压缩完成: " + originalSize + " -> "
                + result.size() + " messages, summary=" + summary.length() + " chars");
            return result;
        }).exceptionally(e -> {
            logger.warning("[StructuredCompaction] 压缩异常: " + e.getMessage());
            List<Message> result = new ArrayList<>(preserved);
            result.add(Message.createSystemMessage(
                "[结构化压缩摘要]\n" + generateFallbackSummary(head, taskGoal) + "\n\n"
                    + "[注意] 压缩异常，使用 fallback 摘要。"
            ));
            result.addAll(tail);
            return result;
        });
    }

    // ==================== Prompt 构建 ====================

    /**
     * 构建强制 XML 结构化输出的压缩 Prompt。
     */
    private String buildStructuredPrompt(List<Message> headMessages, String taskGoal) {
        StringBuilder sb = new StringBuilder();

        // ====== 系统指令：强制 XML 格式 ======
        sb.append("你是JWCode的上下文压缩专家。请将以下对话历史压缩为严格的结构化XML摘要。\n\n");

        sb.append("## 输出格式要求（必须严格遵守）\n\n");
        sb.append("你必须输出以下XML结构，不允许省略任何标签，不允许使用Markdown代码块包裹：\n\n");
        sb.append("<compaction_summary>\n");
        sb.append("  <current_focus>[当前正在执行的最高优先级任务，一句话概括]</current_focus>\n");
        sb.append("  <environment>\n");
        sb.append("    <project>[项目名称和类型]</project>\n");
        sb.append("    <language>[使用的编程语言]</language>\n");
        sb.append("    <dependencies>[关键依赖或框架]</dependencies>\n");
        sb.append("  </environment>\n");
        sb.append("  <completed_tasks>\n");
        sb.append("    <task status=\"success|failed\">[任务描述]: [结果摘要]</task>\n");
        sb.append("    <!-- 每个已完成任务一个task标签 -->\n");
        sb.append("  </completed_tasks>\n");
        sb.append("  <active_issues>\n");
        sb.append("    <issue severity=\"critical|major|minor\" status=\"open|in-progress|resolved\">[问题描述]: [当前状态]</issue>\n");
        sb.append("  </active_issues>\n");
        sb.append("  <code_state>\n");
        sb.append("    <file path=\"...\">[文件修改摘要]</file>\n");
        sb.append("  </code_state>\n");
        sb.append("  <design_decisions>\n");
        sb.append("    <decision>[设计决策]: [理由]</decision>\n");
        sb.append("  </design_decisions>\n");
        sb.append("  <todo_items>\n");
        sb.append("    <todo priority=\"high|medium|low\">[待办事项]</todo>\n");
        sb.append("  </todo_items>\n");
        sb.append("</compaction_summary>\n\n");

        // ====== 优先级约束 ======
        sb.append("## 优先级约束\n\n");
        sb.append("按以下优先级保留信息（高优先级信息必须详实，低优先级可简略）：\n");
        sb.append("1. **current_focus** — 当前任务状态（最高优先级，不可丢失）\n");
        sb.append("2. **active_issues** — 错误与解决方案（避免重复犯错）\n");
        sb.append("3. **code_state** — 代码最终版本（已修改的文件和关键代码变更）\n");
        sb.append("4. **environment** — 系统上下文（项目配置、依赖版本）\n");
        sb.append("5. **design_decisions** — 设计决策（架构选择及理由）\n");
        sb.append("6. **completed_tasks** — 已完成任务（只保留结果，不保留过程）\n");
        sb.append("7. **todo_items** — 待办事项（最低优先级，可截断）\n\n");

        // ====== 内容约束 ======
        sb.append("## 内容约束\n\n");
        sb.append("- 中文输出\n");
        sb.append("- current_focus 必须从最近的ASSISTANT消息中准确提取\n");
        sb.append("- environment 从项目配置文件和用户消息中提取\n");
        sb.append("- 不要编造信息，没有的内容写\"无\"\n");
        sb.append("- 不要解释XML结构，直接输出XML即可\n");
        sb.append("- 确保XML标签正确闭合\n\n");

        // ====== 任务目标注入 ======
        if (taskGoal != null && !taskGoal.isBlank()) {
            sb.append("## 用户原始任务目标\n\n");
            sb.append("```\n").append(truncate(taskGoal, 300)).append("\n```\n\n");
            sb.append("current_focus 必须准确反映此目标及其当前进度。\n\n");
        }

        // ====== 待压缩消息 ======
        sb.append("## 对话历史\n\n");
        sb.append("以下是需要压缩的 ").append(headMessages.size()).append(" 轮对话：\n\n");

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
                content = content.substring(0, 600) + "\n...[截断]";
            }
            sb.append("[").append(role).append("] ").append(content).append("\n\n");
        }

        if (validCount == 0) {
            return null;
        }

        sb.append("请输出结构化XML摘要：");
        return sb.toString();
    }

    // ==================== XML 验证与修复 ====================

    /**
     * 验证 LLM 输出的 XML 结构，修复常见问题。
     */
    private String validateAndFixXml(String raw, List<Message> headMessages, String taskGoal) {
        // 去除 Markdown 代码块包裹
        String cleaned = raw
            .replaceAll("```xml\\s*", "")
            .replaceAll("```\\s*", "")
            .trim();

        // 检查是否包含 compaction_summary 根标签
        if (!cleaned.contains("<compaction_summary>")) {
            // LLM 没有按格式输出，尝试提取有用内容
            logger.warning("[StructuredCompaction] LLM未输出XML格式，尝试包装");
            StringBuilder wrapped = new StringBuilder();
            wrapped.append("<compaction_summary>\n");
            wrapped.append("  <current_focus>").append(escapeXml(extractFirstSentence(cleaned))).append("</current_focus>\n");
            wrapped.append("  <environment><project>未知</project></environment>\n");
            wrapped.append("  <completed_tasks></completed_tasks>\n");
            wrapped.append("  <active_issues></active_issues>\n");
            wrapped.append("  <code_state></code_state>\n");
            wrapped.append("  <design_decisions></design_decisions>\n");
            wrapped.append("  <todo_items></todo_items>\n");
            wrapped.append("  <raw_summary>").append(escapeXml(truncate(cleaned, 500))).append("</raw_summary>\n");
            wrapped.append("</compaction_summary>");
            return wrapped.toString();
        }

        // 确保必要的子标签存在
        String[] requiredTags = {"current_focus", "environment", "completed_tasks",
            "active_issues", "code_state", "design_decisions", "todo_items"};
        for (String tag : requiredTags) {
            if (!cleaned.contains("<" + tag + ">") && !cleaned.contains("<" + tag + " ")) {
                cleaned = cleaned.replace("</compaction_summary>",
                    "  <" + tag + "></" + tag + ">\n</compaction_summary>");
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

        // 按优先级从低到高截断
        List<String> truncationOrder = List.of(
            "todo_items", "completed_tasks", "design_decisions",
            "code_state", "environment"
        );

        String result = xml;
        for (String tag : truncationOrder) {
            if (result.length() <= SUMMARY_TARGET_CHARS * 2) break;
            // 截断低优先级标签的内容：保留第一个子元素，其余替换为省略标记
            result = truncateTagContent(result, tag);
        }

        // 仍超长：全局截断
        if (result.length() > SUMMARY_TARGET_CHARS * 2) {
            result = result.substring(0, SUMMARY_TARGET_CHARS * 2) + "\n<!-- [摘要被截断，低优先级内容已省略] -->\n</compaction_summary>";
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
     * 当 LLM 调用失败时，生成降级的结构化摘要。
     */
    private String generateFallbackSummary(List<Message> headMessages, String taskGoal) {
        StringBuilder xml = new StringBuilder();
        xml.append("<compaction_summary>\n");

        // current_focus
        xml.append("  <current_focus>")
           .append(escapeXml(taskGoal != null ? truncate(taskGoal, 100) : "未知任务"))
           .append("</current_focus>\n");

        // environment
        xml.append("  <environment><project>未知</project></environment>\n");

        // completed_tasks
        xml.append("  <completed_tasks>\n");
        for (Message msg : headMessages) {
            if (msg.getRole() == Message.Role.ASSISTANT) {
                String text = msg.getTextContent();
                if (text != null && !text.isBlank()) {
                    xml.append("    <task status=\"unknown\">")
                       .append(escapeXml(truncate(text, 80)))
                       .append("</task>\n");
                }
            }
        }
        xml.append("  </completed_tasks>\n");

        // 其余空标签
        xml.append("  <active_issues></active_issues>\n");
        xml.append("  <code_state></code_state>\n");
        xml.append("  <design_decisions></design_decisions>\n");
        xml.append("  <todo_items></todo_items>\n");

        xml.append("  <!-- 此摘要由 fallback 机制自动生成，LLM 压缩失败 -->\n");
        xml.append("</compaction_summary>");
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
