package com.jwcode.core.test;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.util.UUID;

/**
 * Agent 测试夹具 — 创建带有模拟 LLM 服务的测试会话。
 */
public class AgentTestFixture {

    private final String sessionId;
    private final Session session;
    private final InstrumentedLLMService llmService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentRegistry agentRegistry;

    public AgentTestFixture() {
        this(System.getProperty("user.dir"));
    }

    public AgentTestFixture(String workingDir) {
        this.sessionId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        this.session = new Session(sessionId, workingDir);
        this.llmService = new InstrumentedLLMService();
        this.toolRegistry = ToolRegistry.createDefault();
        this.agentRegistry = new AgentRegistry(toolRegistry);
        this.toolExecutor = new ToolExecutor(toolRegistry);
    }

    public Session getSession() { return session; }
    public InstrumentedLLMService getLLMService() { return llmService; }
    public AgentRegistry getAgentRegistry() { return agentRegistry; }
    public ToolExecutor getToolExecutor() { return toolExecutor; }

    /** 创建查询引擎 */
    public LLMQueryEngine createEngine() {
        return new LLMQueryEngine(session, llmService, toolExecutor,
            LLMQueryEngine.EngineConfig.defaultConfig(), agentRegistry);
    }

    /** 创建带配置的查询引擎 */
    public LLMQueryEngine createEngine(LLMQueryEngine.EngineConfig config) {
        return new LLMQueryEngine(session, llmService, toolExecutor, config, agentRegistry);
    }

    /** 预设 LLM 响应 */
    public AgentTestFixture withResponse(String text) {
        llmService.withTextResponse(text);
        return this;
    }

    public AgentTestFixture withError(String error) {
        llmService.withErrorResponse(error);
        return this;
    }

    /** 获取接收到的请求数以验证交互 */
    public int getRequestCount() {
        return llmService.getReceivedRequests().size();
    }
}
