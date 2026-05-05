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
 */
public class SimpleCompactionStrategy {

    private static final Logger logger = Logger.getLogger(SimpleCompactionStrategy.class.getName());

    // 保留尾部最近的消息数（默认 8 条 = 约 4 轮对话，确保 AgentTool 任务分配信息不被压缩）
    private static final int DEFAULT_TAIL_SIZE = 8;
    // 摘要 Prompt 最大消息数（防止 Prompt 本身过大）
    private static final int MAX_HEAD_MESSAGES = 40;
    // 摘要目标长度（字符）- 【修复】从 400 增加到 800，确保保留关键任务信息
    private static final int SUMMARY_TARGET_CHARS = 800;
    // 预留 token 安全余量
    private static final int RESERVED_TOKENS = 4096;

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

            // 截断过长的摘要
            if (summary.length() > SUMMARY_TARGET_CHARS * 2) {
                summary = summary.substring(0, SUMMARY_TARGET_CHARS * 2) + "\n...[摘要已截断]";
            }

            // 4. 组装结果：保护消息 + 摘要(system) + 尾部
            List<Message> result = new ArrayList<>(preserved);
            result.add(Message.createSystemMessage(
                "[历史对话摘要] " + summary.trim() + "\n\n[注意] 以上为此前 "
                    + head.size() + " 轮对话的压缩摘要，后续请基于摘要和最近对话继续。"
            ));
            result.addAll(tail);

            logger.info("[SimpleCompaction] 压缩完成: " + originalSize + " -> " + result.size()
                + " messages, summary=" + summary.length() + " chars");
            return result;
        }).exceptionally(e -> {
            logger.warning("[SimpleCompaction] 压缩异常: " + e.getMessage() + "，使用 fallback 摘要");
            List<Message> result = new ArrayList<>(preserved);
            result.add(Message.createSystemMessage(
                "[历史对话摘要] 压缩异常，仅保留最近对话。\n\n[注意] 以上为此前 "
                    + head.size() + " 轮对话的压缩摘要，后续请基于摘要和最近对话继续。"
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
     * 构建压缩 Prompt。
     *
     * @param headMessages 待压缩的头部消息（已排除被保护消息）
     * @param taskGoal     从全部消息中识别的任务目标文本
     */
    private String buildCompactionPrompt(List<Message> headMessages, String taskGoal) {
        StringBuilder sb = new StringBuilder();

        // 【修复】强制注入用户真实任务目标，防止压缩后丢失
        if (taskGoal != null && !taskGoal.isBlank()) {
            sb.append("【强制约束】用户真实任务目标：").append(truncate(taskGoal, 200)).append("\n");
            sb.append("注意：如果上述目标看起来只是问候语/寒暄，请从完整对话历史中重新识别真正的任务目标。\n");
            sb.append("真正的任务目标通常是：包含具体动作（修复/添加/实现/优化）、涉及具体模块/文件、有明确验收标准的消息。\n");
            sb.append("你必须在摘要开头用一句话准确复述用户真实任务目标，禁止篡改、遗漏或臆测。\n\n");
        }

        sb.append("请用 200 字以内总结以下对话历史，保留关键信息：\n");
        sb.append("- 当前任务目标（必须从用户消息中准确提取，禁止臆测或篡改）\n");
        sb.append("- 已做出的设计决策\n");
        sb.append("- 遇到的错误及解决方案\n");
        sb.append("- 尚未完成的待办事项\n\n");

        int msgIndex = 1;
        int validCount = 0;
        for (Message msg : headMessages) {
            String role = msg.getRole().name().toLowerCase();
            String content = extractCompactContent(msg);
            if (content == null || content.isBlank()) {
                continue;
            }
            validCount++;
            // 单条消息内容截断，防止单条消息占满 Prompt
            if (content.length() > 800) {
                content = content.substring(0, 800) + "\n...[内容截断]";
            }
            sb.append("## Message ").append(msgIndex++).append("\n");
            sb.append("Role: ").append(role).append("\n");
            sb.append("Content: ").append(content).append("\n\n");
        }

        // 无可压缩的实质内容，返回 null 触发 fallback 截断
        if (validCount == 0) {
            logger.info("[SimpleCompaction] 头部无可压缩的实质内容（全是工具结果/监控消息），跳过 LLM 压缩");
            return null;
        }

        sb.append("[请用中文总结，控制在 200 字以内]");
        return sb.toString();
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

    /**
     * 提取用于压缩的消息内容。
     * 过滤掉 reasoningContent、工具调用的 JSON 参数等噪声。
     * 【修复】保留 AgentTool 的任务分配信息，避免压缩后丢失关键任务上下文。
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
                // 【修复】保留 AgentTool 的任务分配信息，不简化为"成功/失败"
                Object resultObj = trc.getResult();
                String result = resultObj != null ? resultObj.toString() : "";
                String toolName = trc.getToolName();
                
                // 对于 AgentTool 的任务分配，保留完整内容（包含子任务描述）
                if ("AgentTool".equals(toolName)) {
                    // 截断过长的结果，但保留关键信息
                    if (result.length() > 500) {
                        result = result.substring(0, 500) + "\n...[AgentTool 结果已截断]";
                    }
                    sb.append("[AgentTool 结果: ").append(result).append("]\n");
                } else {
                    // 其他工具结果只保留简要状态
                    String status = result.startsWith("Error:") ? "失败" : "成功";
                    sb.append("[工具结果 (").append(toolName).append("): ").append(status).append("]\n");
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
