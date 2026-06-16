package com.jwcode.core.context;

import com.jwcode.core.session.Session;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * ContextReconcilerBridge — 将 {@link ContextReconciler} 无缝集成到
 * 现有 {@code LLMQueryEngine} 和 {@code Session} 管道的适配器。
 *
 * <p>职责：
 * <ol>
 *   <li>在 {@code LLMQueryEngine.runPreFlightChecks()} 之前调用
 *       {@link #beforeLlmCall(Session)}，获取增量上下文</li>
 *   <li>在 compaction 后调用 {@link #afterCompaction(Session)}，
 *       创建新 epoch</li>
 *   <li>将增量文本注入到系统消息中</li>
 * </ol>
 *
 * <p>设计原则：最小入侵 —— 不需要大规模重构现有代码，
 * 只需在关键调用点插入 2-3 行。
 */
public class ContextReconcilerBridge {

    private static final Logger logger = Logger.getLogger(ContextReconcilerBridge.class.getName());

    private final ContextReconciler reconciler;

    public ContextReconcilerBridge(ContextReconciler reconciler) {
        this.reconciler = Objects.requireNonNull(reconciler);
    }

    public ContextReconciler getReconciler() { return reconciler; }

    // ==================== 集成 API ====================

    /**
     * 在每次 LLM 调用前调用。
     *
     * <p>如果 reconciler 返回增量文本，通过 {@code session} 注入为系统消息。
     * 如果 reconciler 指示需要替换，调用方应触发 compaction 并调用
     * {@link #afterCompaction(Session)}。
     *
     * @param session  当前会话
     * @param agentId  当前 agent ID
     * @return true 如果注入了增量上下文
     */
    public boolean beforeLlmCall(Session session, String agentId) {
        if (reconciler.getCurrentEpoch() == null) {
            reconciler.initialize(agentId);
            return false;
        }

        String delta = reconciler.beforeLlmCall(agentId);
        if (delta == null) {
            return false; // 无变化
        }

        if (delta.isEmpty()) {
            // 需要替换 —— 先尝试新 epoch（保留现有消息不变）
            reconciler.newEpoch(agentId);
            return true;
        }

        // 增量更新：注入系统消息
        injectDeltaContext(session, delta);
        return true;
    }

    /**
     * compaction 后调用。
     *
     * <p>创建新 epoch 并写入替换后的基线。
     */
    public void afterCompaction(Session session, String agentId) {
        reconciler.replaceEpoch(agentId);
        logger.info("[ContextReconcilerBridge] Epoch replaced after compaction, epoch="
            + reconciler.getCurrentEpoch().getId());
    }

    /**
     * Agent 切换时调用。
     */
    public void onAgentSwitch(Session session, String newAgentId) {
        reconciler.newEpoch(newAgentId);
        logger.info("[ContextReconcilerBridge] New epoch for agent=" + newAgentId);
    }

    // ==================== 内部方法 ====================

    /**
     * 将增量上下文作为系统消息注入到 session 中。
     */
    private void injectDeltaContext(Session session, String deltaText) {
        if (deltaText == null || deltaText.isBlank()) return;

        // 检查是否已经包含相同内容（去重）
        for (var msg : session.getMessages()) {
            if (msg.getRole() == com.jwcode.core.model.Message.Role.SYSTEM
                && msg.getTextContent() != null
                && msg.getTextContent().equals(deltaText)) {
                return; // 已注入，去重
            }
        }

        session.addMessage(
            com.jwcode.core.model.Message.createSystemMessage(
                "<context-update epoch=\"" + reconciler.getCurrentEpoch().getId()
                    + "\" rev=\"" + reconciler.getCurrentEpoch().getRevision()
                    + "\">\n" + deltaText + "\n</context-update>"
            )
        );
        logger.fine("[ContextReconcilerBridge] Injected delta context: "
            + deltaText.length() + " chars");
    }
}
