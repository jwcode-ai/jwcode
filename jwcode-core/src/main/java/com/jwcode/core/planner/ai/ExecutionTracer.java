package com.jwcode.core.planner.ai;

import com.jwcode.core.planner.ExecutionPlan;
import com.jwcode.core.planner.PlanStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ExecutionTracer - 执行追踪器
 * 
 * 记录任务执行的完整生命周期，支持：
 * 1. 执行路径记录 - 记录每个步骤的执行轨迹
 * 2. 决策过程记录 - 记录 AI 的决策过程
 * 3. 可视化支持 - 生成可可视化的执行图
 * 4. 回放功能 - 支持执行过程回放
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ExecutionTracer {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutionTracer.class);
    
    // 执行记录存储
    private final Map<String, ExecutionTrace> traces;
    
    public ExecutionTracer() {
        this.traces = new ConcurrentHashMap<>();
    }
    
    /**
     * 开始执行追踪
     */
    public void startExecution(String executionId, ExecutionPlan plan) {
        ExecutionTrace trace = ExecutionTrace.builder()
            .executionId(executionId)
            .planId(plan.getPlanId())
            .originalRequest(plan.getOriginalRequest())
            .startTime(Instant.now())
            .events(new ArrayList<>())
            .decisions(new ArrayList<>())
            .stepExecutions(new HashMap<>())
            .build();
        
        traces.put(executionId, trace);
        
        // 记录开始事件
        recordEvent(executionId, TraceEvent.builder()
            .timestamp(System.currentTimeMillis())
            .type(EventType.EXECUTION_STARTED)
            .description("执行开始")
            .data(Map.of("stepCount", plan.getSteps().size()))
            .build());
        
        log.info("[ExecutionTracer] 开始追踪执行: " + executionId);
    }
    
    /**
     * 记录步骤组执行
     */
    public void recordStepGroup(String executionId, int groupIndex, 
                                List<PlanStep> steps, List<DynamicExecutionEngine.StepResult> results) {
        ExecutionTrace trace = traces.get(executionId);
        if (trace == null) return;
        
        // 记录组执行事件
        recordEvent(executionId, TraceEvent.builder()
            .timestamp(System.currentTimeMillis())
            .type(EventType.STEP_GROUP_STARTED)
            .description("执行第 " + (groupIndex + 1) + " 组任务")
            .data(Map.of(
                "groupIndex", groupIndex,
                "stepCount", steps.size(),
                "stepNumbers", steps.stream().map(PlanStep::getStepNumber).collect(Collectors.toList())
            ))
            .build());
        
        // 记录每个步骤的执行
        for (int i = 0; i < steps.size(); i++) {
            PlanStep step = steps.get(i);
            DynamicExecutionEngine.StepResult result = results.get(i);
            
            StepExecution stepExec = StepExecution.builder()
                .stepNumber(step.getStepNumber())
                .action(step.getAction())
                .agentType(step.getAgentType())
                .startTime(trace.getStartTime().toEpochMilli()) // 简化，实际需要精确时间
                .endTime(System.currentTimeMillis())
                .success(result.isSuccess())
                .output(result.getOutput())
                .error(result.getError())
                .executionTimeMs(result.getExecutionTimeMs())
                .build();
            
            trace.getStepExecutions().put(step.getStepNumber(), stepExec);
            
            // 记录步骤完成事件
            recordEvent(executionId, TraceEvent.builder()
                .timestamp(System.currentTimeMillis())
                .type(result.isSuccess() ? EventType.STEP_COMPLETED : EventType.STEP_FAILED)
                .stepNumber(step.getStepNumber())
                .description("步骤 " + step.getStepNumber() + " " + (result.isSuccess() ? "完成" : "失败"))
                .data(Map.of(
                    "success", result.isSuccess(),
                    "executionTimeMs", result.getExecutionTimeMs()
                ))
                .build());
        }
    }
    
    /**
     * 记录决策
     */
    public void recordDecision(String executionId, String decisionType, 
                               String reasoning, Map<String, Object> context) {
        ExecutionTrace trace = traces.get(executionId);
        if (trace == null) return;
        
        DecisionRecord decision = DecisionRecord.builder()
            .timestamp(System.currentTimeMillis())
            .type(decisionType)
            .reasoning(reasoning)
            .context(context)
            .build();
        
        trace.getDecisions().add(decision);
        
        recordEvent(executionId, TraceEvent.builder()
            .timestamp(System.currentTimeMillis())
            .type(EventType.DECISION_MADE)
            .description("决策: " + decisionType)
            .data(Map.of("reasoning", reasoning))
            .build());
    }
    
    /**
     * 记录重规划
     */
    public void recordReplanning(String executionId, ReplanningStrategy.StrategyType strategy, 
                                  String reason) {
        recordEvent(executionId, TraceEvent.builder()
            .timestamp(System.currentTimeMillis())
            .type(EventType.REPLANNING_OCCURRED)
            .description("触发重规划: " + strategy)
            .data(Map.of(
                "strategy", strategy.name(),
                "reason", reason
            ))
            .build());
    }
    
    /**
     * 完成执行
     */
    public void completeExecution(String executionId, DynamicExecutionEngine.ExecutionResult result) {
        ExecutionTrace trace = traces.get(executionId);
        if (trace == null) return;
        
        trace.setEndTime(Instant.now());
        trace.setSuccess(result.isSuccess());
        trace.setFinalResult(result);
        
        recordEvent(executionId, TraceEvent.builder()
            .timestamp(System.currentTimeMillis())
            .type(EventType.EXECUTION_COMPLETED)
            .description("执行完成")
            .data(Map.of(
                "success", result.isSuccess(),
                "durationMs", result.getDurationMs(),
                "completedSteps", result.getCompletedSteps(),
                "failedSteps", result.getFailedSteps()
            ))
            .build());
        
        log.info("[ExecutionTracer] 执行完成: " + executionId + ", 成功=" + result.isSuccess());
    }
    
    /**
     * 执行失败
     */
    public void failExecution(String executionId, Throwable error) {
        ExecutionTrace trace = traces.get(executionId);
        if (trace == null) return;
        
        trace.setEndTime(Instant.now());
        trace.setSuccess(false);
        trace.setErrorMessage(error.getMessage());
        
        recordEvent(executionId, TraceEvent.builder()
            .timestamp(System.currentTimeMillis())
            .type(EventType.EXECUTION_FAILED)
            .description("执行失败: " + error.getMessage())
            .data(Map.of("error", error.getMessage()))
            .build());
    }
    
    /**
     * 获取执行报告
     */
    public TracerReport getReport(String executionId) {
        ExecutionTrace trace = traces.get(executionId);
        if (trace == null) return null;
        
        return TracerReport.builder()
            .executionId(executionId)
            .planId(trace.getPlanId())
            .originalRequest(trace.getOriginalRequest())
            .startTime(trace.getStartTime())
            .endTime(trace.getEndTime())
            .durationMs(trace.getDurationMs())
            .success(trace.isSuccess())
            .eventCount(trace.getEvents().size())
            .decisionCount(trace.getDecisions().size())
            .completedSteps((int) trace.getStepExecutions().values().stream()
                .filter(StepExecution::isSuccess).count())
            .failedSteps((int) trace.getStepExecutions().values().stream()
                .filter(s -> !s.isSuccess()).count())
            .criticalPath(generateCriticalPath(trace))
            .build();
    }
    
    /**
     * 获取完整追踪数据
     */
    public ExecutionTrace getTrace(String executionId) {
        return traces.get(executionId);
    }
    
    /**
     * 生成可视化数据（Mermaid 格式）
     */
    public String generateMermaidDiagram(String executionId) {
        ExecutionTrace trace = traces.get(executionId);
        if (trace == null) return "";
        
        StringBuilder sb = new StringBuilder();
        sb.append("```mermaid\n");
        sb.append("flowchart TD\n");
        
        // 节点
        for (StepExecution step : trace.getStepExecutions().values()) {
            String nodeStyle = step.isSuccess() ? "[" : "((";
            String closeStyle = step.isSuccess() ? "]" : "))";
            String nodeColor = step.isSuccess() ? "" : ":::failed";
            
            sb.append("    S").append(step.getStepNumber())
              .append(nodeStyle).append(step.getAction()).append(closeStyle).append(nodeColor).append("\n");
        }
        
        // 失败节点样式
        sb.append("    classDef failed fill:#f96,stroke:#333,stroke-width:2px\n");
        
        // 边
        for (int i = 1; i < trace.getStepExecutions().size(); i++) {
            sb.append("    S").append(i).append(" --> S").append(i + 1).append("\n");
        }
        
        sb.append("```");
        return sb.toString();
    }
    
    /**
     * 生成执行时间线
     */
    public String generateTimeline(String executionId) {
        ExecutionTrace trace = traces.get(executionId);
        if (trace == null) return "";
        
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║              ⏱️ 执行时间线                               ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
        
        long startTime = trace.getStartTime().toEpochMilli();
        
        for (TraceEvent event : trace.getEvents()) {
            long offset = event.getTimestamp() - startTime;
            String timeStr = String.format("+%5dms", offset);
            
            String icon = switch (event.getType()) {
                case EXECUTION_STARTED -> "🚀";
                case EXECUTION_COMPLETED -> "✅";
                case EXECUTION_FAILED -> "❌";
                case STEP_COMPLETED -> "✓";
                case STEP_FAILED -> "✗";
                case REPLANNING_OCCURRED -> "🔄";
                case DECISION_MADE -> "💡";
                default -> "•";
            };
            
            sb.append(String.format("%s %s %s\n", timeStr, icon, event.getDescription()));
        }
        
        return sb.toString();
    }
    
    /**
     * 记录事件
     */
    private void recordEvent(String executionId, TraceEvent event) {
        ExecutionTrace trace = traces.get(executionId);
        if (trace != null) {
            trace.getEvents().add(event);
        }
    }
    
    /**
     * 生成关键路径
     */
    private List<Integer> generateCriticalPath(ExecutionTrace trace) {
        return trace.getStepExecutions().values().stream()
            .sorted(Comparator.comparingLong(StepExecution::getStartTime))
            .map(StepExecution::getStepNumber)
            .collect(Collectors.toList());
    }
    
    /**
     * 清除历史记录
     */
    public void clear() {
        traces.clear();
    }
    
    /**
     * 获取所有执行ID
     */
    public Set<String> getAllExecutionIds() {
        return traces.keySet();
    }
    
    // ==================== 数据类 ====================
    
    public static class ExecutionTrace {
        private String executionId;
        private String planId;
        private String originalRequest;
        private Instant startTime;
        private Instant endTime;
        private boolean success;
        private String errorMessage;
        private DynamicExecutionEngine.ExecutionResult finalResult;
        private List<TraceEvent> events = new ArrayList<>();
        private List<DecisionRecord> decisions = new ArrayList<>();
        private Map<Integer, StepExecution> stepExecutions = new HashMap<>();
        
        // Getters
        public String getExecutionId() { return executionId; }
        public String getPlanId() { return planId; }
        public String getOriginalRequest() { return originalRequest; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public DynamicExecutionEngine.ExecutionResult getFinalResult() { return finalResult; }
        public List<TraceEvent> getEvents() { return events; }
        public List<DecisionRecord> getDecisions() { return decisions; }
        public Map<Integer, StepExecution> getStepExecutions() { return stepExecutions; }
        
        // Setters
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setOriginalRequest(String originalRequest) { this.originalRequest = originalRequest; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public void setFinalResult(DynamicExecutionEngine.ExecutionResult finalResult) { this.finalResult = finalResult; }
        public void setEvents(List<TraceEvent> events) { this.events = events; }
        public void setDecisions(List<DecisionRecord> decisions) { this.decisions = decisions; }
        public void setStepExecutions(Map<Integer, StepExecution> stepExecutions) { this.stepExecutions = stepExecutions; }
        
        public long getDurationMs() {
            if (endTime == null) return System.currentTimeMillis() - startTime.toEpochMilli();
            return endTime.toEpochMilli() - startTime.toEpochMilli();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private String executionId;
            private String planId;
            private String originalRequest;
            private Instant startTime;
            private Instant endTime;
            private boolean success;
            private String errorMessage;
            private DynamicExecutionEngine.ExecutionResult finalResult;
            private List<TraceEvent> events = new ArrayList<>();
            private List<DecisionRecord> decisions = new ArrayList<>();
            private Map<Integer, StepExecution> stepExecutions = new HashMap<>();
            
            public Builder executionId(String v) { this.executionId = v; return this; }
            public Builder planId(String v) { this.planId = v; return this; }
            public Builder originalRequest(String v) { this.originalRequest = v; return this; }
            public Builder startTime(Instant v) { this.startTime = v; return this; }
            public Builder endTime(Instant v) { this.endTime = v; return this; }
            public Builder success(boolean v) { this.success = v; return this; }
            public Builder errorMessage(String v) { this.errorMessage = v; return this; }
            public Builder finalResult(DynamicExecutionEngine.ExecutionResult v) { this.finalResult = v; return this; }
            public Builder events(List<TraceEvent> v) { this.events = v; return this; }
            public Builder decisions(List<DecisionRecord> v) { this.decisions = v; return this; }
            public Builder stepExecutions(Map<Integer, StepExecution> v) { this.stepExecutions = v; return this; }
            
            public ExecutionTrace build() {
                ExecutionTrace t = new ExecutionTrace();
                t.executionId = this.executionId;
                t.planId = this.planId;
                t.originalRequest = this.originalRequest;
                t.startTime = this.startTime;
                t.endTime = this.endTime;
                t.success = this.success;
                t.errorMessage = this.errorMessage;
                t.finalResult = this.finalResult;
                t.events = this.events;
                t.decisions = this.decisions;
                t.stepExecutions = this.stepExecutions;
                return t;
            }
        }
    }
    
    public static class TraceEvent {
        private long timestamp;
        private EventType type;
        private Integer stepNumber;
        private String description;
        private Map<String, Object> data;
        
        public long getTimestamp() { return timestamp; }
        public EventType getType() { return type; }
        public Integer getStepNumber() { return stepNumber; }
        public String getDescription() { return description; }
        public Map<String, Object> getData() { return data; }
        
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public void setType(EventType type) { this.type = type; }
        public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
        public void setDescription(String description) { this.description = description; }
        public void setData(Map<String, Object> data) { this.data = data; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private long timestamp;
            private EventType type;
            private Integer stepNumber;
            private String description;
            private Map<String, Object> data;
            
            public Builder timestamp(long v) { this.timestamp = v; return this; }
            public Builder type(EventType v) { this.type = v; return this; }
            public Builder stepNumber(Integer v) { this.stepNumber = v; return this; }
            public Builder description(String v) { this.description = v; return this; }
            public Builder data(Map<String, Object> v) { this.data = v; return this; }
            
            public TraceEvent build() {
                TraceEvent e = new TraceEvent();
                e.timestamp = this.timestamp;
                e.type = this.type;
                e.stepNumber = this.stepNumber;
                e.description = this.description;
                e.data = this.data;
                return e;
            }
        }
    }
    
    public static class StepExecution {
        private int stepNumber;
        private String action;
        private String agentType;
        private long startTime;
        private long endTime;
        private boolean success;
        private String output;
        private String error;
        private long executionTimeMs;
        
        public int getStepNumber() { return stepNumber; }
        public String getAction() { return action; }
        public String getAgentType() { return agentType; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public boolean isSuccess() { return success; }
        public String getOutput() { return output; }
        public String getError() { return error; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        
        public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
        public void setAction(String action) { this.action = action; }
        public void setAgentType(String agentType) { this.agentType = agentType; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setOutput(String output) { this.output = output; }
        public void setError(String error) { this.error = error; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private int stepNumber;
            private String action;
            private String agentType;
            private long startTime;
            private long endTime;
            private boolean success;
            private String output;
            private String error;
            private long executionTimeMs;
            
            public Builder stepNumber(int v) { this.stepNumber = v; return this; }
            public Builder action(String v) { this.action = v; return this; }
            public Builder agentType(String v) { this.agentType = v; return this; }
            public Builder startTime(long v) { this.startTime = v; return this; }
            public Builder endTime(long v) { this.endTime = v; return this; }
            public Builder success(boolean v) { this.success = v; return this; }
            public Builder output(String v) { this.output = v; return this; }
            public Builder error(String v) { this.error = v; return this; }
            public Builder executionTimeMs(long v) { this.executionTimeMs = v; return this; }
            
            public StepExecution build() {
                StepExecution s = new StepExecution();
                s.stepNumber = this.stepNumber;
                s.action = this.action;
                s.agentType = this.agentType;
                s.startTime = this.startTime;
                s.endTime = this.endTime;
                s.success = this.success;
                s.output = this.output;
                s.error = this.error;
                s.executionTimeMs = this.executionTimeMs;
                return s;
            }
        }
    }
    
    public static class DecisionRecord {
        private long timestamp;
        private String type;
        private String reasoning;
        private Map<String, Object> context;
        
        public long getTimestamp() { return timestamp; }
        public String getType() { return type; }
        public String getReasoning() { return reasoning; }
        public Map<String, Object> getContext() { return context; }
        
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public void setType(String type) { this.type = type; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private long timestamp;
            private String type;
            private String reasoning;
            private Map<String, Object> context;
            
            public Builder timestamp(long v) { this.timestamp = v; return this; }
            public Builder type(String v) { this.type = v; return this; }
            public Builder reasoning(String v) { this.reasoning = v; return this; }
            public Builder context(Map<String, Object> v) { this.context = v; return this; }
            
            public DecisionRecord build() {
                DecisionRecord d = new DecisionRecord();
                d.timestamp = this.timestamp;
                d.type = this.type;
                d.reasoning = this.reasoning;
                d.context = this.context;
                return d;
            }
        }
    }
    
    public static class TracerReport {
        private String executionId;
        private String planId;
        private String originalRequest;
        private Instant startTime;
        private Instant endTime;
        private long durationMs;
        private boolean success;
        private int eventCount;
        private int decisionCount;
        private int completedSteps;
        private int failedSteps;
        private List<Integer> criticalPath;
        
        public String getExecutionId() { return executionId; }
        public String getPlanId() { return planId; }
        public String getOriginalRequest() { return originalRequest; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return success; }
        public int getEventCount() { return eventCount; }
        public int getDecisionCount() { return decisionCount; }
        public int getCompletedSteps() { return completedSteps; }
        public int getFailedSteps() { return failedSteps; }
        public List<Integer> getCriticalPath() { return criticalPath; }
        
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setOriginalRequest(String originalRequest) { this.originalRequest = originalRequest; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setEventCount(int eventCount) { this.eventCount = eventCount; }
        public void setDecisionCount(int decisionCount) { this.decisionCount = decisionCount; }
        public void setCompletedSteps(int completedSteps) { this.completedSteps = completedSteps; }
        public void setFailedSteps(int failedSteps) { this.failedSteps = failedSteps; }
        public void setCriticalPath(List<Integer> criticalPath) { this.criticalPath = criticalPath; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private String executionId;
            private String planId;
            private String originalRequest;
            private Instant startTime;
            private Instant endTime;
            private long durationMs;
            private boolean success;
            private int eventCount;
            private int decisionCount;
            private int completedSteps;
            private int failedSteps;
            private List<Integer> criticalPath;
            
            public Builder executionId(String v) { this.executionId = v; return this; }
            public Builder planId(String v) { this.planId = v; return this; }
            public Builder originalRequest(String v) { this.originalRequest = v; return this; }
            public Builder startTime(Instant v) { this.startTime = v; return this; }
            public Builder endTime(Instant v) { this.endTime = v; return this; }
            public Builder durationMs(long v) { this.durationMs = v; return this; }
            public Builder success(boolean v) { this.success = v; return this; }
            public Builder eventCount(int v) { this.eventCount = v; return this; }
            public Builder decisionCount(int v) { this.decisionCount = v; return this; }
            public Builder completedSteps(int v) { this.completedSteps = v; return this; }
            public Builder failedSteps(int v) { this.failedSteps = v; return this; }
            public Builder criticalPath(List<Integer> v) { this.criticalPath = v; return this; }
            
            public TracerReport build() {
                TracerReport r = new TracerReport();
                r.executionId = this.executionId;
                r.planId = this.planId;
                r.originalRequest = this.originalRequest;
                r.startTime = this.startTime;
                r.endTime = this.endTime;
                r.durationMs = this.durationMs;
                r.success = this.success;
                r.eventCount = this.eventCount;
                r.decisionCount = this.decisionCount;
                r.completedSteps = this.completedSteps;
                r.failedSteps = this.failedSteps;
                r.criticalPath = this.criticalPath;
                return r;
            }
        }
    }
    
    public enum EventType {
        EXECUTION_STARTED,
        EXECUTION_COMPLETED,
        EXECUTION_FAILED,
        STEP_GROUP_STARTED,
        STEP_COMPLETED,
        STEP_FAILED,
        REPLANNING_OCCURRED,
        DECISION_MADE,
        PAUSE_OCCURRED,
        RESUME_OCCURRED
    }
}
