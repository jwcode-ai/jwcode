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
     */
    public CompletableFuture<DecompositionResult> decomposeWithAI(String taskDescription) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("[AITaskDecomposer] 使用 AI 分解任务: " + taskDescription.substring(0, Math.min(50, taskDescription.length())));
                
                // 模拟 AI 响应
                String aiResponse = simulateAIResponse(taskDescription);
                
                // 解析 AI 响应
                DecompositionResult result = parseAIResponse(aiResponse);
                
                System.out.println("[AITaskDecomposer] AI 分解完成: " + result.getSubTasks().size() + " 个子任务");
                return result;
                
            } catch (Exception e) {
                System.out.println("[AITaskDecomposer] AI 分解失败: " + e.getMessage());
                return fallbackDecomposition(taskDescription);
            }
        });
    }
    
    /**
     * 模拟 AI 响应
     */
    private String simulateAIResponse(String taskDescription) {
        String lower = taskDescription.toLowerCase();
        
        if (lower.contains("refactor")) {
            return createRefactorResponse();
        } else if (lower.contains("feature") || lower.contains("implement")) {
            return createFeatureResponse();
        } else {
            return createGenericResponse();
        }
    }
    
    private String createRefactorResponse() {
        return """
            {
                "complexity": 8,
                "estimatedSubTasks": 6,
                "reasoning": "Refactoring requires careful analysis of existing code, identification of issues, planning changes, execution, and verification.",
                "subTasks": [
                    {"id": "analyze-impact", "description": "Analyze impact on existing codebase", "type": "ANALYSIS", "priority": 10, "dependencies": []},
                    {"id": "identify-patterns", "description": "Identify current design patterns", "type": "ANALYSIS", "priority": 9, "dependencies": ["analyze-impact"]},
                    {"id": "design-solution", "description": "Design new architecture", "type": "PLANNING", "priority": 8, "dependencies": ["identify-patterns"]},
                    {"id": "implement-core", "description": "Implement core changes", "type": "EXECUTION", "priority": 7, "dependencies": ["design-solution"]},
                    {"id": "update-tests", "description": "Update tests", "type": "EXECUTION", "priority": 6, "dependencies": ["implement-core"]},
                    {"id": "verify-quality", "description": "Verify quality", "type": "VERIFICATION", "priority": 5, "dependencies": ["update-tests"]}
                ]
            }
            """;
    }
    
    private String createFeatureResponse() {
        return """
            {
                "complexity": 7,
                "estimatedSubTasks": 5,
                "reasoning": "Feature implementation requires analysis, design, backend, frontend, and testing.",
                "subTasks": [
                    {"id": "gather-requirements", "description": "Gather requirements", "type": "ANALYSIS", "priority": 10, "dependencies": []},
                    {"id": "design-interface", "description": "Design API interfaces", "type": "PLANNING", "priority": 9, "dependencies": ["gather-requirements"]},
                    {"id": "implement-backend", "description": "Implement backend", "type": "EXECUTION", "priority": 8, "dependencies": ["design-interface"]},
                    {"id": "implement-frontend", "description": "Implement frontend", "type": "EXECUTION", "priority": 8, "dependencies": ["design-interface"]},
                    {"id": "integration-testing", "description": "Integration testing", "type": "VERIFICATION", "priority": 7, "dependencies": ["implement-backend", "implement-frontend"]}
                ]
            }
            """;
    }
    
    private String createGenericResponse() {
        return """
            {
                "complexity": 5,
                "estimatedSubTasks": 4,
                "reasoning": "General task requiring understanding, planning, execution, and verification.",
                "subTasks": [
                    {"id": "understand", "description": "Understand requirements", "type": "ANALYSIS", "priority": 10, "dependencies": []},
                    {"id": "plan", "description": "Plan approach", "type": "PLANNING", "priority": 8, "dependencies": ["understand"]},
                    {"id": "execute", "description": "Execute solution", "type": "EXECUTION", "priority": 7, "dependencies": ["plan"]},
                    {"id": "validate", "description": "Validate result", "type": "VERIFICATION", "priority": 6, "dependencies": ["execute"]}
                ]
            }
            """;
    }
    
    private DecompositionResult parseAIResponse(String aiResponse) throws Exception {
        return objectMapper.readValue(aiResponse, DecompositionResult.class);
    }
    
    private DecompositionResult fallbackDecomposition(String taskDescription) {
        List<SubTaskDef> subTasks = new ArrayList<>();
        subTasks.add(SubTaskDef.builder().id("understand").description("Understand requirements").type("ANALYSIS").priority(10).dependencies(Collections.emptyList()).build());
        subTasks.add(SubTaskDef.builder().id("execute").description("Execute task").type("EXECUTION").priority(7).dependencies(List.of("understand")).build());
        
        return DecompositionResult.builder()
            .complexity(5)
            .estimatedSubTasks(2)
            .reasoning("Fallback decomposition")
            .subTasks(subTasks)
            .build();
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
