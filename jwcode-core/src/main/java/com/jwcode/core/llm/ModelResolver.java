package com.jwcode.core.llm;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.JwcodeConfig.AgentModelBinding;
import com.jwcode.core.config.JwcodeConfig.ModelDefinition;
import com.jwcode.core.config.JwcodeConfig.ModelRefParts;
import com.jwcode.core.config.JwcodeConfig.ProviderConfig;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * 模型解析器 — 按 agentId + executionMode 解析实际使用的模型。
 *
 * <h3>三级优先级:</h3>
 * <ol>
 *   <li>Agent 指定模型（agent-model-bindings 中 mode=specified）</li>
 *   <li>模式默认模型（Plan 默认 / Act 默认）</li>
 *   <li>全局默认模型</li>
 * </ol>
 *
 * <h3>Fallback:</h3>
 * 若高优先级模型不可用，自动降级到下一优先级。
 * <b>最终返回的 modelRef 始终是可实际调用的模型</b>，
 * fallback=true 时额外记录 originalModelRef 和原因。
 */
public class ModelResolver {

    private static final Logger logger = Logger.getLogger(ModelResolver.class.getName());

    /** 执行模式常量 */
    public static final String MODE_PLAN = "plan";
    public static final String MODE_ACT = "act";
    public static final String MODE_GLOBAL = "global";

    private JwcodeConfig config;

    public ModelResolver(JwcodeConfig config) {
        this.config = config;
        if (config != null) {
            config.ensureDefaultsInitialized();
        }
    }

    /**
     * 按 agentId + executionMode 解析模型。
     * <p>
     * 返回的 {@link ResolvedModel#getModelRef()} 始终是实际可调用的模型引用
     * （usable=true 时），从不返回无效但标记为可用的结果。
     *
     * @param agentId       Agent ID（可为 null）
     * @param executionMode 执行模式: "plan", "act", "global"
     * @return 解析结果
     */
    public ResolvedModel resolveForAgent(String agentId, String executionMode) {
        try {
            return resolveInternal(agentId, executionMode);
        } catch (Exception e) {
            logger.severe("[ModelResolver] Unexpected error resolving model for agent="
                + agentId + " mode=" + executionMode + ": " + e.getMessage());
            return ResolvedModel.error("Internal error: " + e.getMessage());
        }
    }

    private ResolvedModel resolveInternal(String agentId, String executionMode) {
        if (config == null) {
            return ResolvedModel.error("No configuration loaded");
        }
        config.ensureDefaultsInitialized();

        String firstAttemptRef = null;  // 记录最高优先级尝试的模型，用于 fallback 报告

        // ---- Level 1: Agent-specified model ----
        if (agentId != null) {
            AgentModelBinding binding = config.getAgentModelBinding(agentId);
            if (binding != null && binding.isSpecified()) {
                String specifiedRef = binding.getModelRef();
                firstAttemptRef = specifiedRef;
                ResolvedModel result = validateModelRef(specifiedRef);
                if (result.isUsable()) {
                    return result;  // 指定模型可用，直接返回
                }
                logger.warning("[ModelResolver] Agent '" + agentId + "' specified model '"
                    + specifiedRef + "' unavailable (" + result.getFallbackReason()
                    + "). Falling back.");
            }
        }

        // ---- Level 2: Mode-specific default (plan / act) ----
        String modeKey = (executionMode != null) ? executionMode : MODE_GLOBAL;
        if (!MODE_GLOBAL.equals(modeKey)) {
            String modeRef = config.getDefaultModelRef(modeKey);
            if (modeRef != null) {
                if (firstAttemptRef == null) firstAttemptRef = modeRef;
                ResolvedModel result = validateModelRef(modeRef);
                if (result.isUsable()) {
                    // 如果是从更高优先级降级而来，包装为 fallback
                    if (firstAttemptRef != null && !modeRef.equals(firstAttemptRef)) {
                        return ResolvedModel.fallback(result.getModelRef(), result.getProvider(),
                            result.getModelId(), firstAttemptRef, "Mode '" + modeKey + "' default unavailable, fell back");
                    }
                    return result;
                }
                logger.warning("[ModelResolver] " + modeKey + "-default model '"
                    + modeRef + "' unavailable (" + result.getFallbackReason()
                    + "). Falling back to global default.");
            }
        }

        // ---- Level 3: Global default ----
        String globalRef = config.getDefaultModelRef(MODE_GLOBAL);
        if (globalRef == null) {
            return ResolvedModel.error("No default model configured. Please configure a default model.");
        }

        if (firstAttemptRef == null) firstAttemptRef = globalRef;
        ResolvedModel result = validateModelRef(globalRef);
        if (result.isUsable()) {
            // 如果是从高优先级降级而来，标记 fallback
            if (firstAttemptRef != null && !globalRef.equals(firstAttemptRef)) {
                return ResolvedModel.fallback(result.getModelRef(), result.getProvider(),
                    result.getModelId(), firstAttemptRef,
                    "Preferred model unavailable (" + result.getFallbackReason() + "), fell back to global default");
            }
            return result;
        }

        // All levels failed
        return ResolvedModel.error("Global default model '" + globalRef + "' unavailable: "
            + result.getFallbackReason()
            + ". Please configure a valid default model.");
    }

    /**
     * 验证 modelRef 对应的模型是否可用。
     *
     * @return 可用时返回 {@link ResolvedModel#usable}，不可用时返回 {@link ResolvedModel#invalid}
     */
    private ResolvedModel validateModelRef(String modelRef) {
        ModelRefParts parts = JwcodeConfig.parseModelRef(modelRef);
        if (parts == null) {
            return ResolvedModel.invalid(modelRef, "Invalid model reference format: '" + modelRef + "'");
        }

        ProviderConfig provider = config.getProvider(parts.getProvider());
        if (provider == null) {
            return ResolvedModel.invalid(modelRef, "Provider '" + parts.getProvider() + "' not found in configuration");
        }

        // Check API key availability
        if (provider.getApiKeys() == null || provider.getApiKeys().isEmpty()) {
            return ResolvedModel.invalid(modelRef, "Provider '" + parts.getProvider() + "' has no API keys configured");
        }
        boolean hasValidKey = provider.getApiKeys().stream()
            .anyMatch(k -> k != null && !k.isBlank() && !k.contains("your-api-key") && k.length() >= 20);
        if (!hasValidKey) {
            return ResolvedModel.invalid(modelRef, "Provider '" + parts.getProvider() + "' has no valid API keys");
        }

        // Find model definition
        Optional<ModelDefinition> modelOpt = provider.findModel(parts.getModelId());
        if (modelOpt.isEmpty()) {
            return ResolvedModel.invalid(modelRef, "Model '" + parts.getModelId() + "' not found in provider '" + parts.getProvider() + "'");
        }

        ModelDefinition model = modelOpt.get();
        if (!model.isEnabled()) {
            return ResolvedModel.invalid(modelRef, "Model '" + parts.getModelId() + "' is disabled");
        }

        return ResolvedModel.usable(modelRef, parts.getProvider(), parts.getModelId());
    }

    /**
     * 获取指定 ModelRef 的 ProviderConfig
     */
    public ProviderConfig getProviderConfig(String modelRef) {
        ModelRefParts parts = JwcodeConfig.parseModelRef(modelRef);
        if (parts == null) return null;
        return config.getProvider(parts.getProvider());
    }

    /**
     * 刷新配置引用
     */
    public void refreshConfig(JwcodeConfig newConfig) {
        this.config = newConfig;
        if (config != null) {
            config.ensureDefaultsInitialized();
        }
    }
}
