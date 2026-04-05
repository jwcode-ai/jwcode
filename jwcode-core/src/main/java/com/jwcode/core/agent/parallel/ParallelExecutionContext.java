package com.jwcode.core.agent.parallel;

import com.jwcode.core.session.Session;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 并行执行上下文
 * 
 * 管理一批并行任务的执行状态和结果聚合
 */
public class ParallelExecutionContext {
    
    private final String executionId;
    private final List<SubAgentTask> tasks;
    private final Session session;
    
    private final Map<String, SubAgentResult> results = new ConcurrentHashMap<>();
    private final Map<String, Throwable> errors = new ConcurrentHashMap<>();
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final long startTime;
    
    private volatile ExecutionStatus status = ExecutionStatus.RUNNING;
    
    private final CompletableFuture<BatchResult> completionFuture = new CompletableFuture<>();
    
    public enum ExecutionStatus {
        RUNNING,      // 执行中
        COMPLETED,    // 全部完成
        PARTIAL,      // 部分完成（有失败）
        FAILED,       // 全部失败
        CANCELLED     // 已取消
    }
    
    public ParallelExecutionContext(List<SubAgentTask> tasks, Session session) {
        this.executionId = "parallel_" + System.currentTimeMillis();
        this.tasks = new ArrayList<>(tasks);
        this.session = session;
        this.startTime = System.currentTimeMillis();
    }
    
    // Getters
    public String getExecutionId() { return executionId; }
    public List<SubAgentTask> getTasks() { return tasks; }
    public Session getSession() { return session; }
    public ExecutionStatus getStatus() { return status; }
    
    /**
     * 添加执行结果
     */
    public void addResult(SubAgentResult result) {
        results.put(result.getTaskId(), result);
        int completed = completedCount.incrementAndGet();
        
        // 检查是否全部完成
        if (completed >= tasks.size()) {
            finalizeExecution();
        }
    }
    
    /**
     * 添加错误
     */
    public void addError(String taskId, Throwable error) {
        errors.put(taskId, error);
        int completed = completedCount.incrementAndGet();
        
        if (completed >= tasks.size()) {
            finalizeExecution();
        }
    }
    
    /**
     * 获取任务结果
     */
    public Optional<SubAgentResult> getResult(String taskId) {
        return Optional.ofNullable(results.get(taskId));
    }
    
    /**
     * 获取所有成功结果
     */
    public List<SubAgentResult> getSuccessfulResults() {
        return results.values().stream()
            .filter(SubAgentResult::isSuccess)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有失败结果
     */
    public List<SubAgentResult> getFailedResults() {
        return results.values().stream()
            .filter(r -> !r.isSuccess())
            .collect(Collectors.toList());
    }
    
    /**
     * 获取执行进度 (0-100)
     */
    public int getProgress() {
        if (tasks.isEmpty()) return 100;
        return (completedCount.get() * 100) / tasks.size();
    }
    
    /**
     * 获取总执行时间
     */
    public long getExecutionTimeMs() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 是否全部成功
     */
    public boolean isAllSuccessful() {
        return results.size() == tasks.size() && 
               results.values().stream().allMatch(SubAgentResult::isSuccess);
    }
    
    /**
     * 等待所有任务完成
     */
    public BatchResult awaitCompletion() throws InterruptedException {
        while (status == ExecutionStatus.RUNNING) {
            Thread.sleep(100);
        }
        return getBatchResult();
    }
    
    /**
     * 等待所有任务完成（带超时）
     */
    public Optional<BatchResult> awaitCompletion(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (status == ExecutionStatus.RUNNING && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        return status != ExecutionStatus.RUNNING ? Optional.of(getBatchResult()) : Optional.empty();
    }
    
    /**
     * 等待所有任务完成（带超时）
     */
    public Optional<BatchResult> awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return awaitCompletion(unit.toMillis(timeout));
    }
    
    /**
     * 获取批量结果
     */
    public BatchResult getBatchResult() {
        return BatchResult.builder()
            .executionId(executionId)
            .totalTasks(tasks.size())
            .successfulCount((int) results.values().stream().filter(SubAgentResult::isSuccess).count())
            .failedCount((int) results.values().stream().filter(r -> !r.isSuccess()).count())
            .results(new ArrayList<>(results.values()))
            .errors(new HashMap<>(errors))
            .executionTimeMs(getExecutionTimeMs())
            .status(status)
            .build();
    }
    
    /**
     * 获取完成 Future
     */
    public CompletableFuture<BatchResult> getCompletionFuture() {
        return completionFuture;
    }
    
    /**
     * 完成执行
     */
    private void finalizeExecution() {
        long successCount = results.values().stream().filter(SubAgentResult::isSuccess).count();
        
        if (successCount == tasks.size()) {
            status = ExecutionStatus.COMPLETED;
        } else if (successCount > 0) {
            status = ExecutionStatus.PARTIAL;
        } else {
            status = ExecutionStatus.FAILED;
        }
        
        completionFuture.complete(getBatchResult());
    }
    
    /**
     * 取消执行
     */
    public void cancel() {
        status = ExecutionStatus.CANCELLED;
        completionFuture.complete(getBatchResult());
    }
    
    // ==================== 批量结果 ====================
    
    public static class BatchResult {
        private String executionId;
        private int totalTasks;
        private int successfulCount;
        private int failedCount;
        private List<SubAgentResult> results;
        private Map<String, Throwable> errors;
        private long executionTimeMs;
        private ExecutionStatus status;
        
        public BatchResult() {}
        
        public String getExecutionId() { return executionId; }
        public void setExecutionId(String v) { this.executionId = v; }
        public int getTotalTasks() { return totalTasks; }
        public void setTotalTasks(int v) { this.totalTasks = v; }
        public int getSuccessfulCount() { return successfulCount; }
        public void setSuccessfulCount(int v) { this.successfulCount = v; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int v) { this.failedCount = v; }
        public List<SubAgentResult> getResults() { return results; }
        public void setResults(List<SubAgentResult> v) { this.results = v; }
        public Map<String, Throwable> getErrors() { return errors; }
        public void setErrors(Map<String, Throwable> v) { this.errors = v; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long v) { this.executionTimeMs = v; }
        public ExecutionStatus getStatus() { return status; }
        public void setStatus(ExecutionStatus v) { this.status = v; }
        
        /**
         * 合并所有成功结果的输出
         */
        public String getCombinedOutput() {
            StringBuilder sb = new StringBuilder();
            for (SubAgentResult result : results) {
                if (result.isSuccess() && result.getOutput() != null) {
                    sb.append("## ").append(result.getTaskId()).append("\n\n");
                    sb.append(result.getOutput()).append("\n\n");
                }
            }
            return sb.toString();
        }
        
        /**
         * 获取成功率
         */
        public double getSuccessRate() {
            if (totalTasks == 0) return 0;
            return (double) successfulCount / totalTasks * 100;
        }
        
        /**
         * 格式化报告
         */
        public String formatReport() {
            StringBuilder report = new StringBuilder();
            report.append("╔════════════════════════════════════════╗\n");
            report.append("║       并行执行报告                      ║\n");
            report.append("╚════════════════════════════════════════╝\n\n");
            report.append("执行ID: ").append(executionId).append("\n");
            report.append("状态: ").append(status).append("\n");
            report.append("总任务: ").append(totalTasks).append("\n");
            report.append("成功: ").append(successfulCount).append("\n");
            report.append("失败: ").append(failedCount).append("\n");
            report.append("成功率: ").append(String.format("%.1f%%", getSuccessRate())).append("\n");
            report.append("耗时: ").append(executionTimeMs).append("ms\n\n");
            
            if (!results.isEmpty()) {
                report.append("详细结果:\n");
                for (SubAgentResult result : results) {
                    String icon = result.isSuccess() ? "✓" : "✗";
                    report.append("  ").append(icon).append(" ")
                          .append(result.getTaskId())
                          .append(" (").append(result.getExecutionTimeMs()).append("ms)\n");
                }
            }
            
            return report.toString();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private final BatchResult result = new BatchResult();
            
            public Builder executionId(String v) { result.executionId = v; return this; }
            public Builder totalTasks(int v) { result.totalTasks = v; return this; }
            public Builder successfulCount(int v) { result.successfulCount = v; return this; }
            public Builder failedCount(int v) { result.failedCount = v; return this; }
            public Builder results(List<SubAgentResult> v) { result.results = v; return this; }
            public Builder errors(Map<String, Throwable> v) { result.errors = v; return this; }
            public Builder executionTimeMs(long v) { result.executionTimeMs = v; return this; }
            public Builder status(ExecutionStatus v) { result.status = v; return this; }
            public BatchResult build() { return result; }
        }
    }
}
