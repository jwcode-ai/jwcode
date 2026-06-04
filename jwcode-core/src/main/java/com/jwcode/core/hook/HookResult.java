package com.jwcode.core.hook;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;

/**
 * HookResult — Hook 执行后的决策结果。
 *
 * <p>携带决策语义、修改后的输入、确认载荷等，是整个 Hook 系统的
 * 核心数据载体。所有 Hook 实现必须返回此类型的实例。</p>
 *
 * <h3>字段含义</h3>
 * <ul>
 *   <li>{@code decision}：决策类型</li>
 *   <li>{@code reason}：{@code DENY} 或 {@code VOID} 时的拒绝原因</li>
 *   <li>{@code modifiedInput}：{@code MODIFY} 时携带修改后的 JSON 输入</li>
 *   <li>{@code askPayload}：{@code ASK} 时携带的确认提示信息</li>
 *   <li>{@code deferToken}：{@code DEFER} 时携带的异步令牌</li>
 *   <li>{@code rollbackAction}：{@code VOID} 时建议的回退动作</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class HookResult {

    private final HookDecision decision;
    private final String hookName;
    private final String reason;
    private final JsonNode modifiedInput;
    private final String askPayload;
    private final String deferToken;
    private final RollbackAction rollbackAction;
    private final long durationMs;
    private final Instant timestamp;
    private final String contextOutput;

    private HookResult(Builder builder) {
        this.decision = Objects.requireNonNull(builder.decision, "decision");
        this.hookName = Objects.requireNonNull(builder.hookName, "hookName");
        this.reason = builder.reason;
        this.modifiedInput = builder.modifiedInput;
        this.askPayload = builder.askPayload;
        this.deferToken = builder.deferToken;
        this.rollbackAction = builder.rollbackAction;
        this.durationMs = builder.durationMs;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.contextOutput = builder.contextOutput;
    }

    // ==================== Getters ====================

    public HookDecision getDecision() { return decision; }
    public String getHookName() { return hookName; }
    public String getReason() { return reason; }
    public JsonNode getModifiedInput() { return modifiedInput; }
    public String getAskPayload() { return askPayload; }
    public String getDeferToken() { return deferToken; }
    public RollbackAction getRollbackAction() { return rollbackAction; }
    public long getDurationMs() { return durationMs; }
    public Instant getTimestamp() { return timestamp; }
    /** Hook stdout text to inject into agent context (wrapped in XML tags). */
    public String getContextOutput() { return contextOutput; }
    public boolean hasContextOutput() { return contextOutput != null && !contextOutput.isBlank(); }

    // ==================== 工厂方法 ====================

    /** 放行 */
    public static HookResult allow(String hookName, String reason) {
        return new Builder(HookDecision.ALLOW, hookName).reason(reason).build();
    }

    /** 放行（无理由） */
    public static HookResult allow(String hookName) {
        return new Builder(HookDecision.ALLOW, hookName).build();
    }

    /** 拒绝 */
    public static HookResult deny(String hookName, String reason) {
        return new Builder(HookDecision.DENY, hookName).reason(reason).build();
    }

    /** 修改输入后放行 */
    public static HookResult modify(String hookName, JsonNode modifiedInput, String reason) {
        return new Builder(HookDecision.MODIFY, hookName)
            .modifiedInput(modifiedInput)
            .reason(reason)
            .build();
    }

    /** 请求用户确认 */
    public static HookResult ask(String hookName, String askPayload, String reason) {
        return new Builder(HookDecision.ASK, hookName)
            .askPayload(askPayload)
            .reason(reason)
            .build();
    }

    /** 延迟执行 */
    public static HookResult defer(String hookName, String deferToken, String reason) {
        return new Builder(HookDecision.DEFER, hookName)
            .deferToken(deferToken)
            .reason(reason)
            .build();
    }

    /** 取消并回退 */
    public static HookResult void_(String hookName, String reason, RollbackAction rollbackAction) {
        return new Builder(HookDecision.VOID, hookName)
            .reason(reason)
            .rollbackAction(rollbackAction)
            .build();
    }

    /** 超时/异常时的默认决策 */
    public static HookResult timeout(String hookName) {
        return new Builder(HookDecision.ALLOW, hookName)
            .reason("Hook timed out, defaulting to ALLOW (fail-open)")
            .build();
    }

    public static HookResult error(String hookName, String errorMessage) {
        return new Builder(HookDecision.ALLOW, hookName)
            .reason("Hook error: " + errorMessage + " (fail-open)")
            .build();
    }

    /** 安全级 fail-closed 错误 */
    public static HookResult errorFailClosed(String hookName, String errorMessage) {
        return new Builder(HookDecision.DENY, hookName)
            .reason("Security hook error: " + errorMessage + " (fail-closed)")
            .build();
    }

    @Override
    public String toString() {
        return String.format("HookResult{decision=%s, hook='%s', reason='%s', duration=%dms}",
            decision, hookName, reason, durationMs);
    }

    // ==================== Builder ====================

    public static class Builder {
        private final HookDecision decision;
        private final String hookName;
        private String reason;
        private JsonNode modifiedInput;
        private String askPayload;
        private String deferToken;
        private RollbackAction rollbackAction;
        private long durationMs;
        private Instant timestamp;
        private String contextOutput;

        public Builder(HookDecision decision, String hookName) {
            this.decision = decision;
            this.hookName = hookName;
        }

        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder modifiedInput(JsonNode input) { this.modifiedInput = input; return this; }
        public Builder askPayload(String payload) { this.askPayload = payload; return this; }
        public Builder deferToken(String token) { this.deferToken = token; return this; }
        public Builder rollbackAction(RollbackAction action) { this.rollbackAction = action; return this; }
        public Builder durationMs(long ms) { this.durationMs = ms; return this; }
        public Builder timestamp(Instant ts) { this.timestamp = ts; return this; }
        /** Text from hook stdout to inject into agent context as an XML-tagged block. */
        public Builder contextOutput(String output) { this.contextOutput = output; return this; }

        public HookResult build() {
            return new HookResult(this);
        }
    }
}
