package com.jwcode.core.hook;

/**
 * HookDecision — Hook 拦截决策语义。
 *
 * <p>5 种标准决策 + 1 种状态机专用决策，涵盖从放行到回退的完整拦截语义。</p>
 *
 * <h3>决策语义</h3>
 * <ul>
 *   <li>{@link #ALLOW} — 直接放行，不干预执行</li>
 *   <li>{@link #DENY} — 拒绝执行，附带原因</li>
 *   <li>{@link #ASK} — 弹窗请求用户确认（返回 {@code askPayload}）</li>
 *   <li>{@link #MODIFY} — 修改输入参数后放行（返回 {@code modifiedInput}）</li>
 *   <li>{@link #DEFER} — 延迟执行，用于异步审批流（返回 {@code deferToken}）</li>
 *   <li>{@link #VOID} — 取消当前操作并回退（仅用于 {@code STATE_TRANSITION} 事件）</li>
 * </ul>
 *
 * <h3>冲突裁决</h3>
 * <p>当多个 Hook 返回不同决策时，按以下规则裁决：
 * <ol>
 *   <li>任一 Hook 返回 {@code DENY} 或 {@code VOID} → 最终为拒绝/回退</li>
 *   <li>多个 {@code MODIFY} → 按优先级链式传递</li>
 *   <li>{@code ASK} + {@code ALLOW} → 最终需要确认</li>
 *   <li>{@code DEFER} → 等待所有审批完成</li>
 * </ol>
 * </p>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public enum HookDecision {

    /** 直接放行 */
    ALLOW,

    /** 拒绝执行，需在 {@link HookResult#getReason()} 中附带原因 */
    DENY,

    /** 弹窗请求用户确认 */
    ASK,

    /** 修改输入后放行 */
    MODIFY,

    /** 延迟执行（异步审批流） */
    DEFER,

    /**
     * 取消当前操作并回退到之前状态。
     * 仅用于 {@link HookEventType#STATE_TRANSITION} 事件。
     */
    VOID;

    /**
     * 是否为终止性决策（不再执行后续 Hook）。
     */
    public boolean isTerminal() {
        return this == DENY || this == VOID;
    }

    /**
     * 是否允许操作继续。
     */
    public boolean isPermissive() {
        return this == ALLOW || this == MODIFY;
    }

    /**
     * 是否需要用户交互。
     */
    public boolean requiresUserInteraction() {
        return this == ASK;
    }

    /**
     * 是否为异步决策。
     */
    public boolean isAsync() {
        return this == DEFER;
    }
}
