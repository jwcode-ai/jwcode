package com.jwcode.core.hook;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * StateTransitionEvent — 状态机转换事件模型。
 *
 * <p>与 {@code MainAgentStateMachine.StateTransition} 记录不同：
 * 此类是 Hook 层的<strong>可决策</strong>事件，携带转换上下文和检查点快照，
 * 供 TransitionGuard 使用。</p>
 *
 * <h3>与 HookContext 的关系</h3>
 * <p>
 * 当 {@link HookEventType#STATE_TRANSITION} 触发时，
 * {@link HookContext} 的 {@code fromState}/{@code toState}/{@code transitionReason}
 * 字段由此类填充。
 * </p>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class StateTransitionEvent {

    private final String sessionId;
    private final String fromState;
    private final String toState;
    private final String reason;
    private final String taskId;
    private final String checkpointId;
    private final Map<String, Object> metadata;
    private final Instant timestamp;

    public StateTransitionEvent(String sessionId, String fromState, String toState,
                                 String reason, String taskId, String checkpointId,
                                 Map<String, Object> metadata) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.fromState = Objects.requireNonNull(fromState, "fromState");
        this.toState = Objects.requireNonNull(toState, "toState");
        this.reason = reason;
        this.taskId = taskId;
        this.checkpointId = checkpointId;
        this.metadata = metadata != null
            ? Collections.unmodifiableMap(Map.copyOf(metadata))
            : Collections.emptyMap();
        this.timestamp = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public String getReason() { return reason; }
    public String getTaskId() { return taskId; }
    public String getCheckpointId() { return checkpointId; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getTimestamp() { return timestamp; }

    /**
     * 是否涉及终止状态。
     */
    public boolean isTerminalTransition() {
        return "IDLE".equals(toState) || "COMPLETED".equals(toState)
            || "FAILED".equals(toState) || "CANCELED".equals(toState);
    }

    /**
     * 是否为关键转换（需要强制 Hook 审批）。
     */
    public boolean isCriticalTransition() {
        // EXECUTING → REVIEWING 和 REVIEWING → IDLE 是关键转换
        return ("EXECUTING".equals(fromState) && "REVIEWING".equals(toState))
            || ("REVIEWING".equals(fromState) && "IDLE".equals(toState));
    }

    /**
     * 是否为需要自动保存检查点的转换。
     */
    public boolean requiresCheckpoint() {
        // 任何离开执行状态的转换都应自动保存
        return "EXECUTING".equals(fromState)
            || "PLANNING".equals(fromState);
    }

    @Override
    public String toString() {
        return String.format("StateTransition{%s → %s, reason='%s', task=%s}",
            fromState, toState, reason, taskId);
    }
}
