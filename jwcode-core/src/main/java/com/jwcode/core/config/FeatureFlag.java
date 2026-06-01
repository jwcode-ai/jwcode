package com.jwcode.core.config;

/**
 * FeatureFlag — 特性开关枚举（对标 Claude Code GrowthBook 特性标记体系）。
 *
 * <p>所有新增功能通过此枚举控制启用/禁用，支持三级优先级：
 * 环境变量 > .jwcode/features.json > 默认值。</p>
 *
 * <p>使用方式：
 * <pre>
 *   if (featureConfig.isEnabled(FeatureFlag.MICRO_COMPACT)) {
 *       microCompactService.compact(...);
 *   }
 * </pre>
 * </p>
 *
 * @author JWCode Team
 * @since 3.1.0
 */
public enum FeatureFlag {

    // ==================== 记忆工程 ====================

    /** 语义记忆检索 — findRelevantMemories 相关度排序 */
    RELEVANT_MEMORY_INJECTION("memory.relevantInjection", true),

    // ==================== 压缩 ====================

    /** MicroCompact — 选择性单条工具结果压缩 */
    MICRO_COMPACT("compact.microCompact", true),

    /** AutoMicroCompact — 工具结果返回后自动触发微压缩 */
    AUTO_MICRO_COMPACT("compact.autoMicroCompact", false),

    /** GraduatedEscalation — 分级升级压缩管道 */
    GRADUATED_ESCALATION("compact.graduatedEscalation", false),

    // ==================== Prompt 工程 ====================

    /** PromptSectionCache — 系统提示段落级缓存 */
    PROMPT_SECTION_CACHE("prompt.sectionCache", true),

    // ==================== API 诊断 ====================

    /** PromptCacheBreakDetection — 缓存断裂检测 */
    CACHE_BREAK_DETECTION("prompt.cacheBreakDetection", false);

    // ==================== 字段 ====================

    private final String configKey;
    private final boolean defaultValue;

    FeatureFlag(String configKey, boolean defaultValue) {
        this.configKey = configKey;
        this.defaultValue = defaultValue;
    }

    public String getConfigKey() { return configKey; }
    public boolean getDefaultValue() { return defaultValue; }
}
