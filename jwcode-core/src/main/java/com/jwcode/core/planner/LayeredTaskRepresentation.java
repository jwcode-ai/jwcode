package com.jwcode.core.planner;

import com.jwcode.core.agent.parallel.SubAgentResult;
import com.jwcode.core.planner.ai.ReplanningStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * LayeredTaskRepresentation — 分层任务表征。
 *
 * <p>实现 Kimi Code 的三层动态系统：</p>
 * <ul>
 *   <li><b>目标层（GoalLayer）</b>：维持原始需求语义不变，仅人类显式修改时才刷新</li>
 *   <li><b>规划层（PlanningLayer）</b>：追踪子任务完成度与依赖关系，子 Agent 异常/超时触发刷新</li>
 *   <li><b>执行层（ExecutionLayer）</b>：工具调用、代码生成、环境交互，环境反馈变化时刷新</li>
 * </ul>
 */
public class LayeredTaskRepresentation {

    private static final Logger logger = LoggerFactory.getLogger(LayeredTaskRepresentation.class);

    private final GoalLayer goalLayer;
    private final PlanningLayer planningLayer;
    private final ExecutionLayer executionLayer;

    public LayeredTaskRepresentation(String originalRequest) {
        this.goalLayer = new GoalLayer(originalRequest);
        this.planningLayer = new PlanningLayer();
        this.executionLayer = new ExecutionLayer();
    }

    // ==================== 目标层 ====================

    public static class GoalLayer {
        private final String originalRequest;
        private volatile String refinedGoal;
        private volatile boolean humanModified = false;

        public GoalLayer(String originalRequest) {
            this.originalRequest = originalRequest;
            this.refinedGoal = originalRequest;
        }

        public String getOriginalRequest() { return originalRequest; }
        public String getRefinedGoal() { return refinedGoal; }

        /**
         * 人类显式修改目标时调用。
         */
        public void humanRefine(String newGoal) {
            this.refinedGoal = newGoal;
            this.humanModified = true;
            logger.info("[GoalLayer] 人类修改目标: {}", newGoal);
        }

        public boolean isHumanModified() { return humanModified; }

        /**
         * 检查规划层的结果是否可能损害目标层。
         */
        public boolean isGoalCompromised(List<String> failedSteps) {
            // 启发式：如果所有步骤都失败，可能目标已受损
            return failedSteps != null && !failedSteps.isEmpty() && failedSteps.size() >= 3;
        }
    }

    // ==================== 规划层 ====================

    public static class PlanningLayer {
        private final List<PlanStep> steps = new ArrayList<>();
        private final Map<String, StepStatus> stepStatuses = new ConcurrentHashMap<>();
        private final Map<String, List<String>> dependencies = new ConcurrentHashMap<>();
        private volatile int currentStepIndex = -1;

        public void setSteps(List<PlanStep> steps) {
            this.steps.clear();
            this.steps.addAll(steps);
            for (PlanStep step : steps) {
                stepStatuses.put(stepKey(step), StepStatus.PENDING);
                dependencies.put(stepKey(step), new ArrayList<>(step.getDependencies()));
            }
        }

        public List<PlanStep> getSteps() { return Collections.unmodifiableList(steps); }

        public void markStepRunning(int index) {
            if (index >= 0 && index < steps.size()) {
                stepStatuses.put(stepKey(steps.get(index)), StepStatus.RUNNING);
                currentStepIndex = index;
            }
        }

        public void markStepCompleted(int index, String result) {
            if (index >= 0 && index < steps.size()) {
                stepStatuses.put(stepKey(steps.get(index)), StepStatus.COMPLETED);
                steps.get(index).setActualOutput(result);
            }
        }

        public void markStepFailed(int index, String error) {
            if (index >= 0 && index < steps.size()) {
                stepStatuses.put(stepKey(steps.get(index)), StepStatus.FAILED);
                steps.get(index).setErrorMessage(error);
            }
        }

        public boolean needsReplanning() {
            long failed = stepStatuses.values().stream().filter(s -> s == StepStatus.FAILED).count();
            long total = stepStatuses.size();
            return total > 0 && (double) failed / total > 0.5;
        }

        public List<PlanStep> getReadySteps() {
            List<PlanStep> ready = new ArrayList<>();
            for (PlanStep step : steps) {
                String key = stepKey(step);
                if (stepStatuses.get(key) != StepStatus.PENDING) continue;

                List<String> deps = dependencies.getOrDefault(key, List.of());
                boolean allDepsMet = deps.stream().allMatch(dep -> {
                    return stepStatuses.getOrDefault(dep, StepStatus.PENDING) == StepStatus.COMPLETED;
                });

                if (allDepsMet) ready.add(step);
            }
            return ready;
        }

        public double getCompletionRatio() {
            long completed = stepStatuses.values().stream().filter(s -> s == StepStatus.COMPLETED).count();
            long total = stepStatuses.size();
            return total > 0 ? (double) completed / total : 0;
        }

        private String stepKey(PlanStep step) {
            return "task-" + step.getStepNumber();
        }
    }

    // ==================== 执行层 ====================

    public static class ExecutionLayer {
        private final List<ToolExecutionRecord> toolExecutions = Collections.synchronizedList(new ArrayList<>());
        private volatile String lastEnvironmentFeedback;

        public void recordToolExecution(String toolName, String input, String output, boolean success) {
            toolExecutions.add(new ToolExecutionRecord(toolName, input, output, success, System.currentTimeMillis()));
        }

        public List<ToolExecutionRecord> getRecentExecutions(int n) {
            synchronized (toolExecutions) {
                int size = toolExecutions.size();
                return toolExecutions.subList(Math.max(0, size - n), size);
            }
        }

        public void setEnvironmentFeedback(String feedback) {
            this.lastEnvironmentFeedback = feedback;
        }

        public String getLastEnvironmentFeedback() { return lastEnvironmentFeedback; }

        /**
         * 检测环境变化是否需要触发执行层刷新。
         */
        public boolean hasSignificantChange() {
            // 启发式：最近有失败或错误反馈
            return toolExecutions.stream()
                .skip(Math.max(0, toolExecutions.size() - 3))
                .anyMatch(r -> !r.success);
        }
    }

    // ==================== 数据类 ====================

    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    public record ToolExecutionRecord(
        String toolName,
        String input,
        String output,
        boolean success,
        long timestamp
    ) {}

    // ==================== 便捷访问 ====================

    public GoalLayer getGoalLayer() { return goalLayer; }
    public PlanningLayer getPlanningLayer() { return planningLayer; }
    public ExecutionLayer getExecutionLayer() { return executionLayer; }

    /**
     * 生成当前三层状态的摘要报告。
     */
    public String generateStatusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔════════════════════════════════════════════════════════╗\n");
        sb.append("║           分层任务表征状态报告                          ║\n");
        sb.append("╚════════════════════════════════════════════════════════╝\n\n");

        sb.append("【目标层】\n");
        sb.append("  原始需求: ").append(truncate(goalLayer.getOriginalRequest(), 60)).append("\n");
        sb.append("  当前目标: ").append(truncate(goalLayer.getRefinedGoal(), 60)).append("\n");
        sb.append("  人类修改: ").append(goalLayer.isHumanModified() ? "是" : "否").append("\n\n");

        sb.append("【规划层】\n");
        sb.append("  总步骤: ").append(planningLayer.steps.size()).append("\n");
        sb.append("  完成度: ").append(String.format("%.0f%%", planningLayer.getCompletionRatio() * 100)).append("\n");
        sb.append("  需重规划: ").append(planningLayer.needsReplanning() ? "是" : "否").append("\n\n");

        sb.append("【执行层】\n");
        sb.append("  工具调用数: ").append(executionLayer.toolExecutions.size()).append("\n");
        sb.append("  环境反馈: ").append(
            executionLayer.lastEnvironmentFeedback != null
                ? truncate(executionLayer.lastEnvironmentFeedback, 60)
                : "无"
        ).append("\n");

        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
