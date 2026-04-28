package com.jwcode.core.service.structured;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 LLM 的 AI 评估器实现
 * 
 * 通过调用 LLM 来评估上下文，决定哪些消息可以丢弃
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LLMAiEvaluator implements AiEvaluator {
    
    private static final Logger logger = Logger.getLogger(LLMAiEvaluator.class.getName());
    
    // 使用占位符，需要由外部注入 LLM 服务
    private Object llmService;
    private String systemPrompt;
    
    public LLMAiEvaluator() {
        this.systemPrompt = buildDefaultSystemPrompt();
    }
    
    public LLMAiEvaluator(Object llmService) {
        this.llmService = llmService;
        this.systemPrompt = buildDefaultSystemPrompt();
    }
    
    @Override
    public EvaluationResult evaluate(StructuredContextManager contextManager, EvaluationTrigger trigger) {
        logger.info("[LLMAiEvaluator] 开始 LLM 评估...");
        
        // 构建评估 Prompt
        String evalPrompt = buildEvaluationPrompt(contextManager, trigger);
        
        // 调用 LLM（简化版本：用规则+模拟）
        // 实际实现中应该调用 llmService.chat(prompt)
        
        // 模拟 LLM 返回结果
        return simulateLLMResponse(contextManager, trigger);
    }
    
    @Override
    public double evaluateImportance(StructuredMessage message, StructuredContextManager contextManager) {
        MessageType type = message.getType();
        
        // 基于类型的默认重要性
        double baseImportance = 0.5;
        
        switch (type) {
            case INTENT:
                baseImportance = 0.95;
                break;
            case QUESTION:
                baseImportance = 0.7;
                break;
            case ANSWER:
                baseImportance = 0.6;
                break;
            case DECISION:
                baseImportance = 0.85;
                break;
            case CONFIRMATION:
                baseImportance = 0.4;
                break;
            case TOOL_RESULT:
                baseImportance = message.getContent().contains("Error") ? 0.8 : 0.2;
                break;
            case ERROR:
                baseImportance = 0.9;
                break;
            case SYSTEM_EVENT:
                baseImportance = 0.3;
                break;
            case CLARIFICATION:
                baseImportance = 0.5;
                break;
            case INTERIM_DISCUSSION:
                baseImportance = 0.3;
                break;
        }
        
        // 根据引用计数调整
        if (message.getRefCount() > 0) {
            baseImportance = Math.min(1.0, baseImportance + 0.1 * message.getRefCount());
        }
        
        // 根据消息长度调整（长消息略微重要）
        if (message.getContent() != null && message.getContent().length() > 100) {
            baseImportance = Math.min(1.0, baseImportance + 0.1);
        }
        
        return baseImportance;
    }
    
    @Override
    public boolean isSameTask(StructuredMessage msg1, StructuredMessage msg2) {
        if (msg1 == null || msg2 == null) {
            return false;
        }
        
        // 检查任务ID
        String taskId1 = msg1.getTaskId();
        String taskId2 = msg2.getTaskId();
        if (taskId1 != null && taskId1.equals(taskId2)) {
            return true;
        }
        
        // 检查标签
        List<String> tags1 = msg1.getTags();
        List<String> tags2 = msg2.getTags();
        if (tags1 != null && tags2 != null) {
            for (String tag : tags1) {
                if (tags2.contains(tag)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // ==================== Prompt 构建 ====================
    
    private String buildDefaultSystemPrompt() {
        return """
            ## 上下文管理协议
            
            你有责任管理自己的上下文记忆。以下是规则：
            
            ### 核心原则
            - 你看到的每条消息都有唯一ID，如 [msg_0001]
            - 消息分为不同类型：INTENT（意图）、ANSWER（回答）、TOOL_RESULT（工具结果）等
            - 你需要维护一个"有用历史"的集合，丢弃不再需要的信息
            
            ### 何时评估
            当系统提示 "【上下文评估】当前活跃消息数: N，请评估哪些可以丢弃" 时，
            你需要执行评估。
            
            ### 评估标准
            请保留的消息：
            1. 用户的**原始核心意图**（intent） — 这是你的任务依据
            2. 用户指定的**关键约束和规则** — 不能遗忘
            3. 当前正在进行的任务的所有**决策链条**
            4. 用户明确要求记住的信息
            
            可以丢弃的消息：
            1. 工具执行成功的结果（如 "操作成功"）— 除非其中包含关键数据
            2. 中间确认对话（"你确认吗？" "我确认"）— 结果已确定即可丢弃
            3. 已被新方案取代的旧讨论 — 保留最终决策即可
            4. 与当前任务无关的历史讨论 — 如果任务已切换
            
            ### 安全边界
            - 即使你认为可以全部丢弃，也必须至少保留最近 5 条消息
            - 不能丢弃任何标记为 "INTENT" 的消息
            - 如果某条消息被其他消息引用，请检查引用者是否也要丢弃
            
            ### 输出格式
            请严格按以下 JSON 格式输出评估结果：
            ```json
            {
              "evaluation": {
                "retain": [{"msg_id": "...", "reason": "..."}],
                "drop": [{"msg_id": "...", "reason": "...", "is_recoverable": true}],
                "summary": "简要说明整体评估结果"
              }
            }
            ```
            """;
    }
    
    private String buildEvaluationPrompt(StructuredContextManager contextManager, EvaluationTrigger trigger) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("【上下文评估】当前活跃消息数: ").append(contextManager.getActiveCount());
        sb.append("，触发原因: ").append(trigger).append("\n\n");
        
        sb.append("请评估哪些消息可以丢弃。输出 JSON 格式的评估结果。\n\n");
        
        // 添加当前任务信息
        TaskContext currentTask = contextManager.getCurrentTask();
        if (currentTask != null) {
            sb.append("当前任务: ").append(currentTask.getOriginalIntent()).append("\n\n");
        }
        
        sb.append("消息列表:\n");
        
        List<StructuredMessage> messages = contextManager.getActiveMessages();
        for (StructuredMessage msg : messages) {
            sb.append("- [").append(msg.getId()).append("] ");
            sb.append(msg.getRole()).append(": ");
            sb.append(msg.getType()).append(" | ");
            sb.append("refCount=").append(msg.getRefCount()).append(" | ");
            
            String content = msg.getContent();
            if (content != null) {
                sb.append(content.substring(0, Math.min(50, content.length())));
                if (content.length() > 50) {
                    sb.append("...");
                }
            }
            sb.append("\n");
        }
        
        sb.append("\n请输出 JSON 格式的评估结果:");
        
        return sb.toString();
    }
    
    // ==================== 模拟 LLM 响应 ====================
    
    private EvaluationResult simulateLLMResponse(StructuredContextManager contextManager, EvaluationTrigger trigger) {
        List<StructuredMessage> messages = contextManager.getActiveMessages();
        List<EvaluationResult.RetainRecommendation> retain = new ArrayList<>();
        List<EvaluationResult.DropRecommendation> drop = new ArrayList<>();
        
        // 简单策略：保留意图、决策、高引用计数、最近N条
        for (StructuredMessage msg : messages) {
            boolean shouldRetain = false;
            String reason = "";
            
            MessageType type = msg.getType();
            
            // 保留 INTENT
            if (type == MessageType.INTENT) {
                shouldRetain = true;
                reason = "用户核心意图";
            }
            // 保留 DECISION
            else if (type == MessageType.DECISION) {
                shouldRetain = true;
                reason = "最终决策";
            }
            // 保留被引用的消息
            else if (msg.getRefCount() > 0) {
                shouldRetain = true;
                reason = "被后续消息引用";
            }
            // 保留最近的消息（最后3条）
            else if (messages.indexOf(msg) >= messages.size() - 3) {
                shouldRetain = true;
                reason = "最近消息";
            }
            // 丢弃工具结果（成功的）
            else if (type == MessageType.TOOL_RESULT && !msg.getContent().contains("失败")) {
                shouldRetain = false;
                reason = "工具执行成功结果";
            }
            else {
                // 随机丢弃一些中间消息
                shouldRetain = false;
                reason = "中间过程消息";
            }
            
            if (shouldRetain) {
                retain.add(new EvaluationResult.RetainRecommendation(msg.getId(), reason));
            } else {
                drop.add(new EvaluationResult.DropRecommendation(msg.getId(), reason, true));
            }
        }
        
        String summary = "LLM 评估: 保留 " + retain.size() + " 条，丢弃 " + drop.size() + " 条";
        
        return new EvaluationResult(
            contextManager.getSessionId(),
            trigger,
            messages.size(),
            retain.size(),
            retain,
            drop,
            summary
        );
    }
    
    // ==================== Getters/Setters ====================
    
    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
}