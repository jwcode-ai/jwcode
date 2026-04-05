package com.jwcode.core.planner;

import com.jwcode.core.agent.parallel.SubAgentResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自适应执行监控器
 * 
 * 监控执行过程，根据实际情况动态调整策略
 * 参考 Kimi Code 的自适应能力
 */
public class AdaptiveExecutionMonitor {
    
    private final ExecutionPlan plan;
    private final Map<String, StepMetrics> stepMetrics = new ConcurrentHashMap<>();
    private final List<ExecutionEvent> eventLog = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger completedSteps = new AtomicInteger(0);
    private final AtomicInteger failedSteps = new AtomicInteger(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    
    // 自适应策略
    private volatile boolean parallelEnabled = true;
    private volatile int currentParallelism = 4;
    private volatile long estimatedRemainingTime = 0;
    
    public AdaptiveExecutionMonitor(ExecutionPlan plan) {
        this.plan = plan;
    }
    
    /**
     * 记录步骤开始
     */
    public void recordStepStart(String stepId) {
        StepMetrics metrics = stepMetrics.computeIfAbsent(stepId, k -> new StepMetrics());
        metrics.startTime = System.currentTimeMillis();
        metrics.status = StepStatus.RUNNING;
        
        eventLog.add(ExecutionEvent.builder()
            .timestamp(System.currentTimeMillis())
            .type(EventType.STEP_STARTED)
            .stepId(stepId)
            .build());
        
        System.out.println("[AdaptiveMonitor] 步骤开始: " + stepId);
    }
    
    /**
     * 记录步骤完成
     */
    public void recordStepComplete(String stepId, SubAgentResult result) {
        StepMetrics metrics = stepMetrics.get(stepId);
        if (metrics != null) {
            metrics.endTime = System.currentTimeMillis();
            metrics.status = result.isSuccess() ? StepStatus.COMPLETED : StepStatus.FAILED;
            metrics.executionTime = metrics.endTime - metrics.startTime;
            metrics.result = result;
            
            totalExecutionTime.addAndGet(metrics.executionTime);
            
            if (result.isSuccess()) {
                completedSteps.incrementAndGet();
            } else {
                failedSteps.incrementAndGet();
            }
        }
        
        eventLog.add(ExecutionEvent.builder()
            .timestamp(System.currentTimeMillis())
            .type(result.isSuccess() ? EventType.STEP_COMPLETED : EventType.STEP_FAILED)
            .stepId(stepId)
            .data(Map.of("success", result.isSuccess()))
            .build());
        
        // 执行自适应调整
        adaptStrategy();
        
        System.out.println("[AdaptiveMonitor] 步骤完成: " + stepId + ", 成功: " + result.isSuccess());
    }
    
    /**
     * 自适应策略调整
     */
    private void adaptStrategy() {
        // 根据失败率调整并行度
        int total = completedSteps.get() + failedSteps.get();
        if (total > 0) {
            double failureRate = (double) failedSteps.get() / total;
            
            if (failureRate > 0.3) {
                // 失败率过高，降低并行度
                currentParallelism = Math.max(1, currentParallelism - 1);
                parallelEnabled = false;
                System.out.println("[AdaptiveMonitor] 失败率较高 (" + String.format("%.0f%%", failureRate * 100) + 
                           ")，切换到串行模式");
            } else if (failureRate < 0.1 && total > 5) {
                // 失败率低，可以增加并行度
                currentParallelism = Math.min(8, currentParallelism + 1);
                parallelEnabled = true;
            }
        }
        
        // 估计剩余时间
        int remainingSteps = plan.getSteps().size() - total;
        if (total > 0) {
            long avgTime = totalExecutionTime.get() / total;
            estimatedRemainingTime = avgTime * remainingSteps;
        }
    }
    
    /**
     * 检查是否需要重新规划
     */
    public boolean needsReplanning() {
        // 如果失败率超过阈值，可能需要重新规划
        int total = completedSteps.get() + failedSteps.get();
        if (total > 3) {
            double failureRate = (double) failedSteps.get() / total;
            return failureRate > 0.5;
        }
        return false;
    }
    
    /**
     * 生成执行报告
     */
    public ExecutionReport generateReport() {
        int total = completedSteps.get() + failedSteps.get();
        double successRate = total > 0 ? (double) completedSteps.get() / total * 100 : 0;
        
        return ExecutionReport.builder()
            .planId(plan.getPlanId())
            .totalSteps(plan.getSteps().size())
            .completedSteps(completedSteps.get())
            .failedSteps(failedSteps.get())
            .successRate(successRate)
            .totalExecutionTime(totalExecutionTime.get())
            .estimatedRemainingTime(estimatedRemainingTime)
            .parallelEnabled(parallelEnabled)
            .currentParallelism(currentParallelism)
            .eventLog(new ArrayList<>(eventLog))
            .stepMetrics(new HashMap<>(stepMetrics))
            .build();
    }
    
    /**
     * 获取当前建议的并行度
     */
    public int getRecommendedParallelism() {
        return currentParallelism;
    }
    
    /**
     * 是否建议并行执行
     */
    public boolean isParallelRecommended() {
        return parallelEnabled;
    }
    
    /**
     * 获取步骤开始时间
     */
    public long getStepStartTime(String stepId) {
        StepMetrics metrics = stepMetrics.get(stepId);
        return metrics != null ? metrics.startTime : 0;
    }
    
    // ==================== 内部类 ====================
    
    private static class StepMetrics {
        volatile long startTime;
        volatile long endTime;
        volatile long executionTime;
        volatile StepStatus status;
        SubAgentResult result;
    }
    
    private enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }
    
    public enum EventType {
        STEP_STARTED, STEP_COMPLETED, STEP_FAILED, STRATEGY_ADJUSTED
    }
    
    public static class ExecutionEvent {
        private long timestamp;
        private EventType type;
        private String stepId;
        private Map<String, Object> data;
        
        public ExecutionEvent() {}
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long v) { this.timestamp = v; }
        public EventType getType() { return type; }
        public void setType(EventType v) { this.type = v; }
        public String getStepId() { return stepId; }
        public void setStepId(String v) { this.stepId = v; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> v) { this.data = v; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private final ExecutionEvent event = new ExecutionEvent();
            
            public Builder timestamp(long v) { event.timestamp = v; return this; }
            public Builder type(EventType v) { event.type = v; return this; }
            public Builder stepId(String v) { event.stepId = v; return this; }
            public Builder data(Map<String, Object> v) { event.data = v; return this; }
            public ExecutionEvent build() { return event; }
        }
    }
    
    public static class ExecutionReport {
        private String planId;
        private int totalSteps;
        private int completedSteps;
        private int failedSteps;
        private double successRate;
        private long totalExecutionTime;
        private long estimatedRemainingTime;
        private boolean parallelEnabled;
        private int currentParallelism;
        private List<ExecutionEvent> eventLog;
        private Map<String, StepMetrics> stepMetrics;
        
        public ExecutionReport() {}
        
        public String getPlanId() { return planId; }
        public void setPlanId(String v) { this.planId = v; }
        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int v) { this.totalSteps = v; }
        public int getCompletedSteps() { return completedSteps; }
        public void setCompletedSteps(int v) { this.completedSteps = v; }
        public int getFailedSteps() { return failedSteps; }
        public void setFailedSteps(int v) { this.failedSteps = v; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double v) { this.successRate = v; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public void setTotalExecutionTime(long v) { this.totalExecutionTime = v; }
        public long getEstimatedRemainingTime() { return estimatedRemainingTime; }
        public void setEstimatedRemainingTime(long v) { this.estimatedRemainingTime = v; }
        public boolean isParallelEnabled() { return parallelEnabled; }
        public void setParallelEnabled(boolean v) { this.parallelEnabled = v; }
        public int getCurrentParallelism() { return currentParallelism; }
        public void setCurrentParallelism(int v) { this.currentParallelism = v; }
        public List<ExecutionEvent> getEventLog() { return eventLog; }
        public void setEventLog(List<ExecutionEvent> v) { this.eventLog = v; }
        public Map<String, StepMetrics> getStepMetrics() { return stepMetrics; }
        public void setStepMetrics(Map<String, StepMetrics> v) { this.stepMetrics = v; }
        
        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("╔════════════════════════════════════════════════════════╗\n");
            sb.append("║              自适应执行报告                             ║\n");
            sb.append("╚════════════════════════════════════════════════════════╝\n\n");
            sb.append("计划ID: ").append(planId).append("\n");
            sb.append("进度: ").append(completedSteps).append("/").append(totalSteps).append("\n");
            sb.append("成功率: ").append(String.format("%.1f%%", successRate)).append("\n");
            sb.append("失败: ").append(failedSteps).append("\n");
            sb.append("总耗时: ").append(totalExecutionTime).append("ms\n");
            sb.append("预计剩余: ").append(estimatedRemainingTime).append("ms\n");
            sb.append("并行模式: ").append(parallelEnabled ? "开启" : "关闭").append("\n");
            sb.append("并行度: ").append(currentParallelism).append("\n");
            return sb.toString();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private final ExecutionReport report = new ExecutionReport();
            
            public Builder planId(String v) { report.planId = v; return this; }
            public Builder totalSteps(int v) { report.totalSteps = v; return this; }
            public Builder completedSteps(int v) { report.completedSteps = v; return this; }
            public Builder failedSteps(int v) { report.failedSteps = v; return this; }
            public Builder successRate(double v) { report.successRate = v; return this; }
            public Builder totalExecutionTime(long v) { report.totalExecutionTime = v; return this; }
            public Builder estimatedRemainingTime(long v) { report.estimatedRemainingTime = v; return this; }
            public Builder parallelEnabled(boolean v) { report.parallelEnabled = v; return this; }
            public Builder currentParallelism(int v) { report.currentParallelism = v; return this; }
            public Builder eventLog(List<ExecutionEvent> v) { report.eventLog = v; return this; }
            public Builder stepMetrics(Map<String, StepMetrics> v) { report.stepMetrics = v; return this; }
            public ExecutionReport build() { return report; }
        }
    }
}
