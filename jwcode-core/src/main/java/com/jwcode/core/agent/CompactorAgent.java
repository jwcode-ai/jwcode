package com.jwcode.core.agent;

import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.Capabilities;
import com.jwcode.core.a2a.model.RetryPolicy;
import com.jwcode.core.a2a.model.Skill;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.Message;
import com.jwcode.core.service.SimpleCompactionStrategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * CompactorAgent — 上下文压缩专家（第2.5层横向服务Agent）。
 *
 * <p>所有Agent（包括Orchestrator）都可以通过A2A协议调用它来压缩上下文。
 * 内部使用 {@link SimpleCompactionStrategy} 执行实际压缩。</p>
 *
 * <p>三种压缩策略：
 * <ul>
 *   <li><b>SMART</b>：智能压缩，保留尾部8条 + 关键任务目标，调用LLM生成语义摘要</li>
 *   <li><b>AGGRESSIVE</b>：激进压缩，仅保留尾部4条 + 摘要</li>
 *   <li><b>MINIMAL</b>：最小压缩，仅移除工具结果噪声，不调用LLM</li>
 * </ul>
 * </p>
 */
public class CompactorAgent {

    private static final Logger logger = Logger.getLogger(CompactorAgent.class.getName());

    /** 压缩结果 */
    public static class CompactionResult {
        private final List<Message> compactedMessages;
        private final int originalSize;
        private final int compactedSize;
        private final long tokensSaved;
        private final String summary;

        public CompactionResult(List<Message> compactedMessages, int originalSize,
                                int compactedSize, long tokensSaved, String summary) {
            this.compactedMessages = compactedMessages;
            this.originalSize = originalSize;
            this.compactedSize = compactedSize;
            this.tokensSaved = tokensSaved;
            this.summary = summary;
        }

        public List<Message> getCompactedMessages() { return compactedMessages; }
        public int getOriginalSize() { return originalSize; }
        public int getCompactedSize() { return compactedSize; }
        public long getTokensSaved() { return tokensSaved; }
        public String getSummary() { return summary; }
    }

    /** 压缩请求 */
    public static class CompactionRequest {
        private final List<Message> messages;
        private final CompactorTrigger.Strategy strategy;
        private final List<String> preserveKeys;
        private final long tokenBudget;

        public CompactionRequest(List<Message> messages, CompactorTrigger.Strategy strategy,
                                 List<String> preserveKeys, long tokenBudget) {
            this.messages = messages;
            this.strategy = strategy;
            this.preserveKeys = preserveKeys;
            this.tokenBudget = tokenBudget;
        }

        public List<Message> getMessages() { return messages; }
        public CompactorTrigger.Strategy getStrategy() { return strategy; }
        public List<String> getPreserveKeys() { return preserveKeys; }
        public long getTokenBudget() { return tokenBudget; }
    }

    private final SimpleCompactionStrategy compactionStrategy;
    private final LLMService llmService;
    private final CompactorTrigger trigger;

    /** 每条消息估计的token数（用于估算） */
    private static final long ESTIMATED_TOKENS_PER_MESSAGE = 500;

    public CompactorAgent(LLMService llmService) {
        this.llmService = llmService;
        this.compactionStrategy = new SimpleCompactionStrategy(llmService);
        this.trigger = new CompactorTrigger();
    }

    public CompactorAgent(LLMService llmService, SimpleCompactionStrategy compactionStrategy) {
        this.llmService = llmService;
        this.compactionStrategy = compactionStrategy;
        this.trigger = new CompactorTrigger();
    }

    /**
     * 核心方法：压缩上下文。
     *
     * @param request 压缩请求
     * @return 压缩结果
     */
    public CompactionResult compact(CompactionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return new CompactionResult(request.getMessages(), 0, 0, 0, "无消息可压缩");
        }

        int originalSize = request.getMessages().size();
        logger.info("[CompactorAgent] 开始压缩: strategy=" + request.getStrategy()
            + ", original=" + originalSize + " messages");

        List<Message> compacted;
        switch (request.getStrategy()) {
            case MINIMAL -> compacted = compactMinimal(request.getMessages());
            case AGGRESSIVE -> compacted = compactAggressive(request.getMessages());
            case SMART -> compacted = compactSmart(request.getMessages());
            default -> compacted = compactSmart(request.getMessages());
        }

        int compactedSize = compacted.size();
        long tokensSaved = estimateTokensSaved(originalSize, compactedSize);
        String summary = generateSummary(originalSize, compactedSize, tokensSaved, request.getStrategy());

        logger.info("[CompactorAgent] 压缩完成: " + originalSize + " -> " + compactedSize
            + " messages, saved ~" + tokensSaved + " tokens");

        return new CompactionResult(compacted, originalSize, compactedSize, tokensSaved, summary);
    }

    /**
     * 异步压缩。
     */
    public CompletableFuture<CompactionResult> compactAsync(CompactionRequest request) {
        return CompletableFuture.supplyAsync(() -> compact(request));
    }

    /**
     * 智能压缩（默认）：保留尾部8条 + 关键任务目标，调用LLM生成语义摘要。
     */
    private List<Message> compactSmart(List<Message> messages) {
        return compactionStrategy.compact(messages);
    }

    /**
     * 激进压缩：仅保留尾部4条 + 摘要。
     */
    private List<Message> compactAggressive(List<Message> messages) {
        // 使用更小的尾部大小
        SimpleCompactionStrategy aggressive = new SimpleCompactionStrategy(llmService, 4);
        return aggressive.compact(messages);
    }

    /**
     * 最小压缩：仅移除工具结果噪声，不调用LLM。
     */
    private List<Message> compactMinimal(List<Message> messages) {
        // 仅过滤掉纯工具结果噪声，保留所有对话
        return messages.stream()
            .filter(msg -> !isNoiseMessage(msg))
            .toList();
    }

    /**
     * 判断是否为噪声消息（纯工具结果、系统监控消息）。
     */
    private boolean isNoiseMessage(Message msg) {
        if (msg.getRole() == Message.Role.TOOL) {
            // 保留包含错误信息的工具结果
            String text = msg.getTextContent();
            return text == null || text.isBlank() || text.startsWith("[Token Budget]");
        }
        return false;
    }

    /**
     * 估算节省的token数。
     */
    private long estimateTokensSaved(int originalSize, int compactedSize) {
        int removed = originalSize - compactedSize;
        return Math.max(0, (long) removed * ESTIMATED_TOKENS_PER_MESSAGE);
    }

    /**
     * 生成压缩摘要。
     */
    private String generateSummary(int originalSize, int compactedSize,
                                    long tokensSaved, CompactorTrigger.Strategy strategy) {
        return String.format(
            "上下文压缩完成: %d → %d 条消息, 节省约 %d tokens, 策略=%s",
            originalSize, compactedSize, tokensSaved, strategy);
    }

    /**
     * 获取CompactorAgent的AgentCard。
     */
    public static AgentCard createAgentCard() {
        return AgentCard.builder()
            .name("Compactor")
            .description("Context compression expert — compresses conversation history to save token budget")
            .agentType("compactor")
            .skills(List.of(
                Skill.builder()
                    .id("compact-context")
                    .name("Compact Context")
                    .description("Compress conversation history using LLM-based semantic summarization")
                    .inputSchema(Map.of(
                        "messages", "List<Message>",
                        "strategy", "String (smart|aggressive|minimal)",
                        "preserveKeys", "List<String>",
                        "tokenBudget", "long"
                    ))
                    .outputSchema(Map.of(
                        "compactedMessages", "List<Message>",
                        "originalSize", "int",
                        "compactedSize", "int",
                        "tokensSaved", "long",
                        "summary", "String"
                    ))
                    .build()
            ))
            .capabilities(Capabilities.builder()
                .streaming(false)
                .pushNotifications(false)
                .websocket(false)
                .batchProcessing(false)
                .maxConcurrentTasks(3)
                .build())
            .build();
    }

    public CompactorTrigger getTrigger() {
        return trigger;
    }
}
