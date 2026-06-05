package com.jwcode.core.service;

/**
 * MicroCompactConfig — 微压缩配置 POJO。
 *
 * <p>控制单条工具结果的轻量级压缩行为，不触发完整 compaction。</p>
 *
 * @author JWCode Team
 * @since 3.1.0
 */
public class MicroCompactConfig {

    /** 自动压缩触发阈值（字符数），默认 2000 */
    private int charThreshold = 2000;

    /** 是否启用自动微压缩 */
    private boolean autoCompact = true;

    /** 最高保留等级（低于此等级的结果会被进一步压缩） */
    private Tier maxTier = Tier.MEDIUM;

    /** 最大保留字符数 */
    private int maxRetainedChars = 500;

    // ==================== 基于时间的 MicroCompaction (Prompt Cache TTL) ====================

    /** 是否启用基于时间间隔的清理（默认关闭，对标 Claude Code timeBasedMC） */
    private boolean timeBasedEnabled = false;

    /** 时间间隔阈值（分钟），默认 60min = Prompt Cache TTL */
    private int gapThresholdMinutes = 60;

    /** 保留最近的 N 个可压缩工具结果，更早的全部替换 */
    private int keepRecent = 5;

    public enum Tier {
        /** 错误/异常 — 完整保留 */
        CRITICAL,
        /** 文件修改 — 路径 + 变更摘要 */
        HIGH,
        /** 读取工具 — 路径 + 首尾 */
        MEDIUM,
        /** 命令执行 — 退出码 + 尾部 */
        LOW,
        /** 其他 — 仅成功/失败 */
        MINIMAL
    }

    public MicroCompactConfig() {}

    public MicroCompactConfig(int charThreshold, boolean autoCompact, Tier maxTier, int maxRetainedChars) {
        this.charThreshold = charThreshold;
        this.autoCompact = autoCompact;
        this.maxTier = maxTier;
        this.maxRetainedChars = maxRetainedChars;
    }

    public int getCharThreshold() { return charThreshold; }
    public void setCharThreshold(int charThreshold) { this.charThreshold = charThreshold; }
    public boolean isAutoCompact() { return autoCompact; }
    public void setAutoCompact(boolean autoCompact) { this.autoCompact = autoCompact; }
    public Tier getMaxTier() { return maxTier; }
    public void setMaxTier(Tier maxTier) { this.maxTier = maxTier; }
    public int getMaxRetainedChars() { return maxRetainedChars; }
    public void setMaxRetainedChars(int maxRetainedChars) { this.maxRetainedChars = maxRetainedChars; }

    public boolean isTimeBasedEnabled() { return timeBasedEnabled; }
    public void setTimeBasedEnabled(boolean timeBasedEnabled) { this.timeBasedEnabled = timeBasedEnabled; }
    public int getGapThresholdMinutes() { return gapThresholdMinutes; }
    public void setGapThresholdMinutes(int gapThresholdMinutes) { this.gapThresholdMinutes = gapThresholdMinutes; }
    public int getKeepRecent() { return keepRecent; }
    public void setKeepRecent(int keepRecent) { this.keepRecent = keepRecent; }
}
