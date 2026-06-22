package com.jwcode.core.llm;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.llm.fragment.*;
import com.jwcode.core.llm.fragment.impl.*;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;

import java.util.logging.Logger;

/**
 * ContextFragmentInjector — 管理上下文片段的注册与注入。
 *
 * <p>从 {@link LLMQueryEngine} 中提取，负责：
 * <ul>
 *   <li>向 {@link FragmentRegistry} 注册默认内置片段</li>
 *   <li>在对话开始时通过片段注册表注入上下文片段</li>
 * </ul>
 *
 * <p>替代原来的分散注入方法（injectAgentSystemPrompt、addFileEditGuidelines、
 * injectEnvironmentInfo），统一通过片段注册表管理。
 */
public class ContextFragmentInjector {

    private static final Logger logger = Logger.getLogger(ContextFragmentInjector.class.getName());

    private final FragmentRegistry fragmentRegistry;
    private final Session session;
    private final AgentRegistry agentRegistry;
    private final ToolExecutor toolExecutor;
    private boolean fragmentsInitialized = false;

    public ContextFragmentInjector(Session session, AgentRegistry agentRegistry,
                                    ToolExecutor toolExecutor, FragmentRegistry fragmentRegistry) {
        this.session = session;
        this.agentRegistry = agentRegistry;
        this.toolExecutor = toolExecutor;
        this.fragmentRegistry = fragmentRegistry != null
            ? fragmentRegistry : FragmentRegistry.getInstance();
    }

    /**
     * 初始化默认片段 — 向 FragmentRegistry 注册内置片段。
     */
    public void initDefaultFragments() {
        if (fragmentsInitialized) return;
        fragmentRegistry.registerAll(java.util.List.of(
            new AgentRoleFragment(),
            new FileEditGuidelinesFragment(),
            new EnvironmentInfoFragment(),
            new ToolDefinitionsFragment(),
            new PermissionContextFragment()
        ));
        fragmentsInitialized = true;
        logger.info("[ContextFragmentInjector] 已注册 " + fragmentRegistry.getAllSorted().size() + " 个默认片段");
    }

    /**
     * 通过 FragmentRegistry 注入所有启用的上下文片段。
     *
     * <p>替代原来的分散注入方法，统一通过片段注册表管理。
     */
    public void injectContextFragments() {
        Agent agent = agentRegistry != null ? agentRegistry.getCurrent() : null;
        FragmentContext ctx = new FragmentContext(
            session, agent, toolExecutor, null);
        java.util.List<FragmentResult> results = fragmentRegistry.buildAndInject(ctx, session);

        if (!results.isEmpty()) {
            int totalTokens = results.stream().mapToInt(FragmentResult::tokenCount).sum();
            logger.info("[ContextFragmentInjector] 已注入 " + results.size() + " 个上下文片段，"
                + "合计 ~" + totalTokens + " tokens | "
                + results.stream()
                    .map(r -> r.fragmentId() + "(" + r.tokenCount() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
    }

    /**
     * 是否已初始化。
     */
    public boolean isInitialized() {
        return fragmentsInitialized;
    }
}
