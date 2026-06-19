package com.jwcode.core.llm;

/**
 * 模型解析结果
 * <p>
 * 包含实际使用的模型引用、是否降级、降级原因等信息。
 * <b>modelRef 始终指向实际可调用的模型</b>（usable=true 时），
 * 若降级则额外记录原始模型引用和原因。
 * </p>
 *
 * <h3>实例化方法选择:</h3>
 * <ul>
 *   <li>{@link #usable(String, String, String)} — 模型可用，无降级</li>
 *   <li>{@link #fallback(String, String, String, String, String)} — 可用但发生了降级</li>
 *   <li>{@link #invalid(String, String)} — 该模型不可用（继续尝试下一优先级）</li>
 *   <li>{@link #error(String)} — 所有备选都不可用，返回错误</li>
 * </ul>
 */
public class ResolvedModel {

    /** 实际使用的模型引用 "provider:modelId"（可用时有效） */
    private final String modelRef;

    /** 提供商名称 */
    private final String provider;

    /** 模型 ID */
    private final String modelId;

    /** 是否可用 */
    private final boolean usable;

    /** 是否发生了降级（指定/模式默认不可用 → 用更低优先级的模型） */
    private final boolean fallback;

    /** 降级原因 */
    private final String fallbackReason;

    /** 降级前尝试的原始模型引用（仅 fallback=true 时有意义） */
    private final String originalModelRef;

    /** 错误消息（usable=false 时） */
    private final String error;

    private ResolvedModel(String modelRef, String provider, String modelId,
                          boolean usable, boolean fallback, String fallbackReason,
                          String originalModelRef, String error) {
        this.modelRef = modelRef;
        this.provider = provider;
        this.modelId = modelId;
        this.usable = usable;
        this.fallback = fallback;
        this.fallbackReason = fallbackReason;
        this.originalModelRef = originalModelRef;
        this.error = error;
    }

    // ==================== Factory methods ====================

    /**
     * 模型可用，无降级。
     */
    public static ResolvedModel usable(String modelRef, String provider, String modelId) {
        return new ResolvedModel(modelRef, provider, modelId, true, false, null, null, null);
    }

    /**
     * 模型可用，但发生了降级。
     * modelRef/provider/modelId 是实际使用的（降级后的）模型，
     * originalModelRef 是因故障未能使用的原始期望模型。
     *
     * @param modelRef          实际使用的模型引用
     * @param provider          实际使用的提供商
     * @param modelId           实际使用的模型 ID
     * @param originalModelRef  降级前期望使用的模型引用（不可用）
     * @param reason            降级原因
     */
    public static ResolvedModel fallback(String modelRef, String provider, String modelId,
                                         String originalModelRef, String reason) {
        return new ResolvedModel(modelRef, provider, modelId, true, true, reason, originalModelRef, null);
    }

    /**
     * 模型不可用（可继续尝试下一优先级）。
     */
    public static ResolvedModel invalid(String modelRef, String reason) {
        return new ResolvedModel(modelRef, null, null, false, false, reason, null, null);
    }

    /**
     * 无可用模型。
     */
    public static ResolvedModel error(String error) {
        return new ResolvedModel(null, null, null, false, false, null, null, error);
    }

    // ==================== Getters ====================

    public String getModelRef() { return modelRef; }
    public String getProvider() { return provider; }
    public String getModelId() { return modelId; }
    public boolean isUsable() { return usable; }
    public boolean isFallback() { return fallback; }
    public String getFallbackReason() { return fallbackReason; }
    public String getOriginalModelRef() { return originalModelRef; }
    public String getError() { return error; }

    /**
     * 转换为前端友好的 Map
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("modelRef", modelRef);
        map.put("provider", provider);
        map.put("modelId", modelId);
        map.put("usable", usable);
        map.put("fallback", fallback);
        map.put("fallbackReason", fallbackReason);
        map.put("error", error);
        return map;
    }

    @Override
    public String toString() {
        if (!usable) return "ResolvedModel{error='" + (error != null ? error : fallbackReason) + "'}";
        if (fallback) return "ResolvedModel{modelRef='" + modelRef + "', fallback=true, originalModelRef='"
            + originalModelRef + "', reason='" + fallbackReason + "'}";
        return "ResolvedModel{modelRef='" + modelRef + "'}";
    }
}
