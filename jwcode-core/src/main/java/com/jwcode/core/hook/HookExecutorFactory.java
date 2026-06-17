package com.jwcode.core.hook;

import com.jwcode.core.hook.executor.AgentHookExecutor;
import com.jwcode.core.hook.executor.HttpHookExecutor;
import com.jwcode.core.hook.executor.PromptHookExecutor;
import com.jwcode.core.hook.executor.ShellHookExecutor;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Objects;

/**
 * HookExecutorFactory — 配置到执行器的工厂。
 *
 * <p>将 {@link HookConfig} 转换为真实的 {@link HookExecutor} 实例。
 * 解决 {@link HookRegistry.ConfiguredHookExecutor} 作为空壳适配器在运行时无法
 * 真正执行 Hook 的问题。</p>
 *
 * <h3>映射规则</h3>
 * <table>
 *   <tr><th>HookConfig 类型</th><th>生成的执行器</th><th>所需依赖</th></tr>
 *   <tr><td>SHELL</td><td>{@link ShellHookExecutor}</td><td>无</td></tr>
 *   <tr><td>HTTP</td><td>{@link HttpHookExecutor}</td><td>无</td></tr>
 *   <tr><td>PROMPT</td><td>{@link PromptHookExecutor}</td><td>{@code LlmCallback}</td></tr>
 *   <tr><td>AGENT</td><td>{@link AgentHookExecutor}</td><td>{@code AgentCallback}</td></tr>
 * </table>
 *
 * <p>如果 PROMPT/AGENT 类型的回调未提供，工厂会创建安全存根（始终 ALLOW）。</p>
 *
 * @author JWCode Team
 * @since 2.1.1
 */
public class HookExecutorFactory {

    private static final Logger logger = Logger.getLogger(HookExecutorFactory.class.getName());

    private final PromptHookExecutor.LlmCallback llmCallback;
    private final AgentHookExecutor.AgentCallback agentCallback;

    /**
     * 创建工厂（仅支持 SHELL 和 HTTP 类型的 Hook）。
     */
    public HookExecutorFactory() {
        this(null, null);
    }

    /**
     * @param llmCallback   LLM 回调（PROMPT 类型需要）
     * @param agentCallback Agent 回调（AGENT 类型需要）
     */
    public HookExecutorFactory(
            PromptHookExecutor.LlmCallback llmCallback,
            AgentHookExecutor.AgentCallback agentCallback) {
        this.llmCallback = llmCallback;
        this.agentCallback = agentCallback;
    }

    /**
     * 根据配置创建对应的 Hook 执行器。
     *
     * @param config Hook 配置
     * @return 真实的 Hook 执行器实例
     */
    public HookExecutor create(HookConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        HookImplementationType type = config.getImplementationType();

        try {
            return switch (type) {
                case SHELL -> createShell(config);
                case HTTP -> createHttp(config);
                case PROMPT -> createPrompt(config);
                case AGENT -> createAgent(config);
            };
        } catch (Exception e) {
            logger.log(Level.WARNING, "[HookExecutorFactory] Failed to create executor for '"
                + config.getName() + "': " + e.getMessage(), e);
            return createFailOpenStub(config);
        }
    }

    private HookExecutor createShell(HookConfig config) {
        if (config.getCommand() == null || config.getCommand().isBlank()) {
            throw new IllegalArgumentException(
                "SHELL hook '" + config.getName() + "' requires a command");
        }
        return new ShellHookExecutor(
            config.getName(),
            config.getCommand(),
            config.getPriority(),
            config.getTimeoutMs(),
            config.isFailOpen(),
            config.isEnabled()
        );
    }

    private HookExecutor createHttp(HookConfig config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException(
                "HTTP hook '" + config.getName() + "' requires a URL");
        }
        return new HttpHookExecutor(
            config.getName(),
            config.getUrl(),
            config.getPriority(),
            config.getTimeoutMs(),
            config.isFailOpen(),
            config.isEnabled()
        );
    }

    private HookExecutor createPrompt(HookConfig config) {
        if (llmCallback == null) {
            logger.warning("[HookExecutorFactory] LlmCallback not available "
                + "for PROMPT hook '" + config.getName()
                + "' — creating ALLOW-only stub");
            return createPromptStub(config);
        }
        return new PromptHookExecutor(
            config.getName(),
            config.getPromptTemplate(),
            llmCallback,
            config.getPriority(),
            config.getTimeoutMs(),
            config.isFailOpen(),
            config.isEnabled()
        );
    }

    private HookExecutor createAgent(HookConfig config) {
        if (agentCallback == null) {
            logger.warning("[HookExecutorFactory] AgentCallback not available "
                + "for AGENT hook '" + config.getName()
                + "' — creating ALLOW-only stub");
            return createAgentStub(config);
        }
        return new AgentHookExecutor(
            config.getName(),
            config.getAgentName(),
            agentCallback,
            config.getPriority(),
            config.getTimeoutMs(),
            config.isFailOpen(),
            config.isEnabled()
        );
    }

    // ==================== 安全存根 ====================

    private HookExecutor createPromptStub(HookConfig config) {
        return new HookExecutor() {
            @Override
            public java.util.concurrent.CompletableFuture<HookResult> execute(HookContext context) {
                logger.fine("[Hook] PROMPT stub '" + config.getName() + "' → ALLOW");
                return java.util.concurrent.CompletableFuture.completedFuture(
                    HookResult.allow(config.getName(), "LlmCallback not available"));
            }
            @Override
            public HookImplementationType getType() { return HookImplementationType.PROMPT; }
            @Override
            public String getName() { return config.getName(); }
            @Override
            public HookPriority getPriority() { return config.getPriority(); }
            @Override
            public boolean isEnabled() { return true; }
            @Override
            public long getTimeoutMs() { return config.getTimeoutMs(); }
            @Override
            public boolean isFailOpen() { return config.isFailOpen(); }
            @Override
            public boolean supportsEvent(HookEventType eventType) { return config.supportsEvent(eventType); }
            public boolean supportsTool(String toolName) { return config.matchesTool(toolName); }
        };
    }

    private HookExecutor createAgentStub(HookConfig config) {
        return new HookExecutor() {
            @Override
            public java.util.concurrent.CompletableFuture<HookResult> execute(HookContext context) {
                logger.fine("[Hook] AGENT stub '" + config.getName() + "' → ALLOW");
                return java.util.concurrent.CompletableFuture.completedFuture(
                    HookResult.allow(config.getName(), "AgentCallback not available"));
            }
            @Override
            public HookImplementationType getType() { return HookImplementationType.AGENT; }
            @Override
            public String getName() { return config.getName(); }
            @Override
            public HookPriority getPriority() { return config.getPriority(); }
            @Override
            public boolean isEnabled() { return true; }
            @Override
            public long getTimeoutMs() { return config.getTimeoutMs(); }
            @Override
            public boolean isFailOpen() { return config.isFailOpen(); }
            @Override
            public boolean supportsEvent(HookEventType eventType) { return config.supportsEvent(eventType); }
            public boolean supportsTool(String toolName) { return config.matchesTool(toolName); }
        };
    }

    private HookExecutor createFailOpenStub(HookConfig config) {
        return new HookExecutor() {
            @Override
            public java.util.concurrent.CompletableFuture<HookResult> execute(HookContext context) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                    HookResult.allow(config.getName(), "Hook creation failed, fail-open"));
            }
            @Override
            public HookImplementationType getType() { return config.getImplementationType(); }
            @Override
            public String getName() { return config.getName(); }
            @Override
            public HookPriority getPriority() { return config.getPriority(); }
            @Override
            public boolean isEnabled() { return true; }
            @Override
            public long getTimeoutMs() { return config.getTimeoutMs(); }
            @Override
            public boolean isFailOpen() { return true; }
            @Override
            public boolean supportsEvent(HookEventType eventType) { return config.supportsEvent(eventType); }
            public boolean supportsTool(String toolName) { return config.matchesTool(toolName); }
        };
    }
}
