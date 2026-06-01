package com.jwcode.core.hook;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HookEventType — 生命周期事件类型枚举（v3.0 扩展版）。
 *
 * <p>扩展至 25 种可拦截事件，对标 Claude Code 事件模型，覆盖七大类别。</p>
 *
 * <h3>事件类别分布</h3>
 * <ul>
 *   <li><b>Session（5）</b>：SESSION_START, SESSION_END, STOP, STOP_FAILURE, NOTIFICATION</li>
 *   <li><b>Tool（4）</b>：PRE_TOOL_USE, POST_TOOL_USE, POST_TOOL_USE_FAILURE, PERMISSION_DENIED</li>
 *   <li><b>Context（4）</b>：PRE_COMPACT, POST_COMPACT, PRE_CONTEXT_RESET, POST_CONTEXT_RESET</li>
 *   <li><b>StateMachine（2）</b>：STATE_TRANSITION, STATE_ENTERED</li>
 *   <li><b>Task（6）</b>：USER_PROMPT_SUBMIT, SUBAGENT_START, SUBAGENT_STOP, TASK_CREATED, TASK_COMPLETED, TEAMMATE_IDLE</li>
 *   <li><b>A2A（2）</b>：TASK_DISPATCH, A2A_REMOTE_INTERCEPT</li>
 *   <li><b>Config（2）</b>：CONFIG_CHANGE, INSTRUCTIONS_LOADED</li>
 * </ul>
 *
 * <h3>匹配器（Matcher）支持</h3>
 * <p>每个事件可携带匹配器元数据：
 * <ul>
 *   <li>{@link #getMatcherField()} — 用于过滤的字段名（如 tool_name, trigger, source）</li>
 *   <li>{@link #getMatcherValues()} — 合法匹配值列表</li>
 * </ul>
 * Claude Code 的 HookConfigManager 通过 matcher 字段将 Hook 按 (event, matcher) 二级分组，
 * 实现精细化过滤，避免无关 Hook 被不必要地执行。</p>
 *
 * @author JWCode Team
 * @since 3.0.0
 */
public enum HookEventType {

    // ==================== Session 事件 ====================

    /** 会话创建时触发。Matcher: source ∈ {startup, resume, clear, compact} */
    SESSION_START(EventCategory.SESSION, HookDecision.ALLOW, "source",
        Arrays.asList("startup", "resume", "clear", "compact")),

    /** 会话结束时触发。Matcher: reason ∈ {clear, logout, prompt_input_exit, other} */
    SESSION_END(EventCategory.SESSION, HookDecision.ALLOW, "reason",
        Arrays.asList("clear", "logout", "prompt_input_exit", "other")),

    /** Claude 即将结束响应时触发（Stop hook） */
    STOP(EventCategory.SESSION, HookDecision.ALLOW),

    /** 由于 API 错误导致 turn 结束时触发。Fire-and-forget — 输出和退出码被忽略。
     *  Matcher: error ∈ {rate_limit, authentication_failed, billing_error, invalid_request, server_error, max_output_tokens, unknown} */
    STOP_FAILURE(EventCategory.SESSION, HookDecision.ALLOW, "error",
        Arrays.asList("rate_limit", "authentication_failed", "billing_error",
            "invalid_request", "server_error", "max_output_tokens", "unknown")),

    /** 通知时触发。Matcher: notification_type ∈ {permission_prompt, idle_prompt, auth_success, elicitation_dialog} */
    NOTIFICATION(EventCategory.SESSION, HookDecision.ALLOW, "notification_type",
        Arrays.asList("permission_prompt", "idle_prompt", "auth_success",
            "elicitation_dialog", "elicitation_complete", "elicitation_response")),

    // ==================== Tool 事件 ====================

    /** 工具执行前触发。Matcher: tool_name ∈ 所有工具名 */
    PRE_TOOL_USE(EventCategory.TOOL, HookDecision.ALLOW, "tool_name", null),

    /** 工具执行成功后触发。Matcher: tool_name ∈ 所有工具名 */
    POST_TOOL_USE(EventCategory.TOOL, HookDecision.ALLOW, "tool_name", null),

    /** 工具执行失败后触发。Matcher: tool_name ∈ 所有工具名 */
    POST_TOOL_USE_FAILURE(EventCategory.TOOL, HookDecision.ALLOW, "tool_name", null),

    /** 自动模式分类器拒绝工具调用时触发。Matcher: tool_name ∈ 所有工具名 */
    PERMISSION_DENIED(EventCategory.TOOL, HookDecision.ALLOW, "tool_name", null),

    /** 权限请求对话框显示时触发。Matcher: tool_name ∈ 所有工具名 */
    PERMISSION_REQUEST(EventCategory.TOOL, HookDecision.ALLOW, "tool_name", null),

    // ==================== Context 事件 ====================

    /** 上下文压缩前触发。Matcher: trigger ∈ {manual, auto} */
    PRE_COMPACT(EventCategory.CONTEXT, HookDecision.ALLOW, "trigger",
        Arrays.asList("manual", "auto")),

    /** 上下文压缩后触发。Matcher: trigger ∈ {manual, auto} */
    POST_COMPACT(EventCategory.CONTEXT, HookDecision.ALLOW, "trigger",
        Arrays.asList("manual", "auto")),

    /** 上下文重置前触发（Context Reset 协议） */
    PRE_CONTEXT_RESET(EventCategory.CONTEXT, HookDecision.ALLOW),

    /** 上下文重置后触发（Context Reset 协议） */
    POST_CONTEXT_RESET(EventCategory.CONTEXT, HookDecision.ALLOW),

    // ==================== StateMachine 事件 ====================

    /** 状态转换前触发（TransitionGuard） */
    STATE_TRANSITION(EventCategory.STATE_MACHINE, HookDecision.ALLOW),

    /** 进入新状态后触发 */
    STATE_ENTERED(EventCategory.STATE_MACHINE, HookDecision.ALLOW),

    // ==================== Task / SubAgent 事件 ====================

    /** 用户提交提示词时触发 */
    USER_PROMPT_SUBMIT(EventCategory.TASK, HookDecision.ALLOW),

    /** 子 Agent 启动时触发。Matcher: agent_type ∈ 所有 Agent 类型 */
    SUBAGENT_START(EventCategory.TASK, HookDecision.ALLOW, "agent_type", null),

    /** 子 Agent 完成时触发。Matcher: agent_type ∈ 所有 Agent 类型 */
    SUBAGENT_STOP(EventCategory.TASK, HookDecision.ALLOW, "agent_type", null),

    /** 任务创建时触发 */
    TASK_CREATED(EventCategory.TASK, HookDecision.ALLOW),

    /** 任务完成时触发 */
    TASK_COMPLETED(EventCategory.TASK, HookDecision.ALLOW),

    /** 队友即将空闲时触发 */
    TEAMMATE_IDLE(EventCategory.TASK, HookDecision.ALLOW),

    // ==================== A2A 事件 ====================

    /** A2A 任务下发前触发 */
    TASK_DISPATCH(EventCategory.A2A, HookDecision.ALLOW),

    /** A2A 远程拦截点 */
    A2A_REMOTE_INTERCEPT(EventCategory.A2A, HookDecision.ALLOW),

    // ==================== Config 事件 ====================

    /** 配置文件变更时触发。Matcher: source ∈ {user_settings, project_settings, local_settings, policy_settings, skills} */
    CONFIG_CHANGE(EventCategory.CONFIG, HookDecision.ALLOW, "source",
        Arrays.asList("user_settings", "project_settings", "local_settings",
            "policy_settings", "skills")),

    /** 指令文件（CLAUDE.md 或 rule）加载时触发。Matcher: load_reason ∈ {session_start, nested_traversal, path_glob_match, include, compact} */
    INSTRUCTIONS_LOADED(EventCategory.CONFIG, HookDecision.ALLOW, "load_reason",
        Arrays.asList("session_start", "nested_traversal", "path_glob_match", "include", "compact")),

    // ==================== FileSystem 事件 ====================

    /** 工作目录变更后触发 */
    CWD_CHANGED(EventCategory.CONFIG, HookDecision.ALLOW),

    /** 监听的文件变更时触发 */
    FILE_CHANGED(EventCategory.CONFIG, HookDecision.ALLOW);

    // ==================== 字段 ====================

    private final EventCategory category;
    private final HookDecision defaultDecision;
    private final String matcherField;
    private final List<String> matcherValues;

    // ==================== 构造函数 ====================

    HookEventType(EventCategory category, HookDecision defaultDecision) {
        this(category, defaultDecision, null, null);
    }

    HookEventType(EventCategory category, HookDecision defaultDecision,
                  String matcherField, List<String> matcherValues) {
        this.category = category;
        this.defaultDecision = defaultDecision;
        this.matcherField = matcherField;
        this.matcherValues = matcherValues != null
            ? Collections.unmodifiableList(matcherValues)
            : Collections.emptyList();
    }

    // ==================== 查询方法 ====================

    public EventCategory getCategory() { return category; }

    public HookDecision getDefaultDecision() { return defaultDecision; }

    /**
     * 获取用于匹配器的字段名。
     * 例如 PRE_TOOL_USE 返回 "tool_name"，PRE_COMPACT 返回 "trigger"。
     * 返回 null 表示此事件不支持匹配器（所有 Hook 都会执行）。
     */
    public String getMatcherField() { return matcherField; }

    /**
     * 获取匹配器合法值列表。
     * 空列表表示值由运行时动态确定（如 tool_name 取决于注册的工具）。
     */
    public List<String> getMatcherValues() { return matcherValues; }

    /**
     * 是否支持匹配器过滤。
     */
    public boolean hasMatcher() { return matcherField != null; }

    // ==================== 分类查询 ====================

    public boolean isToolEvent() { return category == EventCategory.TOOL; }
    public boolean isStateMachineEvent() { return category == EventCategory.STATE_MACHINE; }
    public boolean isA2AEvent() { return category == EventCategory.A2A; }
    public boolean isSessionEvent() { return category == EventCategory.SESSION; }
    public boolean isContextEvent() { return category == EventCategory.CONTEXT; }
    public boolean isConfigEvent() { return category == EventCategory.CONFIG; }

    /**
     * 返回此事件是否是"即发即忘"（fire-and-forget）类型。
     * 即发即忘事件的输出和退出码被忽略，仅用于观测。
     */
    public boolean isFireAndForget() {
        return this == STOP_FAILURE || this == INSTRUCTIONS_LOADED
            || this == CWD_CHANGED || this == FILE_CHANGED;
    }

    /**
     * 返回此事件是否支持阻塞（blocking）。
     * 少数事件（如 STOP_FAILURE, INSTRUCTIONS_LOADED）忽略阻塞。
     */
    public boolean supportsBlocking() {
        return !isFireAndForget();
    }

    // ==================== 事件元数据 ====================

    /**
     * 获取事件描述摘要（用于 UI 和日志）。
     */
    public String getSummary() {
        return switch (this) {
            case SESSION_START        -> "会话创建";
            case SESSION_END          -> "会话销毁";
            case STOP                 -> "响应即将结束";
            case STOP_FAILURE         -> "响应因 API 错误结束";
            case NOTIFICATION         -> "通知发送";
            case PRE_TOOL_USE         -> "工具执行前";
            case POST_TOOL_USE        -> "工具执行后";
            case POST_TOOL_USE_FAILURE -> "工具执行失败后";
            case PERMISSION_DENIED    -> "权限拒绝后";
            case PERMISSION_REQUEST   -> "权限请求时";
            case PRE_COMPACT          -> "上下文压缩前";
            case POST_COMPACT         -> "上下文压缩后";
            case PRE_CONTEXT_RESET    -> "上下文重置前";
            case POST_CONTEXT_RESET   -> "上下文重置后";
            case STATE_TRANSITION     -> "状态转换前";
            case STATE_ENTERED        -> "进入新状态后";
            case USER_PROMPT_SUBMIT   -> "用户提交提示词";
            case SUBAGENT_START       -> "子 Agent 启动";
            case SUBAGENT_STOP        -> "子 Agent 完成";
            case TASK_CREATED         -> "任务创建";
            case TASK_COMPLETED       -> "任务完成";
            case TEAMMATE_IDLE        -> "队友空闲";
            case TASK_DISPATCH        -> "A2A 任务下发";
            case A2A_REMOTE_INTERCEPT -> "A2A 远程拦截";
            case CONFIG_CHANGE        -> "配置文件变更";
            case INSTRUCTIONS_LOADED  -> "指令文件加载";
            case CWD_CHANGED          -> "工作目录变更";
            case FILE_CHANGED         -> "监听文件变更";
        };
    }

    // ==================== 事件类别 ====================

    /**
     * 事件类别 — 用于 Hook 注册分组和快捷查询。
     */
    public enum EventCategory {
        /** 会话生命周期 */
        SESSION,
        /** 工具执行拦截 */
        TOOL,
        /** 上下文管理 */
        CONTEXT,
        /** 状态机转换 */
        STATE_MACHINE,
        /** 任务/子代理 */
        TASK,
        /** 多 Agent 协调 */
        A2A,
        /** 配置/指令变更 */
        CONFIG
    }
}
