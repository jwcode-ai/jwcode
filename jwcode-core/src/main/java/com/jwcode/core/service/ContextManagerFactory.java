package com.jwcode.core.service;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.service.structured.*;

/**
 * 上下文管理器工厂
 * 
 * 根据配置创建合适的上下文管理器：
 * - 如果启用了 structured-context-enabled，使用 StructuredContextManager
 * - 否则使用现有的 ContextWindowManager
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ContextManagerFactory {
    
    /**
     * 创建上下文管理器
     * 
     * @param config 配置
     * @return 返回 StructuredContextManager 或 ContextWindowManager
     */
    public static Object create(JwcodeConfig config) {
        if (config == null) {
            return new ContextWindowManager();
        }
        
        JwcodeConfig.CompressionSettings compression = config.getSettings().getCompression();
        
        // 检查是否启用结构化上下文管理
        if (compression.isStructuredContextEnabled()) {
            return createStructuredManager(compression);
        }
        
        // 使用现有的 ContextWindowManager
        JwcodeConfig.EngineSettings engine = config.getSettings().getEngine();
        return new ContextWindowManager(
            (int) engine.getTokenBudget(),
            compression.getMaxMessagesBeforeCompression(),
            compression.getMaxMessagesBeforeCompression() / 10
        );
    }
    
    /**
     * 创建结构化上下文管理器
     */
    private static StructuredContextManager createStructuredManager(JwcodeConfig.CompressionSettings compression) {
        StructuredContextManager manager = new StructuredContextManager(
            "session-" + System.currentTimeMillis(),
            compression.getMaxActiveSize(),
            compression.getMinRetainCount(),
            compression.isEnableArchive(),
            compression.getPeriodicEvalInterval()
        );
        
        // 配置安全守卫
        ContextSafetyGuard guard = manager.getSafetyGuard();
        guard.setEnableIntentProtection(compression.isEnableIntentProtection());
        guard.setEnableRefCountProtection(compression.isEnableRefCountProtection());
        guard.setEnableRecentProtection(compression.isEnableRecentProtection());
        guard.setRecentMessageProtection(compression.getRecentProtectionCount());
        guard.setMinRetainCount(compression.getMinRetainCount());
        
        // 配置 AI 评估器
        if (compression.isEnableAiEvaluation()) {
            LLMAiEvaluator evaluator = new LLMAiEvaluator();
            manager.setAiEvaluator(evaluator);
        }
        
        return manager;
    }
}