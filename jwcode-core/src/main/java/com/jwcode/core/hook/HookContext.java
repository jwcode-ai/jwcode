package com.jwcode.core.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HookContext — Hook 执行的上下文数据模型。
 *
 * <p>携带拦截点所需的全部上下文信息，根据事件类型携带不同字段。
 * 使用 {@link Builder} 模式构造，按事件类别提供不同的工厂方法。</p>
 *
 * <h3>公共字段（所有事件共有）</h3>
 * <ul>
 *   <li>{@code eventType} — 事件类型</li>
 *   <li>{@code sessionId} — 会话 ID</li>
 *   <li>{@code agentName} — 触发事件的 Agent 名称</li>
 *   <li>{@code timestamp} — 事件时间戳</li>
 *   <li>{@code metadata} — 扩展元数据</li>
 * </ul>
 *
 * <h3>Tool 事件专用</h3>
 * <ul>
 *   <li>{@code toolName} — 工具名称</li>
 *   <li>{@code toolInput} — 工具输入（JSON）</li>
 *   <li>{@code executionContext} — 执行上下文</li>
 *   <li>{@code toolResult} — 工具执行结果（POST 事件）</li>
 *   <li>{@code toolError} — 工具执行错误（POST_TOOL_USE_FAILURE）</li>
 * </ul>
 *
 * <h3>StateMachine 事件专用</h3>
 * <ul>
 *   <li>{@code fromState} — 转换前状态</li>
 *   <li>{@code toState} — 转换后状态</li>
 *   <li>{@code transitionReason} — 转换原因</li>
 * </ul>
 *
 * <h3>A2A 事件专用</h3>
 * <ul>
 *   <li>{@code sourceAgentName} — 发起方 Agent</li>
 *   <li>{@code targetAgentName} — 目标 Agent</li>
 *   <li>{@code taskId} — 任务 ID</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class HookContext {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== 公共字段 ====================
    private final HookEventType eventType;
    private final String sessionId;
    private final String agentName;
    private final Instant timestamp;
    private final Map<String, Object> metadata;

    // ==================== Tool 事件字段 ====================
    private final String toolName;
    private final JsonNode toolInput;
    private final ToolExecutionContext executionContext;
    private final JsonNode toolResult;
    private final String toolError;

    // ==================== StateMachine 事件字段 ====================
    private final String fromState;
    private final String toState;
    private final String transitionReason;

    // ==================== A2A 事件字段 ====================
    private final String sourceAgentName;
    private final String targetAgentName;
    private final String taskId;

    private HookContext(Builder builder) {
        this.eventType = Objects.requireNonNull(builder.eventType, "eventType");
        this.sessionId = builder.sessionId;
        this.agentName = builder.agentName;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.metadata = Collections.unmodifiableMap(
            new ConcurrentHashMap<>(builder.metadata));

        this.toolName = builder.toolName;
        this.toolInput = builder.toolInput;
        this.executionContext = builder.executionContext;
        this.toolResult = builder.toolResult;
        this.toolError = builder.toolError;

        this.fromState = builder.fromState;
        this.toState = builder.toState;
        this.transitionReason = builder.transitionReason;

        this.sourceAgentName = builder.sourceAgentName;
        this.targetAgentName = builder.targetAgentName;
        this.taskId = builder.taskId;
    }

    // ==================== Getters（公共） ====================

    public HookEventType getEventType() { return eventType; }
    public String getSessionId() { return sessionId; }
    public String getAgentName() { return agentName; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) { return (T) metadata.get(key); }

    // ==================== Getters（Tool） ====================

    public String getToolName() { return toolName; }
    public JsonNode getToolInput() { return toolInput; }
    public ToolExecutionContext getExecutionContext() { return executionContext; }
    public JsonNode getToolResult() { return toolResult; }
    public String getToolError() { return toolError; }

    // ==================== Getters（StateMachine） ====================

    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public String getTransitionReason() { return transitionReason; }

    // ==================== Getters（A2A） ====================

    public String getSourceAgentName() { return sourceAgentName; }
    public String getTargetAgentName() { return targetAgentName; }
    public String getTaskId() { return taskId; }

    // ==================== 序列化 ====================

    /**
     * 序列化为 JSON（用于 Shell/HTTP Hook 的 stdin/body）。
     */
    public JsonNode toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("eventType", eventType.name());
        if (sessionId != null) node.put("sessionId", sessionId);
        if (agentName != null) node.put("agentName", agentName);
        node.put("timestamp", timestamp.toString());
        if (toolName != null) node.put("toolName", toolName);
        if (toolInput != null) node.set("toolInput", toolInput);
        if (toolResult != null) node.set("toolResult", toolResult);
        if (toolError != null) node.put("toolError", toolError);
        if (fromState != null) node.put("fromState", fromState);
        if (toState != null) node.put("toState", toState);
        if (transitionReason != null) node.put("transitionReason", transitionReason);
        if (sourceAgentName != null) node.put("sourceAgentName", sourceAgentName);
        if (targetAgentName != null) node.put("targetAgentName", targetAgentName);
        if (taskId != null) node.put("taskId", taskId);
        if (!metadata.isEmpty()) {
            node.set("metadata", MAPPER.valueToTree(metadata));
        }
        return node;
    }

    @Override
    public String toString() {
        return String.format("HookContext{event=%s, session=%s, tool=%s, from=%s, to=%s}",
            eventType, sessionId, toolName, fromState, toState);
    }

    // ==================== Builder ====================

    public static class Builder {
        private final HookEventType eventType;
        private String sessionId;
        private String agentName;
        private Instant timestamp;
        private final Map<String, Object> metadata = new ConcurrentHashMap<>();

        // Tool
        private String toolName;
        private JsonNode toolInput;
        private ToolExecutionContext executionContext;
        private JsonNode toolResult;
        private String toolError;

        // StateMachine
        private String fromState;
        private String toState;
        private String transitionReason;

        // A2A
        private String sourceAgentName;
        private String targetAgentName;
        private String taskId;

        public Builder(HookEventType eventType) {
            this.eventType = eventType;
        }

        public Builder sessionId(String id) { this.sessionId = id; return this; }
        public Builder agentName(String name) { this.agentName = name; return this; }
        public Builder timestamp(Instant ts) { this.timestamp = ts; return this; }
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value); return this;
        }
        public Builder metadata(Map<String, Object> map) {
            this.metadata.putAll(map); return this;
        }

        // Tool builders
        public Builder toolName(String name) { this.toolName = name; return this; }
        public Builder toolInput(JsonNode input) { this.toolInput = input; return this; }
        public Builder executionContext(ToolExecutionContext ctx) { this.executionContext = ctx; return this; }
        public Builder toolResult(JsonNode result) { this.toolResult = result; return this; }
        public Builder toolError(String error) { this.toolError = error; return this; }

        // StateMachine builders
        public Builder fromState(String state) { this.fromState = state; return this; }
        public Builder toState(String state) { this.toState = state; return this; }
        public Builder transitionReason(String reason) { this.transitionReason = reason; return this; }

        // A2A builders
        public Builder sourceAgentName(String name) { this.sourceAgentName = name; return this; }
        public Builder targetAgentName(String name) { this.targetAgentName = name; return this; }
        public Builder taskId(String id) { this.taskId = id; return this; }

        public HookContext build() {
            return new HookContext(this);
        }
    }

    // ==================== 便捷工厂方法 ====================

    /** 创建 Tool 事件上下文 */
    public static HookContext forPreToolUse(String sessionId, String agentName,
                                             String toolName, JsonNode toolInput,
                                             ToolExecutionContext execCtx) {
        return new Builder(HookEventType.PRE_TOOL_USE)
            .sessionId(sessionId).agentName(agentName)
            .toolName(toolName).toolInput(toolInput)
            .executionContext(execCtx)
            .build();
    }

    /** 创建 PostToolUse 事件上下文 */
    public static HookContext forPostToolUse(String sessionId, String agentName,
                                              String toolName, JsonNode toolResult) {
        return new Builder(HookEventType.POST_TOOL_USE)
            .sessionId(sessionId).agentName(agentName)
            .toolName(toolName).toolResult(toolResult)
            .build();
    }

    /** 创建 PostToolUseFailure 事件上下文 */
    public static HookContext forPostToolUseFailure(String sessionId, String agentName,
                                                     String toolName, String toolError) {
        return new Builder(HookEventType.POST_TOOL_USE_FAILURE)
            .sessionId(sessionId).agentName(agentName)
            .toolName(toolName).toolError(toolError)
            .build();
    }

    /** 创建 StateTransition 事件上下文 */
    public static HookContext forStateTransition(String sessionId, String agentName,
                                                  String fromState, String toState,
                                                  String reason) {
        return new Builder(HookEventType.STATE_TRANSITION)
            .sessionId(sessionId).agentName(agentName)
            .fromState(fromState).toState(toState)
            .transitionReason(reason)
            .build();
    }

    /** 创建 A2A TaskDispatch 事件上下文 */
    public static HookContext forTaskDispatch(String sessionId, String sourceAgent,
                                               String targetAgent, String taskId) {
        return new Builder(HookEventType.TASK_DISPATCH)
            .sessionId(sessionId)
            .sourceAgentName(sourceAgent).targetAgentName(targetAgent)
            .taskId(taskId)
            .build();
    }

    /** 创建 Subagent 事件上下文 */
    public static HookContext forSubagent(String sessionId, String agentName,
                                           String subAgentName, HookEventType eventType) {
        return new Builder(eventType)
            .sessionId(sessionId).agentName(agentName)
            .targetAgentName(subAgentName)
            .build();
    }
}
