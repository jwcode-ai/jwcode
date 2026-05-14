package com.jwcode.core.hook;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HookConfig — 单个 Hook 的配置模型。
 *
 * <p>映射 {@code .jwcode/hooks.json} 中的每个 Hook 配置项。
 * 支持声明式配置，包括事件匹配、工具过滤、超时等。</p>
 *
 * <h3>配置结构</h3>
 * <pre>{@code
 * {
 *   "name": "security-audit",
 *   "description": "执行前安全审计",
 *   "events": ["PRE_TOOL_USE", "STATE_TRANSITION"],
 *   "implementation": {
 *     "type": "SHELL",
 *     "command": "python3 .jwcode/hooks/audit.py"
 *   },
 *   "priority": "SECURITY",
 *   "tools": ["BashTool", "FileWriteTool"],
 *   "matchers": {
 *     "toolNamePattern": "Bash.*|FileWrite.*"
 *   },
 *   "timeoutMs": 30000,
 *   "enabled": true,
 *   "failOpen": false
 * }
 * }</pre>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class HookConfig {

    private final String name;
    private final String description;
    private final List<HookEventType> events;
    private final HookImplementationType implementationType;
    private final String command;       // SHELL: 脚本路径
    private final String url;           // HTTP: 端点 URL
    private final String promptTemplate; // PROMPT: 提示模板
    private final String agentName;     // AGENT: 子 Agent 名称
    private final HookPriority priority;
    private final List<String> tools;
    private final Map<String, String> matchers;
    private final long timeoutMs;
    private final boolean enabled;
    private final boolean failOpen;

    private HookConfig(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.description = builder.description != null ? builder.description : "";
        this.events = Collections.unmodifiableList(
            builder.events != null ? List.copyOf(builder.events) : List.of());
        this.implementationType = Objects.requireNonNull(builder.implementationType, "implementationType");
        this.command = builder.command;
        this.url = builder.url;
        this.promptTemplate = builder.promptTemplate;
        this.agentName = builder.agentName;
        this.priority = builder.priority != null ? builder.priority : HookPriority.USER;
        this.tools = Collections.unmodifiableList(
            builder.tools != null ? List.copyOf(builder.tools) : List.of());
        this.matchers = Collections.unmodifiableMap(
            builder.matchers != null ? Map.copyOf(builder.matchers) : Map.of());
        this.timeoutMs = builder.timeoutMs > 0 ? builder.timeoutMs
            : implementationType.getDefaultTimeoutMs();
        this.enabled = builder.enabled;
        this.failOpen = builder.failOpen;
    }

    // ==================== Getters ====================

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<HookEventType> getEvents() { return events; }
    public HookImplementationType getImplementationType() { return implementationType; }
    public String getCommand() { return command; }
    public String getUrl() { return url; }
    public String getPromptTemplate() { return promptTemplate; }
    public String getAgentName() { return agentName; }
    public HookPriority getPriority() { return priority; }
    public List<String> getTools() { return tools; }
    public Map<String, String> getMatchers() { return matchers; }
    public long getTimeoutMs() { return timeoutMs; }
    public boolean isEnabled() { return enabled; }
    public boolean isFailOpen() { return failOpen; }

    /**
     * 是否支持指定事件。
     */
    public boolean supportsEvent(HookEventType eventType) {
        return events.isEmpty() || events.contains(eventType);
    }

    /**
     * 是否匹配指定工具名称。
     */
    public boolean matchesTool(String toolName) {
        if (tools.isEmpty() && !matchers.containsKey("toolNamePattern")) {
            return true; // 不限制工具
        }
        if (tools.contains(toolName)) {
            return true;
        }
        String pattern = matchers.get("toolNamePattern");
        if (pattern != null && toolName != null) {
            return toolName.matches(pattern);
        }
        return false;
    }

    /**
     * 是否匹配指定状态转换。
     */
    public boolean matchesStateTransition(String fromState, String toState) {
        String fromPattern = matchers.get("fromState");
        String toPattern = matchers.get("toState");
        boolean fromMatch = fromPattern == null || fromPattern.equals("*")
            || fromPattern.equals(fromState);
        boolean toMatch = toPattern == null || toPattern.equals("*")
            || toPattern.equals(toState);
        return fromMatch && toMatch;
    }

    @Override
    public String toString() {
        return String.format("HookConfig{name='%s', type=%s, priority=%s, events=%s}",
            name, implementationType, priority, events);
    }

    // ==================== Builder ====================

    public static class Builder {
        private String name;
        private String description;
        private List<HookEventType> events;
        private HookImplementationType implementationType;
        private String command;
        private String url;
        private String promptTemplate;
        private String agentName;
        private HookPriority priority = HookPriority.USER;
        private List<String> tools;
        private Map<String, String> matchers;
        private long timeoutMs;
        private boolean enabled = true;
        private boolean failOpen = true;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder events(List<HookEventType> events) { this.events = events; return this; }
        public Builder implementationType(HookImplementationType type) { this.implementationType = type; return this; }
        public Builder command(String cmd) { this.command = cmd; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder promptTemplate(String template) { this.promptTemplate = template; return this; }
        public Builder agentName(String name) { this.agentName = name; return this; }
        public Builder priority(HookPriority priority) { this.priority = priority; return this; }
        public Builder tools(List<String> tools) { this.tools = tools; return this; }
        public Builder matchers(Map<String, String> matchers) { this.matchers = matchers; return this; }
        public Builder timeoutMs(long ms) { this.timeoutMs = ms; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder failOpen(boolean failOpen) { this.failOpen = failOpen; return this; }

        public HookConfig build() {
            return new HookConfig(this);
        }
    }

    // ==================== 内置 Hook 配置工厂 ====================

    /** 内置：Token 预算保护 Hook */
    public static HookConfig builtinBudgetGuard() {
        return new Builder()
            .name("builtin-budget-guard")
            .description("Token 预算保护 — 预算耗尽时拒绝危险工具调用")
            .implementationType(HookImplementationType.PROMPT)
            .priority(HookPriority.SYSTEM)
            .events(List.of(HookEventType.PRE_TOOL_USE, HookEventType.PRE_COMPACT))
            .tools(List.of("BashTool", "FileWriteTool", "FileEditTool"))
            .build();
    }

    /** 内置：工作区安全边界 Hook */
    public static HookConfig builtinWorkspaceGuard() {
        return new Builder()
            .name("builtin-workspace-guard")
            .description("工作区安全边界 — 拒绝工作区外的文件操作")
            .implementationType(HookImplementationType.SHELL)
            .priority(HookPriority.SECURITY)
            .events(List.of(HookEventType.PRE_TOOL_USE))
            .tools(List.of("FileReadTool", "FileWriteTool", "FileEditTool",
                "BashTool", "BatchReadTool"))
            .build();
    }
}
