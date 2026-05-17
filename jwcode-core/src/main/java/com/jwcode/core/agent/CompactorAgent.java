package com.jwcode.core.agent;

import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.Capabilities;
import com.jwcode.core.a2a.model.RetryPolicy;
import com.jwcode.core.a2a.model.Skill;
import com.jwcode.core.aicl.ContextAssembler;
import com.jwcode.core.aicl.ContextBlock;
import com.jwcode.core.aicl.BlockPriority;
import com.jwcode.core.aicl.BlockLifecycle;
import com.jwcode.core.aicl.ContextControl;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.Message;
import com.jwcode.core.service.SimpleCompactionStrategy;
import com.jwcode.core.service.StructuredCompactionStrategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * CompactorAgent — 上下文压缩专家（第2.5层横向服务Agent）。
 *
 * <p>所有Agent（包括Orchestrator）都可以通过A2A协议调用它来压缩上下文。
 * 内部优先使用 {@link StructuredCompactionStrategy} 执行强制XML结构化压缩，
 * 降级到 {@link SimpleCompactionStrategy} 执行普通压缩。</p>
 *
 * <p>四种压缩策略：
 * <ul>
 *   <li><b>STRUCTURED</b>（推荐）：强制XML结构化输出，按优先级保留信息</li>
 *   <li><b>SMART</b>：智能压缩，保留尾部8条 + 调用LLM生成语义摘要</li>
 *   <li><b>AGGRESSIVE</b>：激进压缩，仅保留尾部4条 + 摘要</li>
 *   <li><b>MINIMAL</b>：最小压缩，仅移除工具结果噪声，不调用LLM</li>
 * </ul>
 *
 * <p>结构化XML输出格式（对标KimiCode）：
 * <pre>
 * &lt;compaction_summary&gt;
 *   &lt;current_focus&gt;[正在做什么]&lt;/current_focus&gt;
 *   &lt;environment&gt;[关键配置]&lt;/environment&gt;
 *   &lt;completed_tasks&gt;[任务]: [结果]&lt;/completed_tasks&gt;
 *   &lt;active_issues&gt;[问题]: [状态]&lt;/active_issues&gt;
 *   &lt;code_state&gt;...&lt;/code_state&gt;
 * &lt;/compaction_summary&gt;
 * </pre>
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

    private final SimpleCompactionStrategy simpleStrategy;
    private final StructuredCompactionStrategy structuredStrategy;
    private final LLMService llmService;
    private final CompactorTrigger trigger;

    // ===== AICL 集成（v1.1） =====
    private ContextAssembler aiclAssembler;

    /** 每条消息估计的token数（用于估算） */
    private static final long ESTIMATED_TOKENS_PER_MESSAGE = 500;

    public CompactorAgent(LLMService llmService) {
        this.llmService = llmService;
        this.simpleStrategy = new SimpleCompactionStrategy(llmService);
        this.structuredStrategy = new StructuredCompactionStrategy(llmService);
        this.trigger = new CompactorTrigger();
        this.aiclAssembler = new ContextAssembler();
    }

    public CompactorAgent(LLMService llmService, SimpleCompactionStrategy simpleStrategy) {
        this.llmService = llmService;
        this.simpleStrategy = simpleStrategy;
        this.structuredStrategy = new StructuredCompactionStrategy(llmService);
        this.trigger = new CompactorTrigger();
        this.aiclAssembler = new ContextAssembler();
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
        boolean resetRecommended = false;
        switch (request.getStrategy()) {
            case STRUCTURED -> compacted = compactStructured(request.getMessages());
            case AICL_PRIORITY -> compacted = compactAicl(request.getMessages());
            case MINIMAL -> compacted = compactMinimal(request.getMessages());
            case AGGRESSIVE -> compacted = compactAggressive(request.getMessages());
            case SMART -> compacted = compactSmart(request.getMessages());
            case RESET -> {
                // RESET 策略：不压缩，而是建议升级为 Context Reset
                compacted = request.getMessages();
                resetRecommended = true;
            }
            default -> compacted = compactAicl(request.getMessages());
        }

        int compactedSize = compacted.size();
        long tokensSaved = estimateTokensSaved(originalSize, compactedSize);
        String summary = resetRecommended
            ? "压缩不足以解决问题，建议升级为 Context Reset（进程级重启+结构化交接）"
            : generateSummary(originalSize, compactedSize, tokensSaved, request.getStrategy());

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
     * 结构化压缩（推荐）：强制XML输出，按优先级保留信息。
     */
    private List<Message> compactStructured(List<Message> messages) {
        return structuredStrategy.compact(messages);
    }

    /**
     * AICL 优先级淘汰（v1.1）：基于 6 级优先级+生命周期的渐进式淘汰。
     *
     * <p>将 Message 列表转换为 ContextBlock 集合，由 ContextAssembler
     * 执行 Priority-LRU 逐级淘汰算法，最后将活跃块转回 Message 列表。</p>
     */
    private List<Message> compactAicl(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        // 将 messages 转为 ContextBlock 并注册到 Assembler
        aiclAssembler = new ContextAssembler(); // 重置 assembler
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            ContextBlock block = messageToBlock(msg, i);
            aiclAssembler.registerBlock(block);
        }

        // 执行淘汰
        ContextAssembler.EvictionStats stats = aiclAssembler.evict();
        logger.info("[CompactorAgent] AICL eviction stats: " + stats);

        // 将活跃块转回 Message 列表
        return aiclAssembler.getActiveBlocks().stream()
                .map(this::blockToMessage)
                .filter(m -> m != null)
                .toList();
    }

    /**
     * 智能压缩（默认）：保留尾部8条 + 关键任务目标，调用LLM生成语义摘要。
     */
    private List<Message> compactSmart(List<Message> messages) {
        return simpleStrategy.compact(messages);
    }

    /**
     * 激进压缩：仅保留尾部4条 + 摘要。
     */
    private List<Message> compactAggressive(List<Message> messages) {
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

    public ContextAssembler getAiclAssembler() {
        return aiclAssembler;
    }

    /**
     * 将 Message 转换为 AICL ContextBlock。
     */
    private ContextBlock messageToBlock(Message msg, int index) {
        String id = msg.getId() != null ? msg.getId() : "msg_" + index;
        String type = switch (msg.getRole()) {
            case SYSTEM -> "system";
            case USER -> "user";
            case TOOL -> "tool";
            default -> "assistant";
        };
        String role = msg.getRole().name().toLowerCase();
        String content = msg.getTextContent();
        BlockPriority priority = switch (msg.getRole()) {
            case SYSTEM -> BlockPriority.CRITICAL;
            case USER -> BlockPriority.HIGH;
            case TOOL -> BlockPriority.LOW;
            default -> BlockPriority.MEDIUM;
        };

        return ContextBlock.builder(id)
                .type(type)
                .role(role)
                .priority(priority)
                .format("markdown")
                .state(BlockLifecycle.ACTIVE)
                .ttl(3)
                .content(content)
                .label((type + " #" + (index + 1)))
                .build();
    }

    /**
     * 将 AICL ContextBlock 转回 Message。
     * 注意：已归档/废弃的块不应转换。
     */
    private Message blockToMessage(ContextBlock block) {
        if (block.getState() == BlockLifecycle.DEPRECATED) return null;
        if (block.getState() == BlockLifecycle.ARCHIVED) return null;

        String content = block.getContent();
        if (content == null || content.isEmpty()) {
            // summarized 块使用 summary
            content = block.getSummary();
            if (content == null || content.isEmpty()) return null;
        }

        Message.Role role = switch (block.getRole().toLowerCase()) {
            case "user" -> Message.Role.USER;
            case "system" -> Message.Role.SYSTEM;
            case "tool" -> Message.Role.TOOL;
            default -> Message.Role.ASSISTANT;
        };

        // 使用 Message 的工厂方法（如有）创建消息 — 这里简化处理
        return new Message(block.getId(), role,
                List.of(new Message.TextContent(content)),
                null, null) {};
    }
}
