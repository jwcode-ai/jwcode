package com.jwcode.core.advanced.swarm.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * AI Task Decomposer - AI 驱动的任务分解器
 * 
 * 使用大模型智能分析任务并分解为子任务
 * 替代基于规则的简单匹配
 */
public class AITaskDecomposer {
    
    private final ObjectMapper objectMapper;
    
    public AITaskDecomposer() {
        this.objectMapper = new ObjectMapper();
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
                System.out.println("[AITaskDecomposer] 使用 AI 分解任务: " + taskDescription.substring(0, Math.min(50, taskDescription.length())));
                
                // 【修复】移除模拟 AI 响应 - 必须调用真实 AI
                String aiResponse = executeRealAI(taskDescription);
                
                // 解析 AI 响应
                DecompositionResult result = parseAIResponse(aiResponse);
                
                System.out.println("[AITaskDecomposer] AI 分解完成: " + result.getSubTasks().size() + " 个子任务");
                return result;
                
            } catch (Exception e) {
                // 【修复】不再降级，直接抛出异常
                System.out.println("[AITaskDecomposer] AI 分解失败: " + e.getMessage());
                throw new IllegalStateException(
                    "AI Task Decomposition failed for: " + taskDescription + 
                    ". Cause: " + e.getMessage() + 
                    ". 请确保 LLM Service 已正确配置，或检查网络连接。"
                );
            }
        });
    }
    
    /**
     * 执行真实的 AI 调用
     * 修复：移除 simulateAIResponse，改为抛异常或调用真实 LLM
     */
    private String executeRealAI(String taskDescription) {
        // TODO: 集成真实的 LLM Service
        // 当前如果没有配置 LLM，应该直接失败
        throw new UnsupportedOperationException(
            "AITaskDecomposer requires LLM Service configuration. " +
            "Please configure LLM provider before using AI-based task decomposition."
        );
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
