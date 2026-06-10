package com.jwcode.core.llm.fragment;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;

import java.util.List;

/**
 * 片段构建上下文 — 提供片段构建所需的所有依赖。
 */
public class FragmentContext {
    private final Session session;
    private final Agent agent;
    private final ToolExecutor toolExecutor;
    private final List<String> enabledFragmentIds;
    private final String workingDirectory;

    public FragmentContext(Session session, Agent agent, ToolExecutor toolExecutor,
                           List<String> enabledFragmentIds) {
        this.session = session;
        this.agent = agent;
        this.toolExecutor = toolExecutor;
        this.enabledFragmentIds = enabledFragmentIds;
        this.workingDirectory = session != null ? session.getWorkingDirectory() : null;
    }

    public Session getSession() { return session; }
    public Agent getAgent() { return agent; }
    public ToolExecutor getToolExecutor() { return toolExecutor; }
    public List<String> getEnabledFragmentIds() { return enabledFragmentIds; }
    public String getWorkingDirectory() { return workingDirectory; }
}
