package com.jwcode.core.context;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * ContextReconciler — 上下文协调运行时。
 *
 * <p>核心职责：
 * <ol>
 *   <li>管理当前 {@link ContextEpoch} 的生命周期</li>
 *   <li>定期 {@link ContextEpoch#reconcile(ContextRegistry)} 检测上下文变更</li>
 *   <li>在 compaction / agent 切换时创建新 epoch</li>
 *   <li>返回增量文本供 LLM 系统消息注入</li>
 * </ol>
 *
 * <p>集成点：{@code LLMQueryEngine} 在每次 LLM 调用前调用
 * {@link #beforeLlmCall(String)}，获取增量上下文。
 */
public class ContextReconciler {

    private static final Logger logger = Logger.getLogger(ContextReconciler.class.getName());

    private final ContextRegistry registry;
    private ContextEpoch currentEpoch;

    /** 自动 reconcile 间隔（毫秒） */
    private long reconcileIntervalMs = 30_000; // 30s
    private Instant lastReconcileTime = Instant.now();

    public ContextReconciler(ContextRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public ContextRegistry getRegistry() { return registry; }
    public ContextEpoch getCurrentEpoch() { return currentEpoch; }

    public void setReconcileIntervalMs(long ms) { this.reconcileIntervalMs = ms; }

    // ==================== 生命周期 ====================

    /**
     * 初始化第一个 epoch。
     */
    public synchronized boolean initialize(String agentId) {
        ContextEpoch epoch = new ContextEpoch(agentId);
        if (!epoch.initialize(registry)) {
            logger.warning("[ContextReconciler] Failed to initialize first epoch");
            return false;
        }
        this.currentEpoch = epoch;
        lastReconcileTime = Instant.now();
        logger.info("[ContextReconciler] Initialized epoch " + epoch.getId()
            + " for agent=" + agentId + ", baseline="
            + epoch.getBaselineText().length() + " chars");
        return true;
    }

    /**
     * 在每次 LLM 调用前调用。返回需要注入的增量上下文文本。
     *
     * <p>返回值含义：
     * <ul>
     *   <li>null — 无变化，无需注入</li>
     *   <li>空字符串 "" — 触发替换（调用方应创建新 epoch）</li>
     *   <li>非空字符串 — 增量更新文本，应注入到系统消息</li>
     * </ul>
     */
    public synchronized String beforeLlmCall(String currentAgentId) {
        if (currentEpoch == null) {
            initialize(currentAgentId);
            return null;
        }

        // 1. agent 切换 → 需创建新 epoch
        if (!currentEpoch.getAgentId().equals(currentAgentId)) {
            logger.info("[ContextReconciler] Agent switch: " + currentEpoch.getAgentId()
                + " -> " + currentAgentId);
            return ""; // 触发 replace
        }

        // 2. 检查是否到 reconcile 时间
        Instant now = Instant.now();
        if (now.toEpochMilli() - lastReconcileTime.toEpochMilli() < reconcileIntervalMs) {
            return null; // 未到时间
        }

        // 3. 执行 reconcile
        ContextEpoch.ReconcileResult result = currentEpoch.reconcile(registry);
        lastReconcileTime = now;

        if (result.isUnchanged()) {
            return null;
        } else if (result.isUpdated()) {
            return result.getDeltaText();
        } else {
            return ""; // replace — 触发新 epoch
        }
    }

    /**
     * 创建新 epoch（compaction / agent 切换后调用）。
     */
    public synchronized boolean newEpoch(String agentId) {
        ContextEpoch epoch = new ContextEpoch(agentId);
        if (!epoch.initialize(registry)) {
            logger.warning("[ContextReconciler] New epoch init failed for agent=" + agentId);
            return false;
        }
        ContextEpoch old = this.currentEpoch;
        this.currentEpoch = epoch;
        lastReconcileTime = Instant.now();

        if (old != null) {
            epoch.getMetadata().put("previousEpochId", old.getId());
            epoch.getMetadata().put("previousEpochRevision", old.getRevision());
        }
        logger.info("[ContextReconciler] New epoch " + epoch.getId()
            + " (was " + (old != null ? old.getId() : "none") + ")");
        return true;
    }

    /**
     * 标记替换（compaction 后，全量替换上下文）。
     */
    public synchronized boolean replaceEpoch(String agentId) {
        ContextEpoch epoch = new ContextEpoch(agentId);
        if (!epoch.replace(registry)) {
            logger.warning("[ContextReconciler] Epoch replace failed");
            return false;
        }
        ContextEpoch old = this.currentEpoch;
        this.currentEpoch = epoch;
        epoch.bumpRevision();
        lastReconcileTime = Instant.now();

        if (old != null) {
            epoch.getMetadata().put("previousEpochId", old.getId());
        }
        logger.info("[ContextReconciler] Replaced epoch " + epoch.getId()
            + " (revision=" + epoch.getRevision() + ")");
        return true;
    }

    /**
     * 获取当前基线文本（即系统上下文的渲染结果）。
     */
    public String getBaselineText() {
        return currentEpoch != null ? currentEpoch.getBaselineText() : "";
    }

    // ==================== 统计 ====================

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("currentEpochId", currentEpoch != null ? currentEpoch.getId() : null);
        stats.put("currentAgentId", currentEpoch != null ? currentEpoch.getAgentId() : null);
        stats.put("epochRevision", currentEpoch != null ? currentEpoch.getRevision() : 0);
        stats.put("reconcileIntervalMs", reconcileIntervalMs);
        stats.put("lastReconcileTime", lastReconcileTime.toString());
        stats.put("registry", registry.getStats());
        return stats;
    }
}
