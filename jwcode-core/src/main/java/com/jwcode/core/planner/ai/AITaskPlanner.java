package com.jwcode.core.planner.ai;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.planner.ExecutionPlan;
import com.jwcode.core.planner.PlanStep;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AITaskPlanner - AI 驱动的统一任务规划器
 */
public class AITaskPlanner {
    
    private static final Logger log = LoggerFactory.getLogger(AITaskPlanner.class);
    
    private final AIPlanner aiPlanner;
    private final SmartDependencyAnalyzer dependencyAnalyzer;
    private final DynamicExecutionEngine executionEngine;
    private final ExecutionTracer executionTracer;
    private final AILearningMemory learningMemory;
    
    public AITaskPlanner(LLMService llmService, ToolRegistry toolRegistry) {
        this(llmService, toolRegistry, null);
    }

    public AITaskPlanner(LLMService llmService, ToolRegistry toolRegistry,
                         Object ignoredLegacyDispatcher) {
        this.aiPlanner = new AIPlanner(llmService);
        this.dependencyAnalyzer = new SmartDependencyAnalyzer();
        this.executionEngine = new DynamicExecutionEngine(toolRegistry);
        this.executionTracer = new ExecutionTracer();
        this.learningMemory = new AILearningMemory();
    }
    
    // ==================== 主要 API ====================
    
    /**
     * 完整流程：分析 -> 分解 -> 执行
     */
    public CompletableFuture<Result> planAndExecute(String taskDescription, 
                                                     Map<String, Object> context,
                                                     Agent parentAgent, 
                                                     Session parentSession) {
        long startTime = System.currentTimeMillis();
        
        return CompletableFuture.supplyAsync(() -> {
            log.info("[AITaskPlanner] 开始完整规划流程: " + taskDescription.substring(0, Math.min(50, taskDescription.length())) + "...");
            
            try {
                // 1. 分析任务
                TaskAnalysis analysis = aiPlanner.analyze(taskDescription, context).join();
                log.info("[AITaskPlanner] 分析完成: 复杂度=" + analysis.getComplexity().getOverallScore() + "/10");
                
                // 2. 分解任务
                List<PlanStep> steps = aiPlanner.decompose(taskDescription, analysis).join();
                log.info("[AITaskPlanner] 分解完成: " + steps.size() + " 个子任务");
                
                // 3. 分析依赖
                SmartDependencyAnalyzer.DependencyAnalysis depAnalysis = dependencyAnalyzer.analyze(steps);
                log.info("[AITaskPlanner] 依赖分析完成: 关键路径=" + depAnalysis.getCriticalPath().size() + " 步");
                
                // 4. 创建执行计划
                ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan_" + System.currentTimeMillis())
                    .originalRequest(taskDescription)
                    .steps(steps)
                    .estimatedSteps(steps.size())
                    .status(ExecutionPlan.PlanStatus.APPROVED)
                    .build();
                
                // 5. 执行计划
                DynamicExecutionEngine.ExecutionResult execResult = 
                    executionEngine.execute(plan, parentAgent, parentSession).join();
                
                // 6. 记录学习数据
                learningMemory.recordExecution(
                    taskDescription,
                    analysis,
                    execResult.getDurationMs(),
                    execResult.isSuccess(),
                    execResult.getCompletedSteps()
                );
                
                long totalTime = System.currentTimeMillis() - startTime;
                log.info("[AITaskPlanner] 完整流程完成，总耗时: " + totalTime + "ms");
                
                return Result.builder()
                    .success(true)
                    .analysis(analysis)
                    .plan(plan)
                    .dependencyAnalysis(depAnalysis)
                    .executionResult(execResult)
                    .totalTimeMs(totalTime)
                    .build();
                    
            } catch (Exception e) {
                log.error("[AITaskPlanner] 规划执行失败: " + e.getMessage());
                return Result.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .totalTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            }
        });
    }
    
    /**
     * 仅分析任务
     */
    public CompletableFuture<TaskAnalysis> analyze(String taskDescription) {
        return aiPlanner.analyze(taskDescription, new HashMap<>());
    }
    
    /**
     * 分析和分解任务
     */
    public CompletableFuture<PlanningResult> plan(String taskDescription, Map<String, Object> context) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[AITaskPlanner] 开始分析任务: " + taskDescription.substring(0, Math.min(50, taskDescription.length())) + "...");
            
            TaskAnalysis analysis = aiPlanner.analyze(taskDescription, context).join();
            List<PlanStep> steps = aiPlanner.decompose(taskDescription, analysis).join();
            SmartDependencyAnalyzer.DependencyAnalysis depAnalysis = dependencyAnalyzer.analyze(steps);
            
            ExecutionPlan plan = ExecutionPlan.builder()
                .planId("plan_" + System.currentTimeMillis())
                .originalRequest(taskDescription)
                .intent(convertIntent(analysis.getIntent()))
                .steps(steps)
                .estimatedSteps(steps.size())
                .status(ExecutionPlan.PlanStatus.DRAFT)
                .build();
            
            return PlanningResult.builder()
                .analysis(analysis)
                .plan(plan)
                .dependencyAnalysis(depAnalysis)
                .build();
        });
    }
    
    /**
     * 递归分解
     */
    public CompletableFuture<PlanningResult> planRecursively(String taskDescription, int complexityThreshold) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[AITaskPlanner] 开始递归分解: " + taskDescription);
            
            TaskAnalysis analysis = aiPlanner.analyze(taskDescription, new HashMap<>()).join();
            
            if (analysis.getComplexity().getOverallScore() > complexityThreshold) {
                List<PlanStep> subPlans = aiPlanner.decompose(taskDescription, analysis).join();
                
                List<PlanStep> allSteps = new ArrayList<>();
                for (PlanStep subPlan : subPlans) {
                    PlanningResult subResult = planRecursively(subPlan.getDescription(), complexityThreshold).join();
                    allSteps.addAll(subResult.getPlan().getSteps());
                }
                
                ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan_" + System.currentTimeMillis())
                    .originalRequest(taskDescription)
                    .intent(convertIntent(analysis.getIntent()))
                    .steps(allSteps)
                    .estimatedSteps(allSteps.size())
                    .build();
                
                SmartDependencyAnalyzer.DependencyAnalysis depAnalysis = dependencyAnalyzer.analyze(allSteps);
                
                return PlanningResult.builder()
                    .analysis(analysis)
                    .plan(plan)
                    .dependencyAnalysis(depAnalysis)
                    .build();
            } else {
                return plan(taskDescription, new HashMap<>()).join();
            }
        });
    }
    
    /**
     * 执行已有计划
     */
    public CompletableFuture<DynamicExecutionEngine.ExecutionResult> execute(
            ExecutionPlan plan, Agent parentAgent, Session parentSession) {
        return executionEngine.execute(plan, parentAgent, parentSession);
    }
    
    /**
     * 获取执行控制
     */
    public ExecutionControl getExecutionControl(String executionId) {
        return new ExecutionControl(executionId, executionEngine);
    }
    
    /**
     * 关闭规划器
     */
    public void shutdown() {
        executionEngine.shutdown();
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 将 TaskAnalysis.IntentAnalysis 转换为 planner.IntentAnalysis
     */
    private com.jwcode.core.planner.IntentAnalysis convertIntent(TaskAnalysis.IntentAnalysis aiIntent) {
        if (aiIntent == null) return null;
        
        com.jwcode.core.planner.IntentAnalysis.IntentType type = 
            com.jwcode.core.planner.IntentAnalysis.IntentType.GENERAL;
        
        if (aiIntent.getType() != null) {
            switch (aiIntent.getType()) {
                case CREATE: type = com.jwcode.core.planner.IntentAnalysis.IntentType.CREATE; break;
                case DEBUG: type = com.jwcode.core.planner.IntentAnalysis.IntentType.DEBUG; break;
                case REFACTOR: type = com.jwcode.core.planner.IntentAnalysis.IntentType.REFACTOR; break;
                case ANALYZE: type = com.jwcode.core.planner.IntentAnalysis.IntentType.ANALYZE; break;
                case TEST: type = com.jwcode.core.planner.IntentAnalysis.IntentType.TEST; break;
                default: type = com.jwcode.core.planner.IntentAnalysis.IntentType.GENERAL;
            }
        }
        
        Map<String, Object> entities = new HashMap<>();
        if (aiIntent.getTargetFiles() != null) {
            entities.put("targetFiles", aiIntent.getTargetFiles());
        }
        if (aiIntent.getTechnologies() != null) {
            entities.put("technologies", aiIntent.getTechnologies());
        }
        
        return com.jwcode.core.planner.IntentAnalysis.builder()
            .type(type)
            .confidence(aiIntent.getConfidence())
            .rawRequest(aiIntent.getDescription())
            .entities(entities)
            .build();
    }
    
    // ==================== 数据类 ====================
    
    public static class Result {
        private boolean success;
        private String errorMessage;
        private TaskAnalysis analysis;
        private ExecutionPlan plan;
        private SmartDependencyAnalyzer.DependencyAnalysis dependencyAnalysis;
        private DynamicExecutionEngine.ExecutionResult executionResult;
        private long totalTimeMs;
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public TaskAnalysis getAnalysis() { return analysis; }
        public ExecutionPlan getPlan() { return plan; }
        public SmartDependencyAnalyzer.DependencyAnalysis getDependencyAnalysis() { return dependencyAnalysis; }
        public DynamicExecutionEngine.ExecutionResult getExecutionResult() { return executionResult; }
        public long getTotalTimeMs() { return totalTimeMs; }
        
        // Setters
        public void setSuccess(boolean success) { this.success = success; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public void setAnalysis(TaskAnalysis analysis) { this.analysis = analysis; }
        public void setPlan(ExecutionPlan plan) { this.plan = plan; }
        public void setDependencyAnalysis(SmartDependencyAnalyzer.DependencyAnalysis dependencyAnalysis) { this.dependencyAnalysis = dependencyAnalysis; }
        public void setExecutionResult(DynamicExecutionEngine.ExecutionResult executionResult) { this.executionResult = executionResult; }
        public void setTotalTimeMs(long totalTimeMs) { this.totalTimeMs = totalTimeMs; }
        
        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("╔══════════════════════════════════════════════════════════╗\n");
            sb.append("║           🤖 AI 任务规划执行报告                          ║\n");
            sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
            
            if (success) {
                sb.append("✅ 执行成功\n\n");
                if (analysis != null) sb.append(analysis.formatReport()).append("\n");
                if (dependencyAnalysis != null) sb.append(dependencyAnalysis.formatReport()).append("\n");
                if (executionResult != null) sb.append(executionResult.formatReport()).append("\n");
                sb.append("总耗时: ").append(totalTimeMs).append("ms");
            } else {
                sb.append("❌ 执行失败\n");
                sb.append("错误: ").append(errorMessage).append("\n");
            }
            
            return sb.toString();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private boolean success;
            private String errorMessage;
            private TaskAnalysis analysis;
            private ExecutionPlan plan;
            private SmartDependencyAnalyzer.DependencyAnalysis dependencyAnalysis;
            private DynamicExecutionEngine.ExecutionResult executionResult;
            private long totalTimeMs;
            
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            public Builder analysis(TaskAnalysis analysis) { this.analysis = analysis; return this; }
            public Builder plan(ExecutionPlan plan) { this.plan = plan; return this; }
            public Builder dependencyAnalysis(SmartDependencyAnalyzer.DependencyAnalysis dependencyAnalysis) { this.dependencyAnalysis = dependencyAnalysis; return this; }
            public Builder executionResult(DynamicExecutionEngine.ExecutionResult executionResult) { this.executionResult = executionResult; return this; }
            public Builder totalTimeMs(long totalTimeMs) { this.totalTimeMs = totalTimeMs; return this; }
            
            public Result build() {
                Result result = new Result();
                result.success = this.success;
                result.errorMessage = this.errorMessage;
                result.analysis = this.analysis;
                result.plan = this.plan;
                result.dependencyAnalysis = this.dependencyAnalysis;
                result.executionResult = this.executionResult;
                result.totalTimeMs = this.totalTimeMs;
                return result;
            }
        }
    }
    
    public static class PlanningResult {
        private TaskAnalysis analysis;
        private ExecutionPlan plan;
        private SmartDependencyAnalyzer.DependencyAnalysis dependencyAnalysis;
        
        // Getters
        public TaskAnalysis getAnalysis() { return analysis; }
        public ExecutionPlan getPlan() { return plan; }
        public SmartDependencyAnalyzer.DependencyAnalysis getDependencyAnalysis() { return dependencyAnalysis; }
        
        // Setters
        public void setAnalysis(TaskAnalysis analysis) { this.analysis = analysis; }
        public void setPlan(ExecutionPlan plan) { this.plan = plan; }
        public void setDependencyAnalysis(SmartDependencyAnalyzer.DependencyAnalysis dependencyAnalysis) { this.dependencyAnalysis = dependencyAnalysis; }
        
        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            if (analysis != null) sb.append(analysis.formatReport()).append("\n");
            if (plan != null) sb.append(plan.formatPlan()).append("\n");
            if (dependencyAnalysis != null) sb.append(dependencyAnalysis.formatReport());
            return sb.toString();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private TaskAnalysis analysis;
            private ExecutionPlan plan;
            private SmartDependencyAnalyzer.DependencyAnalysis dependencyAnalysis;
            
            public Builder analysis(TaskAnalysis analysis) { this.analysis = analysis; return this; }
            public Builder plan(ExecutionPlan plan) { this.plan = plan; return this; }
            public Builder dependencyAnalysis(SmartDependencyAnalyzer.DependencyAnalysis dependencyAnalysis) { this.dependencyAnalysis = dependencyAnalysis; return this; }
            
            public PlanningResult build() {
                PlanningResult result = new PlanningResult();
                result.analysis = this.analysis;
                result.plan = this.plan;
                result.dependencyAnalysis = this.dependencyAnalysis;
                return result;
            }
        }
    }
    
    /**
     * 执行控制接口
     */
    public static class ExecutionControl {
        private final String executionId;
        private final DynamicExecutionEngine engine;
        
        ExecutionControl(String executionId, DynamicExecutionEngine engine) {
            this.executionId = executionId;
            this.engine = engine;
        }
        
        public void pause() {
            engine.pause(executionId);
        }
        
        public void resume() {
            engine.resume(executionId);
        }
        
        public void cancel() {
            engine.cancel(executionId);
        }
        
        public DynamicExecutionEngine.ExecutionStatus getStatus() {
            return engine.getStatus(executionId);
        }
        
        public DynamicExecutionEngine.ExecutionReport getReport() {
            return engine.getReport(executionId);
        }
    }
}
