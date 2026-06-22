package com.jwcode.core.advanced.swarm.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * AI Task Decomposer - AI 驱动的任务分解器
 * 
 * 使用大模型智能分析任务并分解为子任务
 * 替代基于规则的简单匹配
 */
public class AITaskDecomposer {

    private static final Logger logger = Logger.getLogger(AITaskDecomposer.class.getName());

    private final ObjectMapper objectMapper;
    private final LLMService llmService;

    /** 无 LLM 构造器（向后兼容，分解时会失败） */
    public AITaskDecomposer() {
        this.objectMapper = new ObjectMapper();
        this.llmService = null;
    }

    /** 注入 LLM 服务的构造器 */
    public AITaskDecomposer(LLMService llmService) {
        this.objectMapper = new ObjectMapper();
        this.llmService = llmService;
    }
    
    /**
     * 使用 AI 智能分解任务
     * 
     * 修复记录：2026/4/28 - 移除模拟 AI 响应和 fallback 降级
     * 如果 AI 不可用，直接抛出异常而非返回假数据
     */
    public CompletableFuture<DecompositionResult> decomposeWithAI(String taskDescription) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("AI decomposing task: " + taskDescription.substring(0, Math.min(50, taskDescription.length())));
                
                // 【修复】移除模拟 AI 响应 - 必须调用真实 AI
                String aiResponse = executeRealAI(taskDescription);
                
                // 解析 AI 响应
                DecompositionResult result = parseAIResponse(aiResponse);
                
                logger.info("AI decomposition complete: " + result.getSubTasks().size() + " subtasks");
                return result;
                
            } catch (Exception e) {
                // 【修复】不再降级，直接抛出异常
                logger.warning("AI decomposition failed: " + e.getMessage());
                throw new IllegalStateException(
                    "AI Task Decomposition failed for: " + taskDescription + 
                    ". Cause: " + e.getMessage() + 
                    ". 请确保 LLM Service 已正确配置，或检查网络连接。"
                );
            }
        });
    }
    
    /** 任务分解系统提示词 */
    private static final String DECOMPOSE_SYSTEM_PROMPT = """
        你是一个任务分解专家。将用户的开发任务拆分为可并行或串行执行的子任务。

        输出格式（严格 JSON）：
        {
          "complexity": 1-10 的整数,
          "reasoning": "简短的分解理由",
          "estimatedSubTasks": 子任务数量,
          "subTasks": [
            {
              "id": "subtask-1",
              "description": "子任务描述",
              "type": "explore|code|test|review|doc|debug",
              "priority": 1-10,
              "dependencies": []
            }
          ]
        }

        规则：
        - 每个子任务必须有明确的验收标准
        - type 必须从给定的枚举值中选择
        - dependencies 列出必须在前面的子任务 id
        - 简单任务（如修改一个变量名）不要拆分，返回1个子任务
        """;

    /**
     * 执行真实的 AI 调用 — 通过 LLMService 分解任务。
     */
    private String executeRealAI(String taskDescription) {
        if (llmService == null) {
            throw new UnsupportedOperationException(
                "AITaskDecomposer requires LLM Service. " +
                "Please configure LLM provider before using AI-based task decomposition."
            );
        }

        try {
            List<LLMMessage> messages = List.of(
                LLMMessage.system(DECOMPOSE_SYSTEM_PROMPT),
                LLMMessage.user("请分解以下开发任务：\n" + taskDescription)
            );

            LLMResponse response = llmService.chat(messages)
                .get(30, java.util.concurrent.TimeUnit.SECONDS);

            String content = response.getContent();
            logger.fine("AITaskDecomposer AI response (first 200 chars): "
                + (content.length() > 200 ? content.substring(0, 200) + "..." : content));

            // 提取 JSON（AI 可能用 markdown 包裹）
            return extractJson(content);

        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("AITaskDecomposer: LLM 响应超时（30s）");
        } catch (Exception e) {
            throw new RuntimeException("AITaskDecomposer: LLM 调用失败 — " + e.getMessage(), e);
        }
    }

    /** 从 AI 回复中提取 JSON（处理 markdown 代码块包裹） */
    private String extractJson(String content) {
        String trimmed = content.trim();
        // 尝试提取 ```json ... ``` 代码块
        int start = trimmed.indexOf("```json");
        if (start >= 0) {
            int jsonStart = trimmed.indexOf('\n', start) + 1;
            int end = trimmed.indexOf("```", jsonStart);
            if (end > jsonStart) {
                return trimmed.substring(jsonStart, end).trim();
            }
        }
        // 尝试提取 ``` ... ``` 代码块
        start = trimmed.indexOf("```");
        if (start >= 0) {
            int jsonStart = trimmed.indexOf('\n', start) + 1;
            int end = trimmed.indexOf("```", jsonStart);
            if (end > jsonStart) {
                return trimmed.substring(jsonStart, end).trim();
            }
        }
        // 尝试提取 { ... } 第一个 JSON 对象
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return trimmed;
    }
    
    private DecompositionResult parseAIResponse(String aiResponse) throws Exception {
        return objectMapper.readValue(aiResponse, DecompositionResult.class);
    }
    
    // ==================== 数据类 ====================
    
    public static class DecompositionResult {
        private int complexity;
        private int estimatedSubTasks;
        private String reasoning;
        private List<SubTaskDef> subTasks;
        
        public DecompositionResult() {}
        
        public int getComplexity() { return complexity; }
        public void setComplexity(int v) { this.complexity = v; }
        public int getEstimatedSubTasks() { return estimatedSubTasks; }
        public void setEstimatedSubTasks(int v) { this.estimatedSubTasks = v; }
        public String getReasoning() { return reasoning; }
        public void setReasoning(String v) { this.reasoning = v; }
        public List<SubTaskDef> getSubTasks() { return subTasks; }
        public void setSubTasks(List<SubTaskDef> v) { this.subTasks = v; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private final DecompositionResult result = new DecompositionResult();
            
            public Builder complexity(int v) { result.complexity = v; return this; }
            public Builder estimatedSubTasks(int v) { result.estimatedSubTasks = v; return this; }
            public Builder reasoning(String v) { result.reasoning = v; return this; }
            public Builder subTasks(List<SubTaskDef> v) { result.subTasks = v; return this; }
            public DecompositionResult build() { return result; }
        }
    }
    
    public static class SubTaskDef {
        private String id;
        private String description;
        private String type;
        private int priority;
        private List<String> dependencies;
        
        public SubTaskDef() {}
        
        public String getId() { return id; }
        public void setId(String v) { this.id = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public int getPriority() { return priority; }
        public void setPriority(int v) { this.priority = v; }
        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> v) { this.dependencies = v; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private final SubTaskDef task = new SubTaskDef();
            
            public Builder id(String v) { task.id = v; return this; }
            public Builder description(String v) { task.description = v; return this; }
            public Builder type(String v) { task.type = v; return this; }
            public Builder priority(int v) { task.priority = v; return this; }
            public Builder dependencies(List<String> v) { task.dependencies = v; return this; }
            public SubTaskDef build() { return task; }
        }
    }
}
