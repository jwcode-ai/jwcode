package com.jwcode.core.planner.ai;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.planner.AdaptiveExecutionMonitor;
import com.jwcode.core.planner.ExecutionPlan;
import com.jwcode.core.planner.PlanStep;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compatibility facade for the removed dynamic/A2A execution path.
 *
 * <p>Workflow execution is now owned by Workflow IR + EffectVM. This class is
 * kept for older planner callers and reports each plan step as scheduled for
 * the workflow runtime role selected by the plan.</p>
 */
public class DynamicExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(DynamicExecutionEngine.class);

    private final ToolRegistry toolRegistry;
    private final Map<String, ExecutionStatus> activeExecutions = new ConcurrentHashMap<>();

    public DynamicExecutionEngine(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public CompletableFuture<ExecutionResult> execute(ExecutionPlan plan, Agent parentAgent, Session parentSession) {
        String executionId = "workflow-scheduled-" + System.currentTimeMillis();
        activeExecutions.put(executionId, ExecutionStatus.RUNNING);

        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                int totalSteps = plan != null && plan.getSteps() != null ? plan.getSteps().size() : 0;
                if (plan != null && plan.getSteps() != null) {
                    for (PlanStep step : plan.getSteps()) {
                        String role = step.getAgentType() != null ? step.getAgentType() : "default";
                        log.info("[DynamicExecutionEngine] scheduled step {} for workflow runtime role {}",
                            step.getStepNumber(), role);
                    }
                }
                activeExecutions.put(executionId, ExecutionStatus.COMPLETED);
                return ExecutionResult.builder()
                    .executionId(executionId)
                    .planId(plan != null ? plan.getPlanId() : null)
                    .success(true)
                    .completedSteps(totalSteps)
                    .failedSteps(0)
                    .totalSteps(totalSteps)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
            } catch (Exception e) {
                activeExecutions.put(executionId, ExecutionStatus.FAILED);
                int totalSteps = plan != null && plan.getSteps() != null ? plan.getSteps().size() : 0;
                return ExecutionResult.builder()
                    .executionId(executionId)
                    .planId(plan != null ? plan.getPlanId() : null)
                    .success(false)
                    .completedSteps(0)
                    .failedSteps(totalSteps)
                    .totalSteps(totalSteps)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
            } finally {
                activeExecutions.remove(executionId);
            }
        });
    }

    public void pause(String executionId) {
        activeExecutions.put(executionId, ExecutionStatus.PAUSED);
    }

    public void resume(String executionId) {
        activeExecutions.put(executionId, ExecutionStatus.RUNNING);
    }

    public void cancel(String executionId) {
        activeExecutions.put(executionId, ExecutionStatus.CANCELLED);
    }

    public ExecutionStatus getStatus(String executionId) {
        return activeExecutions.getOrDefault(executionId, ExecutionStatus.COMPLETED);
    }

    public ExecutionReport getReport(String executionId) {
        return ExecutionReport.builder()
            .executionId(executionId)
            .status(getStatus(executionId))
            .build();
    }

    public void shutdown() {
        activeExecutions.clear();
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
                StepResult result = new StepResult();
                result.stepNumber = stepNumber;
                result.success = success;
                result.output = output;
                result.error = error;
                result.executionTimeMs = executionTimeMs;
                return result;
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
            return "Execution result report\n"
                + "Execution ID: " + executionId + "\n"
                + "Status: " + (success ? "success" : "failed") + "\n"
                + "Progress: " + completedSteps + "/" + totalSteps + "\n"
                + "Failed: " + failedSteps + "\n"
                + "Duration: " + durationMs + "ms\n";
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

            public Builder executionId(String executionId) { this.executionId = executionId; return this; }
            public Builder planId(String planId) { this.planId = planId; return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder completedSteps(int completedSteps) { this.completedSteps = completedSteps; return this; }
            public Builder failedSteps(int failedSteps) { this.failedSteps = failedSteps; return this; }
            public Builder totalSteps(int totalSteps) { this.totalSteps = totalSteps; return this; }
            public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
            public Builder monitorReport(AdaptiveExecutionMonitor.ExecutionReport monitorReport) { this.monitorReport = monitorReport; return this; }
            public Builder tracerReport(ExecutionTracer.TracerReport tracerReport) { this.tracerReport = tracerReport; return this; }
            public ExecutionResult build() {
                ExecutionResult result = new ExecutionResult();
                result.executionId = executionId;
                result.planId = planId;
                result.success = success;
                result.completedSteps = completedSteps;
                result.failedSteps = failedSteps;
                result.totalSteps = totalSteps;
                result.durationMs = durationMs;
                result.monitorReport = monitorReport;
                result.tracerReport = tracerReport;
                return result;
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

            public Builder executionId(String executionId) { this.executionId = executionId; return this; }
            public Builder status(ExecutionStatus status) { this.status = status; return this; }
            public Builder monitorReport(AdaptiveExecutionMonitor.ExecutionReport monitorReport) { this.monitorReport = monitorReport; return this; }
            public ExecutionReport build() {
                ExecutionReport result = new ExecutionReport();
                result.executionId = executionId;
                result.status = status;
                result.monitorReport = monitorReport;
                return result;
            }
        }
    }

    public enum ExecutionStatus {
        PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    }
}
