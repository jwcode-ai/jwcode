package com.jwcode.core.agent;

import com.jwcode.core.service.PostCompactRecoveryService;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.Message;
import com.jwcode.core.service.SimpleCompactionStrategy;
import com.jwcode.core.service.StructuredCompactionStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * CompactorAgent - context compaction specialist (layer 2.5 horizontal service agent).
 * Workflow/runtime agents can invoke it to compact context.
 * Prioritizes StructuredCompactionStrategy (Markdown-based), falls back to SimpleCompactionStrategy.
 */
public class CompactorAgent {

    private static final Logger logger = Logger.getLogger(CompactorAgent.class.getName());

    public static class CompactionResult {
        private final List<Message> compactedMessages;
        private final int originalSize, compactedSize;
        private final long tokensSaved;
        private final String summary;

        public CompactionResult(List<Message> msgs, int orig, int comp, long saved, String sum) {
            this.compactedMessages = msgs; this.originalSize = orig;
            this.compactedSize = comp; this.tokensSaved = saved; this.summary = sum;
        }
        public List<Message> getCompactedMessages() { return compactedMessages; }
        public int getOriginalSize() { return originalSize; }
        public int getCompactedSize() { return compactedSize; }
        public long getTokensSaved() { return tokensSaved; }
        public String getSummary() { return summary; }
    }

    public static class CompactionRequest {
        private final List<Message> messages;
        private final CompactorTrigger.Strategy strategy;
        private final List<String> preserveKeys;
        private final long tokenBudget;

        public CompactionRequest(List<Message> msgs, CompactorTrigger.Strategy s,
                                 List<String> keys, long budget) {
            this.messages = msgs; this.strategy = s;
            this.preserveKeys = keys; this.tokenBudget = budget;
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
    private final PostCompactRecoveryService recoveryService;
    private String compactionModelId;
    private static final long ESTIMATED_TOKENS_PER_MESSAGE = 500;

    public CompactorAgent(LLMService llmService) {
        this.llmService = llmService;
        this.simpleStrategy = new SimpleCompactionStrategy(llmService);
        this.structuredStrategy = new StructuredCompactionStrategy(llmService);
        this.trigger = new CompactorTrigger();
        this.recoveryService = PostCompactRecoveryService.getInstance();
    }

    public CompactorAgent(LLMService llmService, SimpleCompactionStrategy s) {
        this.llmService = llmService;
        this.simpleStrategy = s;
        this.structuredStrategy = new StructuredCompactionStrategy(llmService);
        this.trigger = new CompactorTrigger();
        this.recoveryService = PostCompactRecoveryService.getInstance();
    }

    public void setCompactionModel(String modelId) { this.compactionModelId = modelId; }
    public String getCompactionModel() { return compactionModelId; }

    public CompactionResult compact(CompactionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return new CompactionResult(request.getMessages(), 0, 0, 0, "no messages");
        }
        int originalSize = request.getMessages().size();
        logger.info("[CompactorAgent] Compacting: strategy=" + request.getStrategy()
            + ", original=" + originalSize);

        String savedModel = null;
        if (compactionModelId != null) {
            savedModel = llmService.getModelName();
            llmService.reconfigure(compactionModelId);
            logger.fine("[CompactorAgent] Switched to compaction model: " + compactionModelId);
        }
        try {
            List<Message> compacted = switch (request.getStrategy()) {
                case STRUCTURED, AICL_PRIORITY -> structuredStrategy.compact(request.getMessages());
                case SMART -> simpleStrategy.compact(request.getMessages());
                case AGGRESSIVE -> new SimpleCompactionStrategy(llmService, 4).compact(request.getMessages());
                case MINIMAL -> request.getMessages().stream()
                    .filter(m -> m.getRole() != Message.Role.TOOL).toList();
                case RESET -> request.getMessages();
                default -> structuredStrategy.compact(request.getMessages());
            };
            // 压缩后自动恢复：重新读取最近访问的文件
            try {
                String recoveryContext = recoveryService.recoverAfterCompact(
                    System.getProperty("user.dir"));
                if (recoveryContext != null && !recoveryContext.isBlank()) {
                    List<Message> withRecovery = new ArrayList<>(compacted);
                    withRecovery.add(Message.createSystemMessage(recoveryContext));
                    compacted = withRecovery;
                }
            } catch (Exception e) {
                logger.fine("[CompactorAgent] 压缩后恢复跳过: " + e.getMessage());
            }

            int compactedSize = compacted.size();
            long tokensSaved = Math.max(0, (long) (originalSize - compactedSize) * ESTIMATED_TOKENS_PER_MESSAGE);
            String summary = String.format("Compaction: %d -> %d messages, ~%d tokens saved, strategy=%s",
                originalSize, compactedSize, tokensSaved, request.getStrategy());
            logger.info("[CompactorAgent] " + summary);
            return new CompactionResult(compacted, originalSize, compactedSize, tokensSaved, summary);
        } finally {
            if (savedModel != null) llmService.reconfigure(savedModel);
        }
    }

    public CompletableFuture<CompactionResult> compactAsync(CompactionRequest request) {
        return CompletableFuture.supplyAsync(() -> compact(request));
    }

    public CompactorTrigger getTrigger() { return trigger; }
}
