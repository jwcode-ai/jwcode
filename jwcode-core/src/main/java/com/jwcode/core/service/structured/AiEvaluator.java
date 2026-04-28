package com.jwcode.core.service.structured;

/**
 * AI 评估器接口
 * 
 * 用于调用 LLM 对上下文进行评估，决定哪些消息可以丢弃
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface AiEvaluator {
    
    /**
     * 对当前上下文进行评估
     * 
     * @param contextManager 上下文管理器
     * @param trigger 触发类型
     * @return 评估结果
     */
    EvaluationResult evaluate(StructuredContextManager contextManager, EvaluationTrigger trigger);
    
    /**
     * 评估单条消息的重要性
     * 
     * @param message 消息
     * @param contextManager 上下文管理器
     * @return 重要性评分 (0.0 ~ 1.0)
     */
    double evaluateImportance(StructuredMessage message, StructuredContextManager contextManager);
    
    /**
     * 检查两条消息是否属于同一任务
     * 
     * @param msg1 消息1
     * @param msg2 消息2
     * @return 是否属于同一任务
     */
    boolean isSameTask(StructuredMessage msg1, StructuredMessage msg2);
}