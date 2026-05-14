package com.jwcode.core.aicl;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * ContextAssembler — AICL 上下文组装与淘汰引擎。
 *
 * <p>核心职责：
 * <ol>
 *   <li>维护 ContextBlock 集合及其优先级/生命周期状态</li>
 *   <li>基于 Priority-LRU 算法按需淘汰块，确保 Token 预算不超限</li>
 *   <li>每轮对话结束时触发 tick()，更新 TTL 和衰减</li>
 * </ol>
 *
 * <h3>淘汰算法</h3>
 * <pre>
 * 当 used &gt; total * threshold 时：
 *   1. 按 priority 分组，从 optional 开始处理
 *   2. 同优先级内按 last-access 升序排序（LRU）
 *   3. 依次执行淘汰动作：
 *      optional: 直接移除
 *      low:      state → archived（保留元数据+摘要）
 *      medium:   state → summarized（保留摘要）
 *      high:     state → compressed（删除冗余格式）
 *      critical: 仅删注释，state 不变
 *      pinned:   跳过
 *   4. 每处理一个块，重新计算 used
 *   5. 当 used &lt;= total * stopThreshold 时停止
 * </pre>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class ContextAssembler {

    private static final Logger logger = Logger.getLogger(ContextAssembler.class.getName());

    /** 块存储：保持插入顺序（LinkedHashMap 做 LRU 有序访问） */
    private final Map<String, ContextBlock> blocks;

    /** 控制配置 */
    private final ContextControl control;

    /** 摘要生成器（可选，用于 LLM 生成摘要） */
    private Function<ContextBlock, String> summaryGenerator;

    /** 压缩器（可选，用于 LLM 同义压缩） */
    private Function<ContextBlock, String> compressor;

    /** 淘汰统计 */
    private final EvictionStats stats;

    public ContextAssembler() {
        this(ContextControl.EvictionConfig.DEFAULT);
    }

    public ContextAssembler(ContextControl.EvictionConfig evictionConfig) {
        this.blocks = new LinkedHashMap<>();
        this.control = new ContextControl(
                new ContextControl.ContextBudget(8000, 0),
                evictionConfig,
                Set.of("sys", "usr"),
                ContextControl.LifecycleDefaults.DEFAULT
        );
        this.stats = new EvictionStats();
    }

    public ContextAssembler(ContextControl control) {
        this.blocks = new LinkedHashMap<>();
        this.control = control;
        this.stats = new EvictionStats();
    }

    // ==================== 块管理 ====================

    /**
     * 注册（添加）一个上下文块。
     * 如果块 ID 已存在则覆盖。
     */
    public ContextAssembler registerBlock(ContextBlock block) {
        Objects.requireNonNull(block, "block must not be null");
        blocks.put(block.getId(), block);
        control.getBudget().recalculate(blocks.values());
        logger.fine("[ContextAssembler] registered block: " + block.getId());
        return this;
    }

    /**
     * 批量注册块。
     */
    public ContextAssembler registerBlocks(Collection<ContextBlock> newBlocks) {
        for (ContextBlock block : newBlocks) {
            blocks.put(block.getId(), block);
        }
        control.getBudget().recalculate(blocks.values());
        return this;
    }

    /**
     * 访问（触摸）一个块，更新 lastAccess 和 accessCount。
     */
    public Optional<ContextBlock> touchBlock(String blockId) {
        ContextBlock block = blocks.get(blockId);
        if (block != null) {
            block.touch();
        }
        return Optional.ofNullable(block);
    }

    /**
     * 获取块。
     */
    public Optional<ContextBlock> getBlock(String blockId) {
        return Optional.ofNullable(blocks.get(blockId));
    }

    /**
     * 移除块。
     */
    public ContextBlock removeBlock(String blockId) {
        ContextBlock removed = blocks.remove(blockId);
        if (removed != null) {
            control.getBudget().recalculate(blocks.values());
        }
        return removed;
    }

    /**
     * 获取所有活跃块（不含已删除/废弃的）。
     */
    public List<ContextBlock> getActiveBlocks() {
        return blocks.values().stream()
                .filter(b -> b.getState() != BlockLifecycle.DEPRECATED)
                .toList();
    }

    /**
     * 获取所有块（含各种状态）。
     */
    public Collection<ContextBlock> getAllBlocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    /**
     * 获取当前总有效 token 数。
     */
    public long getTotalUsedTokens() {
        return blocks.values().stream()
                .filter(b -> b.getState() != BlockLifecycle.DEPRECATED)
                .mapToLong(ContextBlock::effectiveTokens)
                .sum();
    }

    // ==================== 淘汰引擎 ====================

    /**
     * 执行淘汰算法，确保 token 使用率在安全范围内。
     *
     * @return 本次淘汰操作统计
     */
    public EvictionStats evict() {
        stats.reset();

        // 检查是否超过阈值
        double ratio = control.usageRatio();
        if (!control.isOverThreshold()) {
            logger.fine("[ContextAssembler] usage ratio " + String.format("%.1f%%", ratio * 100)
                    + " below threshold, no eviction needed");
            return stats;
        }

        logger.info("[ContextAssembler] eviction triggered: ratio=" + String.format("%.1f%%", ratio * 100)
                + ", threshold=" + String.format("%.1f%%", control.getEvictionConfig().getThreshold() * 100));

        // 1. 按优先级分组排序
        List<ContextBlock> sortedByPriority = getEvictableBlocksSorted();

        // 2. 从低优先级开始逐级淘汰
        BlockPriority[] priorityOrder = {
            BlockPriority.OPTIONAL, BlockPriority.LOW, BlockPriority.MEDIUM,
            BlockPriority.HIGH, BlockPriority.CRITICAL
        };

        for (BlockPriority currentPriority : priorityOrder) {
            if (ratioBelowStopThreshold()) break;

            List<ContextBlock> priorityGroup = sortedByPriority.stream()
                    .filter(b -> b.getPriority() == currentPriority)
                    .toList();

            for (ContextBlock block : priorityGroup) {
                if (ratioBelowStopThreshold()) break;

                applyEviction(block, currentPriority.getEvictionAction());
                control.getBudget().recalculate(blocks.values());

                if (ratioBelowStopThreshold()) {
                    logger.fine("[ContextAssembler] stop eviction at ratio="
                            + String.format("%.1f%%", control.usageRatio() * 100));
                    break;
                }
            }
        }

        logger.info("[ContextAssembler] eviction complete: " + stats);
        return stats;
    }

    /**
     * 获取可淘汰块列表，按优先级分组，每组内按 LRU 排序。
     */
    private List<ContextBlock> getEvictableBlocksSorted() {
        // 收集可淘汰块（非 pinned、非 deprecated）
        List<ContextBlock> evictable = blocks.values().stream()
                .filter(b -> b.getPriority() != BlockPriority.PINNED)
                .filter(b -> b.getState() != BlockLifecycle.DEPRECATED)
                .sorted(Comparator
                        .comparing(ContextBlock::getPriority, Comparator.comparingInt(BlockPriority::getLevel))
                        .thenComparing(ContextBlock::getLastAccess))
                .toList();
        return evictable;
    }

    /**
     * 对单个块执行淘汰动作。
     */
    private void applyEviction(ContextBlock block, BlockPriority.EvictionAction action) {
        ContextBlock updated = new ContextBlock(block);

        switch (action) {
            case REMOVE -> {
                blocks.remove(block.getId());
                stats.removedCount++;
                logger.fine("[ContextAssembler] REMOVED block: " + block.getId());
            }
            case ARCHIVE -> {
                // 保留元数据+摘要，清空内容
                if (updated.getGeneration() < control.getLifecycleDefaults().getMaxGeneration()) {
                    updated.setState(BlockLifecycle.ARCHIVED);
                    updated.decay(); // 触发 decay 更新 generation
                    String shortAbstract = buildShortAbstract(block);
                    updated.setBlockAbstract(shortAbstract);
                    updated.setContent("");
                    updated.setSummary("");
                    updated.setEstimatedTokens(50); // 仅元数据 tokens
                    blocks.put(block.getId(), updated);
                    stats.archivedCount++;
                    logger.fine("[ContextAssembler] ARCHIVED block: " + block.getId());
                } else {
                    // 超过最大代际，直接移除
                    blocks.remove(block.getId());
                    stats.removedCount++;
                }
            }
            case SUMMARIZE -> {
                if (updated.getGeneration() < control.getLifecycleDefaults().getMaxGeneration()) {
                    // 使用摘要替换 content
                    String summary = (summaryGenerator != null)
                            ? summaryGenerator.apply(block)
                            : buildDefaultSummary(block);
                    updated.setSummary(summary);
                    updated.setState(BlockLifecycle.SUMMARIZED);
                    updated.decay();
                    updated.setEstimatedTokens(estimateSummaryTokens(summary));
                    blocks.put(block.getId(), updated);
                    stats.summarizedCount++;
                    logger.fine("[ContextAssembler] SUMMARIZED block: " + block.getId());
                } else {
                    // 降级为归档
                    applyEviction(block, BlockPriority.EvictionAction.ARCHIVE);
                }
            }
            case COMPRESS -> {
                // 压缩格式：删除冗余空行/缩进
                String compressed = (compressor != null)
                        ? compressor.apply(block)
                        : compressFormat(block.getContent());
                updated.setContent(compressed);
                updated.setState(BlockLifecycle.COMPRESSED);
                updated.setEstimatedTokens(block.getEstimatedTokens() * 8 / 10);
                blocks.put(block.getId(), updated);
                stats.compressedCount++;
                logger.fine("[ContextAssembler] COMPRESSED block: " + block.getId());
            }
            case TRIM_COMMENTS -> {
                String trimmed = trimComments(block.getContent());
                updated.setContent(trimmed);
                updated.setEstimatedTokens(block.getEstimatedTokens() * 95 / 100);
                blocks.put(block.getId(), updated);
                stats.trimmedCount++;
            }
            case SKIP -> {
                stats.skippedCount++;
            }
        }
    }

    // ==================== 轮次操作 ====================

    /**
     * 每轮对话结束时调用：更新所有块的 TTL，衰减过期的块。
     */
    public void tick() {
        List<String> toDeprecate = new ArrayList<>();

        for (ContextBlock block : blocks.values()) {
            if (block.getPriority() == BlockPriority.PINNED) continue;
            if (block.getState() == BlockLifecycle.DEPRECATED) continue;

            // TTL 倒计时
            boolean expired = block.tick();

            if (expired) {
                // TTL 到期，衰减一级
                block.decay();
                if (block.getState() == BlockLifecycle.DEPRECATED) {
                    toDeprecate.add(block.getId());
                }
            }
        }

        // 清理废弃块
        for (String id : toDeprecate) {
            blocks.remove(id);
            stats.removedCount++;
            logger.fine("[ContextAssembler] deprecated block removed: " + id);
        }

        control.getBudget().recalculate(blocks.values());

        // 检查是否需要淘汰
        if (control.isOverThreshold()) {
            evict();
        }
    }

    // ==================== 摘要/压缩辅助 ====================

    /** 判断是否低于停止阈值 */
    private boolean ratioBelowStopThreshold() {
        return control.usageRatio() <= control.getEvictionConfig().getStopThreshold();
    }

    /** 构建简短摘要 */
    private String buildShortAbstract(ContextBlock block) {
        String label = block.getLabel();
        if (label != null && !label.isEmpty()) return "已归档: " + label;
        String ab = block.getBlockAbstract();
        if (ab != null && !ab.isEmpty()) return ab;
        String content = block.getContent();
        if (content != null && !content.isEmpty()) {
            return content.length() > 80 ? content.substring(0, 80) + "..." : content;
        }
        return "已归档";
    }

    /** 默认摘要（无 LLM 时的降级方案） */
    private String buildDefaultSummary(ContextBlock block) {
        String label = block.getLabel();
        String content = block.getContent();
        if (content == null || content.isEmpty()) return label;

        // 截取前 200 个字符
        String snippet = content.length() > 200 ? content.substring(0, 200) + "..." : content;
        return (label != null && !label.isEmpty())
                ? "[" + label + "] " + snippet
                : snippet;
    }

    /** 估算摘要 token 数 */
    private long estimateSummaryTokens(String summary) {
        if (summary == null || summary.isEmpty()) return 0;
        return Math.max(50, summary.length() / 3);
    }

    /** 格式压缩：删除连续空行、行首尾空白 */
    private String compressFormat(String content) {
        if (content == null || content.isEmpty()) return "";
        return content.replaceAll("\n\\s*\n", "\n").trim();
    }

    /** 删除注释（简单的 // 和 # 注释） */
    private String trimComments(String content) {
        if (content == null || content.isEmpty()) return "";
        return content.replaceAll("(?m)^\\s*//.*$", "")
                      .replaceAll("(?m)^\\s*#.*$", "")
                      .replaceAll("\n{2,}", "\n")
                      .trim();
    }

    // ==================== 配置 ====================

    public void setSummaryGenerator(Function<ContextBlock, String> generator) {
        this.summaryGenerator = generator;
    }

    public void setCompressor(Function<ContextBlock, String> compressor) {
        this.compressor = compressor;
    }

    public ContextControl getControl() { return control; }
    public EvictionStats getStats() { return stats; }
    public int getBlockCount() { return blocks.size(); }

    // ==================== 淘汰统计 ====================

    public static class EvictionStats {
        private int removedCount;
        private int archivedCount;
        private int summarizedCount;
        private int compressedCount;
        private int trimmedCount;
        private int skippedCount;

        public void reset() {
            removedCount = 0;
            archivedCount = 0;
            summarizedCount = 0;
            compressedCount = 0;
            trimmedCount = 0;
            skippedCount = 0;
        }

        public int getRemovedCount() { return removedCount; }
        public int getArchivedCount() { return archivedCount; }
        public int getSummarizedCount() { return summarizedCount; }
        public int getCompressedCount() { return compressedCount; }
        public int getTrimmedCount() { return trimmedCount; }
        public int getSkippedCount() { return skippedCount; }

        public int totalAffected() {
            return removedCount + archivedCount + summarizedCount + compressedCount + trimmedCount;
        }

        @Override
        public String toString() {
            return String.format("EvictionStats{removed=%d, archived=%d, summarized=%d, compressed=%d, trimmed=%d, skipped=%d}",
                    removedCount, archivedCount, summarizedCount, compressedCount, trimmedCount, skippedCount);
        }
    }
}
