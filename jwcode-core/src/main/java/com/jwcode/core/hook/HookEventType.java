package com.jwcode.core.hook;

/**
 * HookEventType — 生命周期事件类型枚举。
 *
 * <p>定义 12 种可拦截的生命周期事件，覆盖四大类别：</p>
 * <ul>
 *   <li><b>Session（2）</b>：会话生命周期</li>
 *   <li><b>Tool（3）</b>：工具执行拦截</li>
 *   <li><b>Context（1）</b>：上下文管理</li>
 *   <li><b>StateMachine（2）</b>：状态机转换</li>
 *   <li><b>Task（2）</b>：任务-子代理生命周期</li>
 *   <li><b>A2A（2）</b>：多 Agent 协调</li>
 * </ul>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   Orchestrator (Layer 1) → SESSION_START/END, USER_PROMPT_SUBMIT
 *   DomainAgent (Layer 2)  → SUBAGENT_START/STOP, TASK_DISPATCH
 *   ToolAgent (Layer 3)    → PRE_TOOL_USE, POST_TOOL_USE, POST_TOOL_USE_FAILURE
 *   StateMachine (横向)      → STATE_TRANSITION, STATE_ENTERED
 *   LLMQueryEngine (横向)   → PRE_COMPACT
 * </pre>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public enum HookEventType {

    // ==================== Session 事件 ====================

    /** 会话创建时触发 */
    SESSION_START(EventCategory.SESSION, HookDecision.ALLOW),

    /** 会话销毁时触发 */
    SESSION_END(EventCategory.SESSION, HookDecision.ALLOW),

    // ==================== Tool 事件 ====================

    /** 工具执行前触发（可修改入参） */
    PRE_TOOL_USE(EventCategory.TOOL, HookDecision.ALLOW),

    /** 工具执行成功后触发 */
    POST_TOOL_USE(EventCategory.TOOL, HookDecision.ALLOW),

    /** 工具执行失败后触发 */
    POST_TOOL_USE_FAILURE(EventCategory.TOOL, HookDecision.ALLOW),

    // ==================== Context 事件 ====================

    /** 上下文压缩前触发 */
    PRE_COMPACT(EventCategory.CONTEXT, HookDecision.ALLOW),

    /** 上下文重置前触发（Context Reset 协议） */
    PRE_CONTEXT_RESET(EventCategory.CONTEXT, HookDecision.ALLOW),

    /** 上下文重置后触发（Context Reset 协议） */
    POST_CONTEXT_RESET(EventCategory.CONTEXT, HookDecision.ALLOW),

    // ==================== StateMachine 事件 ====================

    /** 状态转换前触发（TransitionGuard） */
    STATE_TRANSITION(EventCategory.STATE_MACHINE, HookDecision.ALLOW),

    /** 进入新状态后触发 */
    STATE_ENTERED(EventCategory.STATE_MACHINE, HookDecision.ALLOW),

    // ==================== Task 事件 ====================

    /** 用户提交提示词时触发 */
    USER_PROMPT_SUBMIT(EventCategory.TASK, HookDecision.ALLOW),

    /** 子 Agent 启动时触发 */
    SUBAGENT_START(EventCategory.TASK, HookDecision.ALLOW),

    /** 子 Agent 完成时触发 */
    SUBAGENT_STOP(EventCategory.TASK, HookDecision.ALLOW),

    // ==================== A2A 事件 ====================

    /** A2A 任务下发前触发（可修改目标 Agent） */
    TASK_DISPATCH(EventCategory.A2A, HookDecision.ALLOW),

    /** A2A 远程拦截点 */
    A2A_REMOTE_INTERCEPT(EventCategory.A2A, HookDecision.ALLOW),

    // ==================== 通知事件 ====================

    /** 通知时触发（权限请求、空闲提醒等） */
    NOTIFICATION(EventCategory.SESSION, HookDecision.ALLOW);

    private final EventCategory category;
    private final HookDecision defaultDecision;

    HookEventType(EventCategory category, HookDecision defaultDecision) {
        this.category = category;
        this.defaultDecision = defaultDecision;
    }

    public EventCategory getCategory() {
        return category;
    }

    public HookDecision getDefaultDecision() {
        return defaultDecision;
    }

    public boolean isToolEvent() {
        return category == EventCategory.TOOL;
    }

    public boolean isStateMachineEvent() {
        return category == EventCategory.STATE_MACHINE;
    }

    public boolean isA2AEvent() {
        return category == EventCategory.A2A;
    }

    public boolean isSessionEvent() {
        return category == EventCategory.SESSION;
    }

    /**
     * 事件类别。
     */
    public enum EventCategory {
        SESSION,
        TOOL,
        CONTEXT,
        STATE_MACHINE,
        TASK,
        A2A
    }
}
