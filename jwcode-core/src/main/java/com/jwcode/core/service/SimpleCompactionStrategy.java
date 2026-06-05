package com.jwcode.core.service;

import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * SimpleCompaction 策略 — Kimi Code 式上下文压缩。
 *
 * <p>核心思想：保留尾部最近 N 条消息（安全区），将其余历史通过 LLM 调用压缩为语义摘要。
 * 解决长会话中 prompt token 无限膨胀、TokenBudget 提前耗尽的问题。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>压缩时禁用工具调用，防止摘要过程误触工具</li>
 *   <li>过滤 reasoning/thinking 片段，仅保留文本内容</li>
 *   <li>摘要失败时优雅降级为 {@link ContextWindowManager} 截断策略</li>
 *   <li>预留 4K tokens 安全余量，确保压缩后还能继续对话</li>
 * </ul>
 *
 * <p>Token 银行联动：压缩成功后通过 {@link CompactCallback} 通知释放 Token 预算。</p>
 */
public class SimpleCompactionStrategy {

    /**
     * 压缩回调接口 — 用于 Token 银行联动。
     * 压缩成功后调用，通知释放对应数量的 Token 预算。
     */
    @FunctionalInterface
    public interface CompactCallback {
        /**
         * 压缩完成回调
         *
         * @param originalSize  原始消息数
         * @param compactedSize 压缩后消息数
         * @param estimatedTokensSaved 估计节省的 token 数
         */
        void onCompacted(int originalSize, int compactedSize, long estimatedTokensSaved);
    }

    private static final Logger logger = Logger.getLogger(SimpleCompactionStrategy.class.getName());

    // 保留尾部最近的消息数（默认 8 条 = 约 4 轮对话，确保 AgentTool 任务分配信息不被压缩）
    private static final int DEFAULT_TAIL_SIZE = 8;
    // 摘要 Prompt 最大消息数（防止 Prompt 本身过大）
    private static final int MAX_HEAD_MESSAGES = 40;
    // 摘要目标长度（字符）— 9 段结构化摘要需要更大空间
    private static final int SUMMARY_TARGET_CHARS = 2500;
    // 预留 token 安全余量
    private static final int RESERVED_TOKENS = 4096;

    // 禁止工具调用声明 — 首尾双重强调，防止压缩 agent 尝试调用工具
    // Sonnet 4.6 上无此声明时工具调用失败率 2.79%（vs Sonnet 4.5 的 0.01%）
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

    public SimpleCompactionStrategy(LLMService llmService) {
        this(llmService, DEFAULT_TAIL_SIZE);
    }

    public SimpleCompactionStrategy(LLMService llmService, int tailSize) {
        this.llmService = llmService;
        this.tailSize = Math.max(tailSize, 2);
    }

    /**
     * 同步压缩消息列表。
     *
     * @param messages 原始消息历史
     * @return 压缩后的消息列表；若压缩失败或不需要压缩，返回原列表
     */
    public List<Message> compact(List<Message> messages) {
        if (messages == null || messages.size() <= tailSize + 2) {
            return messages;
        }

        try {
            return compactAsync(messages).get();
        } catch (Exception e) {
            logger.warning("[SimpleCompaction] 同步压缩失败，降级为原列表: " + e.getMessage());
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
        logger.info("[SimpleCompaction] 开始压缩: original=" + originalSize + " messages");

        // 0. 保护前 2 条非系统消息（通常是用户初始任务描述），额外保护真实任务目标
        List<Message> preserved = extractFirstUserMessages(messages, 2);

        // 1. 分离尾部安全区（排除已保护的消息）
        List<Message> tail = extractTail(messages);
        List<Message> head = extractHead(messages, tail.size() + preserved.size());

        // 【修复】从 head 中排除已被保护的消息，避免重复压缩
        head.removeAll(preserved);

        // 【修复】额外保护被识别的真实任务目标消息（如果它不在前 2 条中且不在 tail 中）
        Message taskGoalMsg = findTaskGoalMessage(messages);
        if (taskGoalMsg != null && !preserved.contains(taskGoalMsg) && !tail.contains(taskGoalMsg)) {
            preserved.add(taskGoalMsg);
            head.remove(taskGoalMsg);
        }

        // 同时从 tail 中排除已被保护的消息，避免最终结果中出现重复
        tail.removeAll(preserved);

        // 2. 构建压缩 Prompt（注入被保护的任务目标）
        String taskGoal = extractTaskGoal(messages);
        String compactionPrompt = buildCompactionPrompt(head, taskGoal);
        if (compactionPrompt == null || compactionPrompt.isBlank()) {
            logger.info("[SimpleCompaction] 头部无可压缩内容，仅截断");
            List<Message> fallback = new ArrayList<>(preserved);
            fallback.addAll(tail);
            return CompletableFuture.completedFuture(fallback);
        }

        // 3. 调用 LLM 生成摘要（禁用工具调用）
        List<LLMMessage> promptMessages = List.of(
            LLMMessage.system(compactionPrompt)
        );

        return llmService.chat(promptMessages).thenApply(response -> {
            String summary;
            if (response == null || response.hasError()) {
                String err = response != null ? response.getErrorMessage() : "null response";
                logger.warning("[SimpleCompaction] LLM 摘要失败: " + err + "，使用 fallback 摘要");
                summary = "压缩失败，仅保留最近对话。";
            } else {
                summary = response.getContent();
                if (summary == null || summary.isBlank()) {
                    logger.warning("[SimpleCompaction] LLM 返回空摘要，使用 fallback 摘要");
                    summary = "压缩失败，仅保留最近对话。";
                }
            }

            // 后处理：删除 <analysis> 草稿纸块，仅保留 <summary> 正式内容
            summary = formatCompactSummary(summary);

            // 截断过长的摘要
            if (summary.length() > SUMMARY_TARGET_CHARS * 2) {
                summary = summary.substring(0, SUMMARY_TARGET_CHARS * 2) + "\n...[摘要已截断]";
            }

            // 4. 组装结果：保护消息 + 摘要(system) + 尾部
            List<Message> result = new ArrayList<>(preserved);
            Message summaryMsg = Message.createSystemMessage(
                "[历史对话摘要] 此次对话来自之前超出上下文的延续会话：\n\n" + summary.trim()
                    + "\n\n[注意] 以上为此前 " + head.size()
                    + " 轮对话的压缩摘要，后续请基于摘要和最近对话继续。"
            );
            // 压缩代际追踪：标记此消息为压缩产物，记录来源
            int maxGen = head.stream().mapToInt(Message::getCompactionGeneration).max().orElse(0);
            summaryMsg.setCompactionGeneration(maxGen + 1);
            summaryMsg.setCompressedFromIds(head.stream().map(Message::getId).collect(
                java.util.stream.Collectors.toSet()));
            result.add(summaryMsg);
            result.addAll(tail);

            logger.info("[SimpleCompaction] 压缩完成: " + originalSize + " -> " + result.size()
                + " messages, summary=" + summary.length() + " chars");
            return result;
        }).exceptionally(e -> {
            logger.warning("[SimpleCompaction] 压缩异常: " + e.getMessage() + "，使用 fallback 摘要");
            List<Message> result = new ArrayList<>(preserved);
            result.add(Message.createSystemMessage(
                "[历史对话摘要] 此次对话来自之前超出上下文的延续会话。\n\n"
                    + "<summary>\n<primary_request>压缩异常，无法生成摘要。</primary_request>\n"
                    + "<pending_tasks>未知。请检查最近对话中未完成的任务。</pending_tasks>\n"
                    + "<current_work>压缩前最后的工作内容请参阅下方的最近消息。</current_work>\n</summary>\n\n"
                    + "[注意] 以上为此前 " + head.size()
                    + " 轮对话的压缩摘要，后续请基于摘要和最近对话继续。"
            ));
            result.addAll(tail);
            return result;
        });
    }

    /**
     * 提取尾部安全区（最近 N 条消息）。
     * 若截断点落在 tool_calls 配对中间，自动向前调整以保证配对完整。
     */
    private List<Message> extractTail(List<Message> messages) {
        int startIndex = Math.max(0, messages.size() - tailSize);
        startIndex = fixTruncationBoundary(messages, startIndex);
        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }

    private List<Message> extractHead(List<Message> messages, int tailCount) {
        int headEnd = messages.size() - tailCount;
        if (headEnd <= 0) {
            return List.of();
        }
        // 限制头部大小，防止压缩 Prompt 过大
        int headStart = Math.max(0, headEnd - MAX_HEAD_MESSAGES);
        return new ArrayList<>(messages.subList(headStart, headEnd));
    }

    /**
     * 提取前 N 条用户消息（初始任务描述），这些消息不参与压缩。
     * 额外保护被启发式识别为"真实任务目标"的消息（如果它不在前 N 条中）。
     */
    private List<Message> extractFirstUserMessages(List<Message> messages, int count) {
        List<Message> result = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.USER && result.size() < count) {
                result.add(msg);
            }
            if (result.size() >= count) {
                break;
            }
        }
        return result;
    }

    /**
     * 从所有消息中启发式识别真实任务目标消息。
     */
    private Message findTaskGoalMessage(List<Message> allMessages) {
        if (allMessages == null || allMessages.isEmpty()) {
            return null;
        }

        Message bestCandidate = null;
        int bestScore = -1;

        for (Message msg : allMessages) {
            if (msg.getRole() != Message.Role.USER) {
                continue;
            }

            String content = msg.getTextContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            String trimmed = content.trim();
            String lower = trimmed.toLowerCase();

            // 排除纯问候语
            boolean isGreeting = false;
            for (String pattern : GREETING_PATTERNS) {
                String patternLower = pattern.toLowerCase();
                if (lower.equals(patternLower)
                    || lower.startsWith(patternLower + " ")
                    || lower.startsWith(patternLower + "，")
                    || lower.startsWith(patternLower + ",")) {
                    if (trimmed.length() < 15) {
                        isGreeting = true;
                        break;
                    }
                }
            }
            if (isGreeting) {
                continue;
            }

            // 排除过短消息（大概率是寒暄）
            if (trimmed.length() < 10) {
                continue;
            }

            int score = 0;

            // 长度启发：真实任务通常较长
            if (trimmed.length() > 20) {
                score += trimmed.length();
            }

            // 任务关键词
            for (String keyword : TASK_KEYWORDS) {
                if (trimmed.contains(keyword)) {
                    score += 50;
                }
            }

            // 代码/文件/模块指示
            for (String indicator : CODE_INDICATORS) {
                if (trimmed.contains(indicator)) {
                    score += 30;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = msg;
            } else if (score == bestScore && bestCandidate != null) {
                // 平局：更长的消息胜出
                if (trimmed.length() > bestCandidate.getTextContent().trim().length()) {
                    bestCandidate = msg;
                }
            }
        }

        return bestCandidate;
    }

    /**
     * 从所有消息中提取任务目标文本。
     */
    private String extractTaskGoal(List<Message> allMessages) {
        Message taskGoalMsg = findTaskGoalMessage(allMessages);
        return taskGoalMsg != null ? taskGoalMsg.getTextContent().trim() : null;
    }

    /**
     * 构建压缩 Prompt — 对标 Claude Code 9 段结构化摘要 + drafting scratchpad 模式。
     *
     * <p>两阶段输出：&lt;analysis&gt; 草稿纸（后处理删除，不占上下文）
     * + &lt;summary&gt; 正式摘要（9 个固定章节）。</p>
     *
     * @param headMessages 待压缩的头部消息（已排除被保护消息）
     * @param taskGoal     从全部消息中识别的任务目标文本
     */
    private String buildCompactionPrompt(List<Message> headMessages, String taskGoal) {
        StringBuilder sb = new StringBuilder();

        // NO_TOOLS_PREAMBLE — 首部禁止工具调用声明
        sb.append(NO_TOOLS_PREAMBLE).append("\n");

        // 压缩任务说明
        sb.append("You are summarizing a conversation between an AI agent and a user.\n");
        sb.append("All tasks described below are already completed.\n");
        sb.append("**DO NOT re-run, re-do or re-execute any of the tasks mentioned!**\n");
        sb.append("Use this summary only for context understanding.\n\n");

        // 强制注入用户真实任务目标
        if (taskGoal != null && !taskGoal.isBlank()) {
            sb.append("The user's primary request was: ").append(truncate(taskGoal, 300)).append("\n\n");
        }

        // 两阶段输出格式
        sb.append("Your response MUST consist of exactly two blocks:\n\n");
        sb.append("1. <analysis> — Your scratchpad. Analyze each message chronologically:\n");
        sb.append("   - What the user asked for and how their intent may have shifted\n");
        sb.append("   - What technical decisions were made and why\n");
        sb.append("   - What code was written, modified, or read\n");
        sb.append("   - What errors occurred and how they were fixed\n");
        sb.append("   - What remains incomplete\n");
        sb.append("   This block will be DISCARDED — it is a scratchpad only.\n\n");
        sb.append("2. <summary> — The final structured summary with these 9 sections:\n\n");
        sb.append("   <primary_request>\n");
        sb.append("   The user's core request and intent. If the intent shifted mid-conversation,\n");
        sb.append("   note each shift chronologically. Be specific — quote key phrases.\n");
        sb.append("   </primary_request>\n\n");
        sb.append("   <technical_concepts>\n");
        sb.append("   Key technical concepts, frameworks, libraries, APIs, and patterns discussed.\n");
        sb.append("   Include version numbers and configuration details where relevant.\n");
        sb.append("   </technical_concepts>\n\n");
        sb.append("   <files_and_code>\n");
        sb.append("   All files examined, modified, or created. For each file include:\n");
        sb.append("   - Full path relative to workspace root\n");
        sb.append("   - What was changed and why (for modifications)\n");
        sb.append("   - Key code snippets or function signatures where relevant\n");
        sb.append("   </files_and_code>\n\n");
        sb.append("   <errors_and_fixes>\n");
        sb.append("   Every error encountered and how it was resolved. Include:\n");
        sb.append("   - The exact error message or symptom\n");
        sb.append("   - The root cause\n");
        sb.append("   - The fix applied and why it worked\n");
        sb.append("   - Any approaches that were tried and failed\n");
        sb.append("   </errors_and_fixes>\n\n");
        sb.append("   <problem_solving>\n");
        sb.append("   Problems that were solved and those still under investigation.\n");
        sb.append("   Describe the reasoning process for key decisions.\n");
        sb.append("   </problem_solving>\n\n");
        sb.append("   <all_user_messages>\n");
        sb.append("   List ALL user messages that are not tool results, VERBATIM.\n");
        sb.append("   These are critical for understanding the user's feedback and changing intent.\n");
        sb.append("   Do NOT paraphrase, summarize, or omit any user message.\n");
        sb.append("   Format: [Msg N] user: <exact text>\n");
        sb.append("   </all_user_messages>\n\n");
        sb.append("   <pending_tasks>\n");
        sb.append("   All tasks that were requested but not yet completed.\n");
        sb.append("   Include TODO items, planned work, and follow-up actions.\n");
        sb.append("   </pending_tasks>\n\n");
        sb.append("   <current_work>\n");
        sb.append("   What was being worked on immediately before this summary.\n");
        sb.append("   The model reading this summary should be able to continue seamlessly.\n");
        sb.append("   </current_work>\n\n");
        sb.append("   <optional_next_step>\n");
        sb.append("   If there is an obvious next action, describe it.\n");
        sb.append("   Include verbatim quotes from the original conversation where relevant\n");
        sb.append("   to prevent task drift across compaction boundaries.\n");
        sb.append("   If nothing is pending, state 'No specific next step.'\n");
        sb.append("   </optional_next_step>\n\n");

        // 对话历史
        sb.append("--- CONVERSATION HISTORY TO SUMMARIZE ---\n\n");

        int msgIndex = 1;
        int validCount = 0;
        for (Message msg : headMessages) {
            String role = msg.getRole().name().toLowerCase();
            String content = extractCompactContent(msg);
            if (content == null || content.isBlank()) {
                continue;
            }
            validCount++;
            if (content.length() > 800) {
                content = content.substring(0, 800) + "\n...[content truncated]";
            }
            sb.append("## Message ").append(msgIndex++).append("\n");
            sb.append("Role: ").append(role).append("\n");
            sb.append("Content: ").append(content).append("\n\n");
        }

        if (validCount == 0) {
            logger.info("[SimpleCompaction] 头部无可压缩的实质内容（全是工具结果/监控消息），跳过 LLM 压缩");
            return null;
        }

        // NO_TOOLS_TRAILER — 尾部再次提醒
        sb.append(NO_TOOLS_TRAILER);
        return sb.toString();
    }

    /**
     * 后处理压缩摘要 — 删除 &lt;analysis&gt; 草稿纸块（drafting scratchpad pattern）。
     *
     * <p>草稿纸帮助模型理清思路，提高最终摘要质量，但不占用压缩后的上下文空间。</p>
     *
     * @param rawSummary LLM 原始输出
     * @return 去除 analysis 块后的摘要
     */
    private String formatCompactSummary(String rawSummary) {
        if (rawSummary == null || rawSummary.isBlank()) {
            return rawSummary;
        }
        // 删除 <analysis>...</analysis> 块（含多行内容）
        String formatted = rawSummary.replaceAll(
            "(?i)<analysis>[\\s\\S]*?</analysis>", "");
        // 清理多余的空行
        formatted = formatted.replaceAll("\\n{3,}", "\n\n");
        return formatted.trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[已截断]";
    }

    // 噪声消息前缀——这些消息不应参与 LLM 语义压缩
    private static final List<String> NOISE_PREFIXES = List.of(
        "[Token Budget]",
        "[历史对话摘要]",
        "[Token Budget] EXHAUSTED"
    );

    /**
     * 检查消息是否为噪声（系统监控类消息，不应参与压缩）
     */
    private boolean isNoiseMessage(Message msg) {
        if (msg.getRole() != Message.Role.SYSTEM) {
            return false;
        }
        String text = msg.getTextContent();
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String prefix : NOISE_PREFIXES) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private final MicroCompactService microCompactService = new MicroCompactService();

    /**
     * 提取用于压缩的消息内容。
     * 委托给 MicroCompactService 执行五级分层保留压缩。
     */
    private String extractCompactContent(Message msg) {
        if (isNoiseMessage(msg)) {
            return null;
        }
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return msg.getTextContent();
        }

        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.TextContent tc) {
                sb.append(tc.getText()).append("\n");
            } else if (block instanceof Message.ToolResultContent trc) {
                Object resultObj = trc.getResult();
                String result = resultObj != null ? resultObj.toString() : "";
                String toolName = trc.getToolName();

                if ("AgentTool".equals(toolName)) {
                    if (result.length() > 500) {
                        result = result.substring(0, 500) + "\n...[AgentTool 结果已截断]";
                    }
                    sb.append("[AgentTool 结果: ").append(result).append("]\n");
                } else {
                    MicroCompactConfig.Tier tier = microCompactService.classifyToolResult(toolName, result);
                    sb.append(microCompactService.microCompact(toolName, result, tier)).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 修复截断边界，确保不破坏 tool_calls 配对。
     */
    private int fixTruncationBoundary(List<Message> messages, int startIndex) {
        while (startIndex > 0 && startIndex < messages.size()) {
            Message msg = messages.get(startIndex);
            if (msg.getRole() != Message.Role.TOOL) {
                break;
            }
            String toolUseId = extractToolUseId(msg);
            if (toolUseId == null) {
                startIndex++;
                continue;
            }
            int assistantIndex = findAssistantWithToolCall(messages, toolUseId, startIndex - 1);
            if (assistantIndex >= 0) {
                startIndex = assistantIndex;
            } else {
                startIndex++;
                break;
            }
        }
        return startIndex;
    }

    private String extractToolUseId(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return null;
        }
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.ToolResultContent trc) {
                return trc.getToolUseId();
            }
        }
        return null;
    }

    private int findAssistantWithToolCall(List<Message> messages, String toolUseId, int endIndex) {
        for (int i = endIndex; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                for (Message.ToolCallInfo tc : msg.getToolCalls()) {
                    if (toolUseId.equals(tc.getId())) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public int getTailSize() {
        return tailSize;
    }
}
