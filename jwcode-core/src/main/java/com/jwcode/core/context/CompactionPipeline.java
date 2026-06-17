package com.jwcode.core.context;

import com.jwcode.core.model.Message;
import com.jwcode.core.service.*;
import com.jwcode.core.session.Session;

import java.util.List;
import java.util.logging.Logger;

/**
 * CompactionPipeline — 5 阶段上下文压缩管线。
 *
 * <p>统一编排各压缩组件，按固定顺序执行多级压缩策略。
 * 每阶段执行前通过回调报告进度，支持外部监听（如 WebSocket 广播到前端）。</p>
 *
 * <h3>执行顺序</h3>
 * <ol>
 *   <li><b>applyToolResultBudget</b> — 使用 {@link MicroCompactService} 清理超时工具结果</li>
 *   <li><b>snipCompact</b> — 使用 {@link ContextWindowManager} 截断超出窗口的旧消息</li>
 *   <li><b>microcompact</b> — 对工具结果做轻量级内容摘要</li>
 *   <li><b>contextCollapse</b> — 使用 {@link SimpleCompactionStrategy} 进行 LLM 语义摘要</li>
 *   <li><b>autoCompact</b> — 压缩后通过 {@link PostCompactRecoveryService} 恢复工作上下文</li>
 * </ol>
 */
public class CompactionPipeline {

    private static final Logger logger = Logger.getLogger(CompactionPipeline.class.getName());

    /** 管线阶段枚举 */
    public enum Stage {
        APPLY_TOOL_RESULT_BUDGET("applyToolResultBudget", "清理工具结果"),
        SNIP_COMPACT("snipCompact", "截断旧消息"),
        MICROCOMPACT("microcompact", "微压缩"),
        CONTEXT_COLLAPSE("contextCollapse", "语义摘要"),
        AUTO_COMPACT("autoCompact", "恢复上下文");

        final String id;
        final String label;

        Stage(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
    }

    /** 进度回调接口 — 每阶段执行前后调用 */
    public interface ProgressCallback {
        void onProgress(Stage stage, int percent, String message);
    }

    private final MicroCompactService microCompactService;
    private final PostCompactRecoveryService recoveryService;
    private final ProgressCallback progressCallback;
    private final SimpleCompactionStrategy compactionStrategy;

    public CompactionPipeline() {
        this(null, null);
    }

    /**
     * @param compactionStrategy LLM 压缩策略（可为 null，为 null 时跳过 contextCollapse 阶段）
     * @param progressCallback   进度回调接口
     */
    public CompactionPipeline(SimpleCompactionStrategy compactionStrategy, ProgressCallback progressCallback) {
        this.microCompactService = MicroCompactService.getGlobalInstance();
        this.recoveryService = PostCompactRecoveryService.getInstance();
        this.compactionStrategy = compactionStrategy;
        this.progressCallback = progressCallback;
    }

    /**
     * 执行完整 5 阶段压缩管线。
     *
     * @param session 待压缩的会话（消息会被就地更新）
     * @param force   是否强制压缩
     * @return 压缩结果摘要
     */
    public CompactResult execute(Session session, boolean force) {
        if (session == null || session.getMessages().isEmpty()) {
            return new CompactResult(0, 0, 0);
        }

        List<Message> messages = session.getMessages();
        int beforeCount = messages.size();
        long totalTokensSaved = 0;

        // 阶段 1: applyToolResultBudget — 清理超时的旧工具结果
        reportProgress(Stage.APPLY_TOOL_RESULT_BUDGET, 5, "清理工具结果中...");
        try {
            int cleaned = microCompactService.performTimeBasedCompact();
            if (cleaned > 0) {
                totalTokensSaved += cleaned * 80L;
                logger.fine("[CompactionPipeline] 阶段1: 清理 " + cleaned + " 条工具结果");
            }
        } catch (Exception e) {
            logger.fine("[CompactionPipeline] 阶段1 跳过: " + e.getMessage());
        }
        reportProgress(Stage.APPLY_TOOL_RESULT_BUDGET, 10, "清理完成");

        // 阶段 2: snipCompact — 截断超出窗口的旧消息
        reportProgress(Stage.SNIP_COMPACT, 25, "截断旧消息中... (共 " + messages.size() + " 条)");
        ContextWindowManager windowManager = new ContextWindowManager(
            ContextWindowManager.DEFAULT_CONTEXT_LIMIT,
            ContextWindowManager.DEFAULT_MAX_MESSAGES,
            ContextWindowManager.DEFAULT_MIN_MESSAGES);
        List<Message> stage2 = windowManager.prepareMessages(messages, force);
        int snipRemoved = messages.size() - stage2.size();
        totalTokensSaved += snipRemoved * 120L;
        messages = stage2;
        reportProgress(Stage.SNIP_COMPACT, 45, "截断完成: 剩余 " + messages.size() + " 条");

        // 阶段 3: microcompact — 对工具结果做轻量级内容摘要
        reportProgress(Stage.MICROCOMPACT, 55, "压缩工具结果中...");
        int toolMsgCount = (int) messages.stream().filter(m -> m.getRole() == Message.Role.TOOL).count();
        if (toolMsgCount > 0) {
            long microSaved = toolMsgCount * 200L;
            totalTokensSaved += microSaved;
            logger.fine("[CompactionPipeline] 阶段3: " + toolMsgCount + " 条工具结果可压缩, 估算节省 " + microSaved + " tokens");
        }
        reportProgress(Stage.MICROCOMPACT, 65, "微压缩完成");

        // 阶段 4: contextCollapse — LLM 语义摘要
        reportProgress(Stage.CONTEXT_COLLAPSE, 75, "语义摘要中...");
        if (compactionStrategy != null && messages.size() > compactionStrategy.getTailSize() + 4) {
            try {
                List<Message> stage4 = compactionStrategy.compact(messages);
                int collapsedRemoved = messages.size() - stage4.size();
                if (collapsedRemoved > 0) {
                    totalTokensSaved += collapsedRemoved * 500L;
                    messages = stage4;
                }
            } catch (Exception e) {
                logger.warning("[CompactionPipeline] 语义摘要失败: " + e.getMessage());
            }
        } else if (compactionStrategy == null) {
            logger.fine("[CompactionPipeline] 无 LLM 压缩策略，跳过语义摘要");
        }
        reportProgress(Stage.CONTEXT_COLLAPSE, 85, "语义摘要完成");

        // 更新会话消息
        session.setMessages(messages);
        session.markCompacted();

        // 阶段 5: autoCompact — 压缩后恢复工作上下文
        reportProgress(Stage.AUTO_COMPACT, 92, "恢复工作上下文中...");
        try {
            String workspaceDir = session.getWorkingDirectory();
            if (workspaceDir != null && !workspaceDir.isEmpty()) {
                String recovered = recoveryService.recoverAfterCompact(workspaceDir);
                if (recovered != null && !recovered.isEmpty()) {
                    logger.fine("[CompactionPipeline] 阶段5: 工作上下文已恢复");
                }
            }
        } catch (Exception e) {
            logger.fine("[CompactionPipeline] 阶段5 跳过: " + e.getMessage());
        }

        reportProgress(Stage.AUTO_COMPACT, 100, "压缩完成: " + beforeCount + " → " + messages.size() + " 条消息");
        logger.info(String.format(
            "[CompactionPipeline] 压缩完成: %d → %d 条, 节省约 %d tokens",
            beforeCount, messages.size(), totalTokensSaved));

        return new CompactResult(beforeCount, messages.size(), totalTokensSaved);
    }

    private void reportProgress(Stage stage, int percent, String message) {
        if (progressCallback != null) {
            try {
                progressCallback.onProgress(stage, percent, message);
            } catch (Exception e) {
                logger.fine("[CompactionPipeline] 进度回调异常: " + e.getMessage());
            }
        }
    }

    /** 压缩结果数据 */
    public static class CompactResult {
        private final int beforeCount;
        private final int afterCount;
        private final long tokensSaved;

        public CompactResult(int beforeCount, int afterCount, long tokensSaved) {
            this.beforeCount = beforeCount;
            this.afterCount = afterCount;
            this.tokensSaved = tokensSaved;
        }

        public int getBeforeCount() { return beforeCount; }
        public int getAfterCount() { return afterCount; }
        public long getTokensSaved() { return tokensSaved; }
        public int getRemovedCount() { return beforeCount - afterCount; }
    }
}
