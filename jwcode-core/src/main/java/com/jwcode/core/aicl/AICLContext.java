package com.jwcode.core.aicl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AICLContext — AICL 完整上下文容器。
 *
 * <p>对应 {@code <ctx:context>} 根元素，聚合所有上下文块和控制配置。
 * 是序列化/反序列化的顶层入口，也是 Assembler 操作的主数据结构。</p>
 *
 * <pre>
 * &lt;ctx:context xmlns:ctx="http://aicl.org/v1" version="1.0"
 *              session="sess-8f3a9b" turn="4"&gt;
 *   &lt;ctx:control&gt;...&lt;/ctx:control&gt;
 *   &lt;ctx:blocks&gt;...&lt;/ctx:blocks&gt;
 *   &lt;ctx:trace&gt;...&lt;/ctx:trace&gt;
 * &lt;/ctx:context&gt;
 * </pre>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class AICLContext {

    private final String sessionId;
    private int currentTurn;
    private final ContextControl control;
    private final Map<String, ContextBlock> blocks;
    private String checksum;

    /**
     * 创建 AICL 上下文。
     */
    public AICLContext(String sessionId, int currentTurn, ContextControl control,
                       Collection<ContextBlock> blocks) {
        this.sessionId = Objects.requireNonNullElse(sessionId, "default");
        this.currentTurn = currentTurn;
        this.control = Objects.requireNonNullElseGet(control,
                () -> new ContextControl(
                        new ContextControl.ContextBudget(8000, 0),
                        ContextControl.EvictionConfig.DEFAULT,
                        Set.of("sys", "usr"),
                        ContextControl.LifecycleDefaults.DEFAULT
                ));
        this.blocks = new LinkedHashMap<>();
        if (blocks != null) {
            for (ContextBlock block : blocks) {
                this.blocks.put(block.getId(), block);
            }
        }
        this.checksum = computeChecksum();
    }

    /**
     * 创建空 AICL 上下文（使用默认配置）。
     */
    public static AICLContext empty(String sessionId, int turn) {
        return new AICLContext(sessionId, turn, null, List.of());
    }

    // ==================== 块管理 ====================

    /**
     * 添加块。
     */
    public AICLContext addBlock(ContextBlock block) {
        blocks.put(block.getId(), block);
        control.getBudget().recalculate(blocks.values());
        invalidateChecksum();
        return this;
    }

    /**
     * 批量添加块。
     */
    public AICLContext addBlocks(Collection<ContextBlock> newBlocks) {
        for (ContextBlock block : newBlocks) {
            blocks.put(block.getId(), block);
        }
        control.getBudget().recalculate(blocks.values());
        invalidateChecksum();
        return this;
    }

    /**
     * 获取块。
     */
    public Optional<ContextBlock> getBlock(String id) {
        return Optional.ofNullable(blocks.get(id));
    }

    /**
     * 移除块。
     */
    public boolean removeBlock(String id) {
        boolean removed = blocks.remove(id) != null;
        if (removed) {
            control.getBudget().recalculate(blocks.values());
            invalidateChecksum();
        }
        return removed;
    }

    /**
     * 获取所有块（按注册顺序）。
     */
    public List<ContextBlock> getBlocks() {
        return List.copyOf(blocks.values());
    }

    /**
     * 获取活跃块（非废弃、非归档）。
     */
    public List<ContextBlock> getActiveBlocks() {
        return blocks.values().stream()
                .filter(b -> b.getState() != BlockLifecycle.DEPRECATED)
                .filter(b -> b.getState() != BlockLifecycle.ARCHIVED)
                .toList();
    }

    /**
     * 按类型查找块。
     */
    public List<ContextBlock> findBlocksByType(String type) {
        return blocks.values().stream()
                .filter(b -> type.equals(b.getType()))
                .toList();
    }

    /**
     * 按优先级查找块。
     */
    public List<ContextBlock> findBlocksByPriority(BlockPriority priority) {
        return blocks.values().stream()
                .filter(b -> b.getPriority() == priority)
                .toList();
    }

    // ==================== 状态操作 ====================

    /**
     * 进入下一轮。
     */
    public void nextTurn() {
        currentTurn++;
        invalidateChecksum();
    }

    /**
     * 刷新预算统计。
     */
    public void refreshBudget() {
        control.getBudget().recalculate(blocks.values());
    }

    // ==================== 统计 ====================

    public int getBlockCount() {
        return blocks.size();
    }

    public long getTotalTokens() {
        return blocks.values().stream()
                .filter(b -> b.getState() != BlockLifecycle.DEPRECATED)
                .mapToLong(ContextBlock::effectiveTokens)
                .sum();
    }

    public double getUsageRatio() {
        return control.usageRatio();
    }

    // ==================== Getters ====================

    public String getSessionId() { return sessionId; }
    public int getCurrentTurn() { return currentTurn; }
    public ContextControl getControl() { return control; }
    public String getChecksum() { return checksum; }

    // ==================== 校验 ====================

    private void invalidateChecksum() {
        this.checksum = computeChecksum();
    }

    private String computeChecksum() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = blocks.values().stream()
                    .map(b -> b.getId() + ":" + b.getState().getState())
                    .collect(Collectors.joining(","));
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().substring(0, 16) + "...";
        } catch (NoSuchAlgorithmException e) {
            return "checksum-unavailable";
        }
    }

    @Override
    public String toString() {
        return String.format("AICLContext{session=%s, turn=%d, blocks=%d, tokens=%d, ratio=%.1f%%}",
                sessionId, currentTurn, blocks.size(), getTotalTokens(), getUsageRatio() * 100);
    }
}
