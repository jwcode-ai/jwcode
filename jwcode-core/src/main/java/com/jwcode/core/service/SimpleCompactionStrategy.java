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

    // 保留尾部最近的消息数（默认 4 条 = 约 2 轮对话）
    private static final int DEFAULT_TAIL_SIZE = 4;
    // 摘要 Prompt 最大消息数（防止 Prompt 本身过大）
    private static final int MAX_HEAD_MESSAGES = 40;
    // 摘要目标长度（字符）
    private static final int SUMMARY_TARGET_CHARS = 400;
    // 预留 token 安全余量
    private static final int RESERVED_TOKENS = 4096;

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

        // 1. 分离尾部安全区
        List<Message> tail = extractTail(messages);
        List<Message> head = extractHead(messages, tail.size());

        // 2. 构建压缩 Prompt
        String compactionPrompt = buildCompactionPrompt(head);
        if (compactionPrompt == null || compactionPrompt.isBlank()) {
            logger.info("[SimpleCompaction] 头部无可压缩内容，仅截断");
            return CompletableFuture.completedFuture(tail);
        }

        // 3. 调用 LLM 生成摘要（禁用工具调用）
        List<LLMMessage> promptMessages = List.of(
            LLMMessage.system(compactionPrompt)
        );

        return llmService.chat(promptMessages).thenApply(response -> {
            if (response == null || response.hasError()) {
                String err = response != null ? response.getErrorMessage() : "null response";
                logger.warning("[SimpleCompaction] LLM 摘要失败: " + err + "，降级为截断");
                return tail;
            }

            String summary = response.getContent();
            if (summary == null || summary.isBlank()) {
                logger.warning("[SimpleCompaction] LLM 返回空摘要，降级为截断");
                return tail;
            }

            // 截断过长的摘要
            if (summary.length() > SUMMARY_TARGET_CHARS * 2) {
                summary = summary.substring(0, SUMMARY_TARGET_CHARS * 2) + "\n...[摘要已截断]";
            }

            // 4. 组装结果：摘要(system) + 尾部
            List<Message> result = new ArrayList<>();
            result.add(Message.createSystemMessage(
                "[历史对话摘要] " + summary.trim() + "\n\n[注意] 以上为此前 "
                    + head.size() + " 轮对话的压缩摘要，后续请基于摘要和最近对话继续。"
            ));
            result.addAll(tail);

            logger.info("[SimpleCompaction] 压缩完成: " + originalSize + " -> " + result.size()
                + " messages, summary=" + summary.length() + " chars");
            return result;
        }).exceptionally(e -> {
            logger.warning("[SimpleCompaction] 压缩异常: " + e.getMessage() + "，降级为截断");
            return tail;
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
     * 构建压缩 Prompt。
     */
    private String buildCompactionPrompt(List<Message> headMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("请用 200 字以内总结以下对话历史，保留关键信息：\n");
        sb.append("- 当前任务目标\n");
        sb.append("- 已做出的设计决策\n");
        sb.append("- 遇到的错误及解决方案\n");
        sb.append("- 尚未完成的待办事项\n\n");

        int msgIndex = 1;
        for (Message msg : headMessages) {
            String role = msg.getRole().name().toLowerCase();
            String content = extractCompactContent(msg);
            if (content == null || content.isBlank()) {
                continue;
            }
            // 单条消息内容截断，防止单条消息占满 Prompt
            if (content.length() > 800) {
                content = content.substring(0, 800) + "\n...[内容截断]";
            }
            sb.append("## Message ").append(msgIndex++).append("\n");
            sb.append("Role: ").append(role).append("\n");
            sb.append("Content: ").append(content).append("\n\n");
        }

        sb.append("[请用中文总结，控制在 200 字以内]");
        return sb.toString();
    }

    /**
     * 提取用于压缩的消息内容。
     * 过滤掉 reasoningContent、工具调用的 JSON 参数等噪声。
     */
    private String extractCompactContent(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return msg.getTextContent();
        }

        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.TextContent tc) {
                sb.append(tc.getText()).append("\n");
            } else if (block instanceof Message.ToolResultContent trc) {
                // 工具结果只保留简要状态，省略完整输出
                Object resultObj = trc.getResult();
                String result = resultObj != null ? resultObj.toString() : "";
                String status = result.startsWith("Error:") ? "失败" : "成功";
                sb.append("[工具结果: ").append(status).append("]\n");
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
