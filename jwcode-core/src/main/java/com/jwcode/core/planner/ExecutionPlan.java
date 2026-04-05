package com.jwcode.core.planner;

import com.jwcode.core.agent.parallel.SubAgentTask;
import com.jwcode.core.agent.parallel.SubAgentResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 执行计划
 */
public class ExecutionPlan {
    
    private String planId;
    private String originalRequest;
    private IntentAnalysis intent;
    private List<PlanStep> steps;
    private int estimatedSteps;
    private long planningTimeMs;
    private List<String> validationIssues;
    private PlanStatus status = PlanStatus.DRAFT;
    private long createdAt = System.currentTimeMillis();
    
    public enum PlanStatus {
        DRAFT,          // 草稿
        VALID,          // 已验证
        NEEDS_REVIEW,   // 需要审核
        APPROVED,       // 已批准
        EXECUTING,      // 执行中
        COMPLETED,      // 已完成
        FAILED          // 失败
    }
    
    public ExecutionPlan() {
        this.steps = new ArrayList<>();
        this.validationIssues = new ArrayList<>();
    }
    
    // Getters
    public String getPlanId() { return planId; }
    public String getOriginalRequest() { return originalRequest; }
    public IntentAnalysis getIntent() { return intent; }
    public List<PlanStep> getSteps() { 
        if (steps == null) steps = new ArrayList<>();
        return steps; 
    }
    public int getEstimatedSteps() { return estimatedSteps; }
    public long getPlanningTimeMs() { return planningTimeMs; }
    public List<String> getValidationIssues() { 
        if (validationIssues == null) validationIssues = new ArrayList<>();
        return validationIssues; 
    }
    public PlanStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    
    // Setters
    public void setPlanId(String planId) { this.planId = planId; }
    public void setOriginalRequest(String originalRequest) { this.originalRequest = originalRequest; }
    public void setIntent(IntentAnalysis intent) { this.intent = intent; }
    public void setSteps(List<PlanStep> steps) { this.steps = steps; }
    public void setEstimatedSteps(int estimatedSteps) { this.estimatedSteps = estimatedSteps; }
    public void setPlanningTimeMs(long planningTimeMs) { this.planningTimeMs = planningTimeMs; }
    public void setValidationIssues(List<String> validationIssues) { this.validationIssues = validationIssues; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    /**
     * 转换为并行任务列表
     */
    public List<SubAgentTask> toSubAgentTasks() {
        return steps.stream()
            .map(step -> SubAgentTask.builder()
                .taskId(planId + "_step_" + step.getStepNumber())
                .instruction(step.getDescription())
                .agentType(step.getAgentType())
                .priority(step.getPriority())
                .dependencies(step.getDependencies())
                .context(step.getContext())
                .build())
            .collect(Collectors.toList());
    }
    
    /**
     * 获取可以并行执行的步骤组
     */
    public List<List<PlanStep>> getParallelizableGroups() {
        List<List<PlanStep>> groups = new ArrayList<>();
        List<PlanStep> remaining = new ArrayList<>(steps);
        
        while (!remaining.isEmpty()) {
            // 找出没有未完成依赖的步骤
            List<PlanStep> ready = remaining.stream()
                .filter(step -> step.getDependencies().isEmpty() || 
                    step.getDependencies().stream()
                        .allMatch(dep -> remaining.stream()
                            .noneMatch(r -> String.valueOf(r.getStepNumber()).equals(dep))))
                .collect(Collectors.toList());
            
            if (ready.isEmpty()) {
                // 有循环依赖，强制分组
                ready = List.of(remaining.get(0));
            }
            
            groups.add(ready);
            remaining.removeAll(ready);
        }
        
        return groups;
    }
    
    /**
     * 格式化计划为可读文本
     */
    public String formatPlan() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔════════════════════════════════════════════════════════╗\n");
        sb.append("║              任务执行计划                               ║\n");
        sb.append("╚════════════════════════════════════════════════════════╝\n\n");
        
        sb.append("意图分析:\n");
        sb.append("  类型: ").append(intent.getType()).append("\n");
        sb.append("  置信度: ").append(String.format("%.0f%%", intent.getConfidence() * 100)).append("\n");
        if (!intent.getEntities().isEmpty()) {
            sb.append("  实体: ").append(intent.getEntities()).append("\n");
        }
        sb.append("\n");
        
        sb.append("执行步骤 (共 ").append(steps.size()).append(" 步):\n\n");
        
        for (PlanStep step : steps) {
            String deps = step.getDependencies().isEmpty() ? "" : 
                " [依赖: " + String.join(",", step.getDependencies()) + "]";
            String agent = step.getAgentType() != null ? " (@" + step.getAgentType() + ")" : "";
            
            sb.append(String.format("%d. %s%s%s\n", 
                step.getStepNumber(), 
                step.getAction(),
                agent,
                deps));
            sb.append("   ").append(step.getDescription()).append("\n\n");
        }
        
        if (!validationIssues.isEmpty()) {
            sb.append("⚠ 注意事项:\n");
            for (String issue : validationIssues) {
                sb.append("  - ").append(issue).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 估计总执行时间（毫秒）
     */
    public long estimateTotalTimeMs() {
        // 简化的估计：串行步骤时间 + 并行步骤最大时间
        long serialTime = steps.stream()
            .filter(s -> !s.getDependencies().isEmpty())
            .count() * 30000L; // 假设每步30秒
        
        long parallelTime = steps.stream()
            .filter(s -> s.getDependencies().isEmpty())
            .count() * 20000L; // 并行步骤假设20秒
        
        return Math.max(serialTime, parallelTime);
    }
    
    // Builder
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String planId;
        private String originalRequest;
        private IntentAnalysis intent;
        private List<PlanStep> steps = new ArrayList<>();
        private int estimatedSteps;
        private long planningTimeMs;
        private List<String> validationIssues = new ArrayList<>();
        private PlanStatus status = PlanStatus.DRAFT;
        private long createdAt = System.currentTimeMillis();
        
        public Builder planId(String planId) { this.planId = planId; return this; }
        public Builder originalRequest(String originalRequest) { this.originalRequest = originalRequest; return this; }
        public Builder intent(IntentAnalysis intent) { this.intent = intent; return this; }
        public Builder steps(List<PlanStep> steps) { this.steps = steps; return this; }
        public Builder estimatedSteps(int estimatedSteps) { this.estimatedSteps = estimatedSteps; return this; }
        public Builder planningTimeMs(long planningTimeMs) { this.planningTimeMs = planningTimeMs; return this; }
        public Builder validationIssues(List<String> validationIssues) { this.validationIssues = validationIssues; return this; }
        public Builder status(PlanStatus status) { this.status = status; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
        
        public ExecutionPlan build() {
            ExecutionPlan plan = new ExecutionPlan();
            plan.planId = this.planId;
            plan.originalRequest = this.originalRequest;
            plan.intent = this.intent;
            plan.steps = this.steps;
            plan.estimatedSteps = this.estimatedSteps;
            plan.planningTimeMs = this.planningTimeMs;
            plan.validationIssues = this.validationIssues;
            plan.status = this.status;
            plan.createdAt = this.createdAt;
            return plan;
        }
    }
}
