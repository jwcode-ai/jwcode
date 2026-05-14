package com.jwcode.core.hook;

/**
 * HookImplementationType — Hook 实现形态枚举。
 *
 * <p>定义 4 种 Hook 实现方式，从轻量脚本到 AI 子代理：</p>
 * <ul>
 *   <li>{@link #SHELL} — 执行外部脚本，stdin/JSON 输入，stdout/JSON 输出</li>
 *   <li>{@link #HTTP} — 调用外部 REST 端点</li>
 *   <li>{@link #PROMPT} — 发送单轮 Prompt 给 LLM 做决策</li>
 *   <li>{@link #AGENT} — 启动子代理（带 Read/Grep/Glob）做深度调查</li>
 * </ul>
 *
 * <h3>超时策略</h3>
 * <table>
 *   <tr><th>类型</th><th>默认超时</th><th>超时行为</th></tr>
 *   <tr><td>SHELL</td><td>30s</td><td>ALLOW（fail-open）</td></tr>
 *   <tr><td>HTTP</td><td>10s</td><td>ALLOW（fail-open）</td></tr>
 *   <tr><td>PROMPT</td><td>15s</td><td>ALLOW（fail-open）</td></tr>
 *   <tr><td>AGENT</td><td>60s</td><td>ALLOW（fail-open）</td></tr>
 * </table>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public enum HookImplementationType {

    /** 外部 Shell 脚本 */
    SHELL(30_000, "通过 stdin 接收 JSON 上下文，stdout 返回 JSON 决策"),

    /** HTTP REST 端点 */
    HTTP(10_000, "POST JSON 到外部端点，解析 JSON 响应"),

    /** LLM Prompt 决策 */
    PROMPT(15_000, "发送单轮 Prompt 给 LLM，AI 做动态风险评估"),

    /** AI Agent 子代理调查 */
    AGENT(60_000, "启动子代理（Read/Grep/Glob）做深度调查后返回结构化决策");

    private final long defaultTimeoutMs;
    private final String description;

    HookImplementationType(long defaultTimeoutMs, String description) {
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.description = description;
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否为本地执行（不依赖网络）。
     */
    public boolean isLocal() {
        return this == SHELL || this == PROMPT;
    }

    /**
     * 是否使用 AI 做决策。
     */
    public boolean isAIPowered() {
        return this == PROMPT || this == AGENT;
    }
}
