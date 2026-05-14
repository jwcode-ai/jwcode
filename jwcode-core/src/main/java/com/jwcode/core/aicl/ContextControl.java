package com.jwcode.core.aicl;

import java.util.*;

/**
 * AICL 控制层模型 — 对应 {@code <ctx:control>}。
 *
 * <p>包含 Token 预算、淘汰策略、永固块列表、生命周期默认配置。</p>
 *
 * <pre>
 * &lt;ctx:control&gt;
 *   &lt;ctx:budget total="8000" used="6200" remaining="1800"/&gt;
 *   &lt;ctx:strategy&gt;
 *     &lt;ctx:eviction policy="priority-lru" threshold="0.8"/&gt;
 *     &lt;ctx:pin ids="sys,usr"/&gt;
 *     &lt;ctx:lifecycle default-ttl="3" max-generation="2"/&gt;
 *   &lt;/ctx:strategy&gt;
 * &lt;/ctx:control&gt;
 * </pre>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class ContextControl {

    /** Token 预算 */
    private final ContextBudget budget;

    /** 淘汰策略配置 */
    private final EvictionConfig evictionConfig;

    /** 固定保留的块 ID 列表 */
    private final Set<String> pinnedIds;

    /** 生命周期默认配置 */
    private final LifecycleDefaults lifecycleDefaults;

    public ContextControl(ContextBudget budget, EvictionConfig evictionConfig,
                          Set<String> pinnedIds, LifecycleDefaults lifecycleDefaults) {
        this.budget = Objects.requireNonNullElse(budget, new ContextBudget(8000, 0));
        this.evictionConfig = Objects.requireNonNullElse(evictionConfig, EvictionConfig.DEFAULT);
        this.pinnedIds = Objects.requireNonNullElse(pinnedIds, Set.of("sys", "usr"));
        this.lifecycleDefaults = Objects.requireNonNullElse(lifecycleDefaults, LifecycleDefaults.DEFAULT);
    }

    // ===== Getters =====
    public ContextBudget getBudget() { return budget; }
    public EvictionConfig getEvictionConfig() { return evictionConfig; }
    public Set<String> getPinnedIds() { return Collections.unmodifiableSet(pinnedIds); }
    public LifecycleDefaults getLifecycleDefaults() { return lifecycleDefaults; }

    /**
     * 当前 token 使用率（0.0 ~ 1.0）。
     */
    public double usageRatio() {
        return budget.usageRatio();
    }

    /**
     * 是否超过淘汰阈值。
     */
    public boolean isOverThreshold() {
        return usageRatio() > evictionConfig.getThreshold();
    }

    // ==================== 内部类：Token 预算 ====================

    public static class ContextBudget {
        private final long total;
        private long used;
        private long remaining;

        public ContextBudget(long total, long used) {
            this.total = total;
            this.used = used;
            this.remaining = total - used;
        }

        /** 从块集合计算 used */
        public static ContextBudget fromBlocks(long totalBudget, Collection<ContextBlock> blocks) {
            long used = blocks.stream().mapToLong(ContextBlock::effectiveTokens).sum();
            return new ContextBudget(totalBudget, used);
        }

        public long getTotal() { return total; }
        public long getUsed() { return used; }
        public long getRemaining() { return remaining; }
        public double usageRatio() { return total > 0 ? (double) used / total : 0.0; }

        /** 更新使用量 */
        public void recalculate(Collection<ContextBlock> blocks) {
            this.used = blocks.stream().mapToLong(ContextBlock::effectiveTokens).sum();
            this.remaining = total - this.used;
        }
    }

    // ==================== 内部类：淘汰策略配置 ====================

    public static class EvictionConfig {
        public static final EvictionConfig DEFAULT = new EvictionConfig("priority-lru", 0.8);

        private final String policy;       // priority-lru
        private final double threshold;    // 触发阈值（0~1）
        private final double stopThreshold; // 停止阈值（低于此值停止淘汰）

        public EvictionConfig(String policy, double threshold) {
            this(policy, threshold, threshold - 0.05);
        }

        public EvictionConfig(String policy, double threshold, double stopThreshold) {
            this.policy = policy;
            this.threshold = Math.max(0.0, Math.min(1.0, threshold));
            this.stopThreshold = Math.max(0.0, Math.min(this.threshold, stopThreshold));
        }

        public String getPolicy() { return policy; }
        public double getThreshold() { return threshold; }
        public double getStopThreshold() { return stopThreshold; }
    }

    // ==================== 内部类：生命周期默认配置 ====================

    public static class LifecycleDefaults {
        public static final LifecycleDefaults DEFAULT = new LifecycleDefaults(3, 2);

        private final int defaultTtl;       // 默认存活轮数，-1 表示永久
        private final int maxGeneration;    // 最大压缩代际

        public LifecycleDefaults(int defaultTtl, int maxGeneration) {
            this.defaultTtl = defaultTtl;
            this.maxGeneration = Math.max(1, maxGeneration);
        }

        public int getDefaultTtl() { return defaultTtl; }
        public int getMaxGeneration() { return maxGeneration; }
    }
}
