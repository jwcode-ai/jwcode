package com.jwcode.core.planner.ai;

import com.jwcode.core.a2a.model.StepStatus;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.planner.AdaptiveExecutionMonitor;
import com.jwcode.core.planner.ExecutionPlan;
import com.jwcode.core.planner.PlanStep;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * DynamicExecutionEngine - 动态执行引擎
 * 
 * 核心能力：
 * 1. 执行中监控 - 实时监控每个子任务的执行状态
 * 2. 动态调整 - 根据执行结果调整后续任务
 * 3. 重规划 - 失败时自动重规划
 * 4. 暂停/恢复/取消 - 完整的任务生命周期管理
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class DynamicExecutionEngine {
    
    private static final Logger log = LoggerFactory.getLogger(DynamicExecutionEngine.class);
    
    private final ExecutorService executor;
    private final ToolRegistry toolRegistry;
    private final ReplanningStrategy replanningStrategy;
    private final ExecutionTracer executionTracer;
    private final AILearningMemory learningMemory;
    
    // 执行状态
    private final Map<String, ExecutionContext> activeExecutions;
    
    public DynamicExecutionEngine(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.replanningStrategy = new ReplanningStrategy();
        this.executionTracer = new ExecutionTracer();
        this.learningMemory = new AILearningMemory();
        this.activeExecutions = new ConcurrentHashMap<>();
        
        // 创建线程池
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("DynamicExecution-" + t.getId());
            return t;
        });
    }
    
    /**
     * 执行计划 - 主入口
     */
    public CompletableFuture<ExecutionResult> execute(ExecutionPlan plan, Agent parentAgent, Session parentSession) {
        String executionId = "exec_" + System.currentTimeMillis();
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            log.info("[DynamicExecutionEngine] 开始执行计划: " + executionId);
            
            // 创建执行上下文
            ExecutionContext context = new ExecutionContext();
            context.executionId = executionId;
            context.plan = plan;
            context.parentAgent = parentAgent;
            context.parentSession = parentSession;
            context.monitor = new AdaptiveExecutionMonitor(plan);
            context.status = ExecutionStatus.RUNNING;
            context.paused = new AtomicBoolean(false);
            context.cancelled = new AtomicBoolean(false);
            
            activeExecutions.put(executionId, context);
            executionTracer.startExecution(executionId, plan);
            
            try {
                // 1. 获取可并行化的步骤组
                List<List<PlanStep>> stepGroups = plan.getParallelizableGroups();
                log.info("[DynamicExecutionEngine] 执行分组: " + stepGroups.size() + " 组");
                
                // 2. 按组执行
                List<StepResult> allResults = new ArrayList<>();
                
                for (int i = 0; i < stepGroups.size(); i++) {
                    List<PlanStep> group = stepGroups.get(i);
                    log.info("[DynamicExecutionEngine] 执行第 " + (i + 1) + " 组，共 " + group.size() + " 个步骤");
                    
                    // 检查暂停
                    if (context.isPaused()) {
                        waitForResume(context);
                    }
                    
                    // 检查取消
                    if (context.isCancelled()) {
                        log.info("[DynamicExecutionEngine] 执行已取消: " + executionId);
                        break;
                    }
                    
                    // 并行执行组内步骤
                    List<StepResult> groupResults = executeStepGroup(group, context);
                    allResults.addAll(groupResults);
                    
                    // 检查失败
                    List<StepResult> failures = groupResults.stream()
                        .filter(r -> !r.isSuccess())
                        .collect(Collectors.toList());
                    
                    if (!failures.isEmpty()) {
                        log.info("[DynamicExecutionEngine] 第 " + (i + 1) + " 组有 " + failures.size() + " 个失败");
                        
                        // 重规划
                        ReplanningResult replanResult = handleFailures(context, failures, allResults);
                        
                        if (replanResult != null && replanResult.isAbort()) {
                            log.info("[DynamicExecutionEngine] 重规划决定终止执行");
                            break;
                        }
                        
                        if (replanResult != null && replanResult.isContinueWithNewPlan()) {
                            // 使用新计划继续
                            stepGroups = replanResult.getNewStepGroups();
                            log.info("[DynamicExecutionEngine] 使用新计划继续执行");
                        }
                    }
                }
                
                // 3. 构建结果
                long duration = System.currentTimeMillis() - startTime;
                ExecutionResult result = buildExecutionResult(context, duration);
                
                // 4. 记录学习数据
                recordLearningData(plan, result);
                
                // 5. 清理
                activeExecutions.remove(executionId);
                
                log.info("[DynamicExecutionEngine] 执行完成: " + executionId + ", 耗时: " + duration + "ms");
                
                return result;
                
            } catch (Exception e) {
                log.error("[DynamicExecutionEngine] 执行异常: " + executionId, e);
                return ExecutionResult.builder()
                    .executionId(executionId)
                    .planId(plan.getPlanId())
                    .success(false)
                    .completedSteps(0)
                    .failedSteps(0)
                    .totalSteps(plan.getSteps().size())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
            }
        }, executor);
    }
    
    /**
     * 执行步骤组
     */
    private List<StepResult> executeStepGroup(List<PlanStep> steps, ExecutionContext context) {
        // 依赖外部 Agent 执行，这里简化为模拟执行
        return steps.stream()
            .map(step -> executeStep(step, context))
            .collect(Collectors.toList());
    }
    
    /**
     * 执行单个步骤
     */
    private StepResult executeStep(PlanStep step, ExecutionContext context) {
        long start = System.currentTimeMillis();
        
        try {
            // 模拟步骤执行
            log.info("[DynamicExecutionEngine] 执行步骤: " + step.getStepNumber() + " - " + step.getAction());
            
            // 这里应该调用实际的 Agent 执行逻辑
            // 简化处理
            Thread.sleep(100);
            
            boolean success = true;
            String output = "步骤 " + step.getStepNumber() + " 执行完成";
            
            return StepResult.builder()
                .stepNumber(step.getStepNumber())
                .success(success)
                .output(output)
                .error(null)
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
                
        } catch (Exception e) {
            return StepResult.builder()
                .stepNumber(step.getStepNumber())
                .success(false)
                .output(null)
                .error(e.getMessage())
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
        }
    }
    
    /**
     * 处理失败
     */
    private ReplanningResult handleFailures(ExecutionContext context, List<StepResult> failures, List<StepResult> lastResults) {
        log.info("[DynamicExecutionEngine] 处理 " + failures.size() + " 个失败步骤");
        
        // 转换 StepResult 为错误字符串
        List<String> failureMessages = failures.stream()
            .map(f -> f.getError() != null ? f.getError() : "Step " + f.getStepNumber() + " failed")
            .collect(Collectors.toList());
        
        // 选择重规划策略
        ReplanningStrategy.StrategyType strategy = replanningStrategy.selectStrategy(
            context.getPlan(), failureMessages, context.getMonitor().generateReport()
        );
        
        log.info("[DynamicExecutionEngine] 重规划策略: " + strategy);
        
        switch (strategy) {
            case SUBDIVIDE:
                return subdivideFailedSteps(context, lastResults);
            case RETRY:
                return retryFailedSteps(context, lastResults);
            case ADJUST_ORDER:
                return adjustExecutionOrder(context);
            case CHANGE_AGENT:
                return changeAgentTypes(context, lastResults);
            case ABORT:
            default:
                return ReplanningResult.builder().abort(true).build();
        }
    }
    
    /**
     * 细化分解失败的任务
     */
    private ReplanningResult subdivideFailedSteps(ExecutionContext context, List<StepResult> lastResults) {
        List<PlanStep> currentSteps = context.getPlan().getSteps();
        List<PlanStep> newSteps = new ArrayList<>();
        
        for (int i = 0; i < currentSteps.size(); i++) {
            PlanStep step = currentSteps.get(i);
            StepResult result = lastResults.stream()
                .filter(r -> r.getStepNumber() == step.getStepNumber())
                .findFirst()
                .orElse(null);
            
            if (result != null && !result.isSuccess()) {
                newSteps.addAll(subdivideStep(step));
            } else {
                newSteps.add(step);
            }
        }
        
        context.getPlan().setSteps(newSteps);
        
        return ReplanningResult.builder()
            .continueWithNewPlan(true)
            .newStepGroups(context.getPlan().getParallelizableGroups())
            .build();
    }
    
    /**
     * 重试失败的任务
     */
    private ReplanningResult retryFailedSteps(ExecutionContext context, List<StepResult> lastResults) {
        for (StepResult result : lastResults) {
            if (!result.isSuccess()) {
                context.getPlan().getSteps().stream()
                    .filter(s -> s.getStepNumber() == result.getStepNumber())
                    .forEach(s -> s.setStatus(StepStatus.PENDING));
            }
        }
        
        return ReplanningResult.builder()
            .continueWithNewPlan(true)
            .newStepGroups(context.getPlan().getParallelizableGroups())
            .build();
    }
    
    /**
     * 调整执行顺序
     */
    private ReplanningResult adjustExecutionOrder(ExecutionContext context) {
        List<PlanStep> steps = context.getPlan().getSteps();
        steps.sort(Comparator.comparingInt(PlanStep::getPriority).reversed());
        
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setStepNumber(i + 1);
        }
        
        return ReplanningResult.builder()
            .continueWithNewPlan(true)
            .newStepGroups(context.getPlan().getParallelizableGroups())
            .build();
    }
    
    /**
     * 更换 Agent 类型
     */
    private ReplanningResult changeAgentTypes(ExecutionContext context, List<StepResult> lastResults) {
        // 简单处理：继续执行
        return ReplanningResult.builder()
            .continueWithNewPlan(true)
            .newStepGroups(context.getPlan().getParallelizableGroups())
            .build();
    }
    
    /**
     * 细分步骤
     */
    private List<PlanStep> subdivideStep(PlanStep step) {
        List<PlanStep> subSteps = new ArrayList<>();
        
        subSteps.add(PlanStep.builder()
            .stepNumber(step.getStepNumber())
            .action(step.getAction() + " - 分析")
            .description("分析阶段: " + step.getDescription())
            .agentType("analyzer")
            .priority(step.getPriority())
            .dependencies(List.of())
            .estimatedTimeMs(step.getEstimatedTimeMs() / 3)
            .build());
        
        subSteps.add(PlanStep.builder()
            .stepNumber(step.getStepNumber() + 1)
            .action(step.getAction() + " - 执行")
            .description("执行阶段: " + step.getDescription())
            .agentType(step.getAgentType())
            .priority(step.getPriority())
            .dependencies(List.of("task-" + step.getStepNumber()))
            .estimatedTimeMs(step.getEstimatedTimeMs() / 3)
            .build());
        
        subSteps.add(PlanStep.builder()
            .stepNumber(step.getStepNumber() + 2)
            .action(step.getAction() + " - 验证")
            .description("验证阶段: " + step.getDescription())
            .agentType("test")
            .priority(step.getPriority())
            .dependencies(List.of("task-" + (step.getStepNumber() + 1)))
            .estimatedTimeMs(step.getEstimatedTimeMs() / 3)
            .build());
        
        return subSteps;
    }
    
    /**
     * 等待恢复
     */
    private void waitForResume(ExecutionContext context) throws InterruptedException {
        log.info("[DynamicExecutionEngine] 执行暂停，等待恢复...");
        synchronized (context) {
            while (context.isPaused()) {
                context.wait(1000);
            }
        }
        log.info("[DynamicExecutionEngine] 执行恢复");
    }
    
    /**
     * 暂停执行
     */
    public void pause(String executionId) {
        ExecutionContext context = activeExecutions.get(executionId);
        if (context != null) {
            context.setPaused(true);
            context.setStatus(ExecutionStatus.PAUSED);
            log.info("[DynamicExecutionEngine] 执行已暂停: " + executionId);
        }
    }
    
    /**
     * 恢复执行
     */
    public void resume(String executionId) {
        ExecutionContext context = activeExecutions.get(executionId);
        if (context != null) {
            synchronized (context) {
                context.setPaused(false);
                context.setStatus(ExecutionStatus.RUNNING);
                context.notifyAll();
            }
            log.info("[DynamicExecutionEngine] 执行已恢复: " + executionId);
        }
    }
    
    /**
     * 取消执行
     */
    public void cancel(String executionId) {
        ExecutionContext context = activeExecutions.get(executionId);
        if (context != null) {
            context.setCancelled(true);
            context.setStatus(ExecutionStatus.CANCELLED);
            log.info("[DynamicExecutionEngine] 执行已取消: " + executionId);
        }
    }
    
    /**
     * 获取执行状态
     */
    public ExecutionStatus getStatus(String executionId) {
        ExecutionContext context = activeExecutions.get(executionId);
        return context != null ? context.getStatus() : null;
    }
    
    /**
     * 获取执行报告
     */
    public ExecutionReport getReport(String executionId) {
        ExecutionContext context = activeExecutions.get(executionId);
        if (context != null) {
            return ExecutionReport.builder()
                .executionId(executionId)
                .status(context.getStatus())
                .monitorReport(context.getMonitor().generateReport())
                .build();
        }
        return null;
    }
    
    /**
     * 构建执行结果
     */
    private ExecutionResult buildExecutionResult(ExecutionContext context, long duration) {
        AdaptiveExecutionMonitor.ExecutionReport monitorReport = context.getMonitor().generateReport();
        
        return ExecutionResult.builder()
            .executionId(context.getExecutionId())
            .planId(context.getPlan().getPlanId())
            .success(monitorReport.getFailedSteps() == 0)
            .completedSteps(monitorReport.getCompletedSteps())
            .failedSteps(monitorReport.getFailedSteps())
            .totalSteps(monitorReport.getTotalSteps())
            .durationMs(duration)
            .monitorReport(monitorReport)
            .tracerReport(executionTracer.getReport(context.getExecutionId()))
            .build();
    }
    
    /**
     * 记录学习数据
     */
    private void recordLearningData(ExecutionPlan plan, ExecutionResult result) {
        try {
            learningMemory.recordExecution(
                plan.getOriginalRequest(),
                null,
                result.getDurationMs(),
                result.isSuccess(),
                result.getCompletedSteps()
            );
        } catch (Exception e) {
            log.warn("[DynamicExecutionEngine] 记录学习数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 关闭引擎
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // ==================== 数据类 ====================
    
    private static class ExecutionContext {
        String executionId;
        ExecutionPlan plan;
        Agent parentAgent;
        Session parentSession;
        AdaptiveExecutionMonitor monitor;
        ExecutionStatus status;
        AtomicBoolean paused = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        
        public String getExecutionId() { return executionId; }
        public ExecutionPlan getPlan() { return plan; }
        public Agent getParentAgent() { return parentAgent; }
        public Session getParentSession() { return parentSession; }
        public AdaptiveExecutionMonitor getMonitor() { return monitor; }
        public ExecutionStatus getStatus() { return status; }
        public void setStatus(ExecutionStatus status) { this.status = status; }
        public boolean isPaused() { return paused.get(); }
        public void setPaused(boolean paused) { this.paused.set(paused); }
        public boolean isCancelled() { return cancelled.get(); }
        public void setCancelled(boolean cancelled) { this.cancelled.set(cancelled); }
    }
    
    public static class StepResult {
        private int stepNumber;
        private boolean success;
        private String output;
        private String error;
        private long executionTimeMs;
        
        public int getStepNumber() { return stepNumber; }
        public boolean isSuccess() { return success; }
        public String getOutput() { return output; }
        public String getError() { return error; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private int stepNumber;
            private boolean success;
            private String output;
            private String error;
            private long executionTimeMs;
            
            public Builder stepNumber(int stepNumber) { this.stepNumber = stepNumber; return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder output(String output) { this.output = output; return this; }
            public Builder error(String error) { this.error = error; return this; }
            public Builder executionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
            public StepResult build() {
                StepResult r = new StepResult();
                r.stepNumber = stepNumber;
                r.success = success;
                r.output = output;
                r.error = error;
                r.executionTimeMs = executionTimeMs;
                return r;
            }
        }
    }
    
    private static class ReplanningResult {
        private boolean continueWithNewPlan;
        private boolean abort;
        private List<List<PlanStep>> newStepGroups;
        
        public boolean isContinueWithNewPlan() { return continueWithNewPlan; }
        public boolean isAbort() { return abort; }
        public List<List<PlanStep>> getNewStepGroups() { return newStepGroups; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private boolean continueWithNewPlan;
            private boolean abort;
            private List<List<PlanStep>> newStepGroups;
            
            public Builder continueWithNewPlan(boolean v) { this.continueWithNewPlan = v; return this; }
            public Builder abort(boolean v) { this.abort = v; return this; }
            public Builder newStepGroups(List<List<PlanStep>> v) { this.newStepGroups = v; return this; }
            public ReplanningResult build() {
                ReplanningResult r = new ReplanningResult();
                r.continueWithNewPlan = continueWithNewPlan;
                r.abort = abort;
                r.newStepGroups = newStepGroups;
                return r;
            }
        }
    }
    
    public static class ExecutionResult {
        private String executionId;
        private String planId;
        private boolean success;
        private int completedSteps;
        private int failedSteps;
        private int totalSteps;
        private long durationMs;
        private AdaptiveExecutionMonitor.ExecutionReport monitorReport;
        private ExecutionTracer.TracerReport tracerReport;
        
        public String getExecutionId() { return executionId; }
        public String getPlanId() { return planId; }
        public boolean isSuccess() { return success; }
        public int getCompletedSteps() { return completedSteps; }
        public int getFailedSteps() { return failedSteps; }
        public int getTotalSteps() { return totalSteps; }
        public long getDurationMs() { return durationMs; }
        public AdaptiveExecutionMonitor.ExecutionReport getMonitorReport() { return monitorReport; }
        public ExecutionTracer.TracerReport getTracerReport() { return tracerReport; }
        
        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("执行结果报告\n");
            sb.append("执行ID: ").append(executionId).append("\n");
            sb.append("状态: ").append(success ? "成功" : "失败").append("\n");
            sb.append("进度: ").append(completedSteps).append("/").append(totalSteps).append("\n");
            sb.append("失败: ").append(failedSteps).append("\n");
            sb.append("耗时: ").append(durationMs).append("ms\n");
            return sb.toString();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private String executionId;
            private String planId;
            private boolean success;
            private int completedSteps;
            private int failedSteps;
            private int totalSteps;
            private long durationMs;
            private AdaptiveExecutionMonitor.ExecutionReport monitorReport;
            private ExecutionTracer.TracerReport tracerReport;
            
            public Builder executionId(String v) { this.executionId = v; return this; }
            public Builder planId(String v) { this.planId = v; return this; }
            public Builder success(boolean v) { this.success = v; return this; }
            public Builder completedSteps(int v) { this.completedSteps = v; return this; }
            public Builder failedSteps(int v) { this.failedSteps = v; return this; }
            public Builder totalSteps(int v) { this.totalSteps = v; return this; }
            public Builder durationMs(long v) { this.durationMs = v; return this; }
            public Builder monitorReport(AdaptiveExecutionMonitor.ExecutionReport v) { this.monitorReport = v; return this; }
            public Builder tracerReport(ExecutionTracer.TracerReport v) { this.tracerReport = v; return this; }
            public ExecutionResult build() {
                ExecutionResult r = new ExecutionResult();
                r.executionId = executionId;
                r.planId = planId;
                r.success = success;
                r.completedSteps = completedSteps;
                r.failedSteps = failedSteps;
                r.totalSteps = totalSteps;
                r.durationMs = durationMs;
                r.monitorReport = monitorReport;
                r.tracerReport = tracerReport;
                return r;
            }
        }
    }
    
    public static class ExecutionReport {
        private String executionId;
        private ExecutionStatus status;
        private AdaptiveExecutionMonitor.ExecutionReport monitorReport;
        
        public String getExecutionId() { return executionId; }
        public ExecutionStatus getStatus() { return status; }
        public AdaptiveExecutionMonitor.ExecutionReport getMonitorReport() { return monitorReport; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private String executionId;
            private ExecutionStatus status;
            private AdaptiveExecutionMonitor.ExecutionReport monitorReport;
            
            public Builder executionId(String v) { this.executionId = v; return this; }
            public Builder status(ExecutionStatus v) { this.status = v; return this; }
            public Builder monitorReport(AdaptiveExecutionMonitor.ExecutionReport v) { this.monitorReport = v; return this; }
            public ExecutionReport build() {
                ExecutionReport r = new ExecutionReport();
                r.executionId = executionId;
                r.status = status;
                r.monitorReport = monitorReport;
                return r;
            }
        }
    }
    
    public enum ExecutionStatus {
        PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    }
    
    private static class ExecutionException extends RuntimeException {
        ExecutionException(String message) {
            super(message);
        }
    }
}
