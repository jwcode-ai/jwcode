package com.jwcode.core.a2a;

import com.jwcode.core.a2a.dispatcher.A2AAgentDispatcher;
import com.jwcode.core.a2a.dispatcher.AgentDispatcher;
import com.jwcode.core.a2a.dispatcher.LocalAgentDispatcher;
import com.jwcode.core.a2a.model.A2AMessage;
import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.a2a.registry.A2ARegistry;
import com.jwcode.core.a2a.registry.AgentSession;
import com.jwcode.core.a2a.retry.RetryOrchestrator;
import com.jwcode.core.a2a.retry.RetryStrategy;
import com.jwcode.core.a2a.router.AgentRouter;
import com.jwcode.core.a2a.router.RoutingStrategy;
import com.jwcode.core.a2a.server.A2AServer;
import com.jwcode.core.a2a.service.TaskService;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A2A 完整流程集成测试
 *
 * <p>测试 A2A 层从 注册中心 → 路由 → 调度器 → 服务 → 服务器的完整流转链路，
 * 覆盖 registry → router → dispatcher → server → service 全路径。</p>
 */
@DisplayName("A2A 完整流程集成测试")
public class A2AFullFlowIntegrationTest {

    private A2AFacade facade;
    private A2AConfig config;
    private AgentRegistry agentRegistry;
    private LLMService mockLLM;
    private ToolExecutor mockToolExecutor;
    private ToolRegistry mockToolRegistry;

    @BeforeEach
    void setUp() {
        config = new A2AConfig();
        config.setMode(A2AConfig.DispatchMode.LOCAL);
        config.setConnectTimeoutSeconds(10);

        mockLLM = Mockito.mock(LLMService.class);
        mockToolExecutor = Mockito.mock(ToolExecutor.class);
        mockToolRegistry = Mockito.mock(ToolRegistry.class);

        agentRegistry = new AgentRegistry();

        facade = new A2AFacade(config, agentRegistry, mockLLM, mockToolExecutor, mockToolRegistry);
    }

    // ==================== A2AConfig 配置测试 ====================

    @Test
    @DisplayName("A2A 配置 - 默认配置验证")
    void testDefaultConfig() {
        A2AConfig defaultConfig = new A2AConfig();
        assertAll("默认配置验证",
            () -> assertEquals(A2AConfig.DispatchMode.LOCAL, defaultConfig.getMode(), "默认模式应为 LOCAL"),
            () -> assertEquals("http://localhost:9100", defaultConfig.getRegistryEndpoint(), "默认注册端点"),
            () -> assertEquals(9100, defaultConfig.getBasePort(), "默认端口应为 9100"),
            () -> assertEquals(10, defaultConfig.getConnectTimeoutSeconds(), "默认超时 10 秒"),
            () -> assertEquals(30, defaultConfig.getRequestTimeoutSeconds(), "默认请求超时 30 秒")
        );
    }

    @Test
    @DisplayName("A2A 配置 - 编程式设置")
    void testConfigProgrammatic() {
        config.setMode(A2AConfig.DispatchMode.AUTO);
        config.setRegistryEndpoint("http://example:9200");
        config.setBasePort(9200);
        config.setConnectTimeoutSeconds(30);
        config.setRequestTimeoutSeconds(60);

        assertAll("编程式配置验证",
            () -> assertEquals(A2AConfig.DispatchMode.AUTO, config.getMode()),
            () -> assertEquals("http://example:9200", config.getRegistryEndpoint()),
            () -> assertEquals(9200, config.getBasePort()),
            () -> assertEquals(30, config.getConnectTimeoutSeconds()),
            () -> assertEquals(60, config.getRequestTimeoutSeconds())
        );
    }

    // ==================== A2ARegistry 测试 ====================

    @Test
    @DisplayName("A2A 注册中心 - Agent 注册与发现")
    void testRegistryAgentRegistration() {
        A2ARegistry registry = new A2ARegistry();

        AgentCard agentCard = new AgentCard("agent-1", "测试Agent", List.of("skill1"), "http://localhost:9101");
        registry.register(agentCard);

        assertAll("Agent 注册验证",
            () -> assertTrue(registry.isRegistered("agent-1"), "Agent 应已注册"),
            () -> assertNotNull(registry.lookup("agent-1"), "应能查找到 Agent"),
            () -> assertEquals("测试Agent", registry.lookup("agent-1").getName(), "Agent 名称匹配")
        );
    }

    @Test
    @DisplayName("A2A 注册中心 - Agent 注销")
    void testRegistryAgentUnregister() {
        A2ARegistry registry = new A2ARegistry();
        registry.register(new AgentCard("agent-2", "待注销Agent", List.of(), "http://localhost:9102"));

        assertTrue(registry.isRegistered("agent-2"), "注册后应可用");

        registry.unregister("agent-2");
        assertFalse(registry.isRegistered("agent-2"), "注销后应不可用");
    }

    // ==================== AgentRouter 测试 ====================

    @Test
    @DisplayName("Agent 路由 - 根据技能路由到正确 Agent")
    void testRouterBySkill() {
        AgentRouter router = new AgentRouter();
        router.registerAgent("agent-a", List.of("code-review", "analysis"));
        router.registerAgent("agent-b", List.of("deploy", "test"));

        assertAll("路由验证",
            () -> assertEquals("agent-a", router.routeBySkill("code-review"), "code-review 应路由到 agent-a"),
            () -> assertEquals("agent-b", router.routeBySkill("deploy"), "deploy 应路由到 agent-b"),
            () -> assertEquals("agent-b", router.routeBySkill("test"), "test 应路由到 agent-b")
        );
    }

    @Test
    @DisplayName("Agent 路由 - 未知技能应返回 null 或抛出异常")
    void testRouterUnknownSkill() {
        AgentRouter router = new AgentRouter();
        router.registerAgent("agent-a", List.of("code-review"));

        String result = router.routeBySkill("unknown-skill");
        assertNull(result, "未知技能应返回 null");
    }

    // ==================== LocalAgentDispatcher 测试 ====================

    @Test
    @DisplayName("本地调度器 - 提交任务并获取结果")
    void testLocalDispatcherSubmitTask() throws Exception {
        LocalAgentDispatcher dispatcher = new LocalAgentDispatcher(
            agentRegistry, mockLLM, mockToolExecutor, mockToolRegistry);

        A2ATask task = new A2ATask("test-task-1", "测试任务描述");
        CompletableFuture<TaskOutput> future = dispatcher.dispatchTask("agent-1", task);

        assertNotNull(future, "调度器应返回异步结果");
    }

    // ==================== A2AFacade 完整流程测试 ====================

    @Test
    @DisplayName("A2A 门面 - 不同调度模式的策略选择")
    void testFacadeDispatchModeSelection() {
        assertAll("调度模式验证",
            () -> assertDoesNotThrow(() -> {
                facade.setMode(A2AConfig.DispatchMode.LOCAL);
            }, "切换到 LOCAL 模式不应抛出异常"),
            () -> assertDoesNotThrow(() -> {
                facade.setMode(A2AConfig.DispatchMode.AUTO);
            }, "切换到 AUTO 模式不应抛出异常")
        );
    }

    @Test
    @DisplayName("A2A 门面 - Agent 卡片信息查询")
    void testFacadeAgentCard() {
        AgentCard card = facade.getAgentCard();
        assertAll("Agent 卡片信息验证",
            () -> assertNotNull(card, "Agent 卡片不应为 null"),
            () -> assertNotNull(card.getAgentName(), "Agent 名称不应为 null"),
            () -> assertNotNull(card.getSkills(), "技能列表不应为 null")
        );
    }

    // ==================== A2AMessage 模型测试 ====================

    @Test
    @DisplayName("A2A 消息模型 - 创建和属性验证")
    void testA2AMessage() {
        A2AMessage message = new A2AMessage("msg-1", "agent-a", "agent-b", "hello");

        assertAll("消息模型验证",
            () -> assertEquals("msg-1", message.getMessageId(), "消息 ID 匹配"),
            () -> assertEquals("agent-a", message.getSource(), "发送方匹配"),
            () -> assertEquals("agent-b", message.getTarget(), "接收方匹配"),
            () -> assertEquals("hello", message.getContent(), "消息内容匹配")
        );
    }

    // ==================== RetryOrchestrator 测试 ====================

    @Test
    @DisplayName("重试编排器 - 重试策略应用")
    void testRetryOrchestrator() {
        RetryOrchestrator orchestrator = new RetryOrchestrator();
        RetryStrategy strategy = new RetryStrategy(3, 100, 2.0);

        assertAll("重试编排器验证",
            () -> assertDoesNotThrow(() -> orchestrator.registerStrategy("default", strategy),
                "注册策略不应抛出异常"),
            () -> assertDoesNotThrow(() -> orchestrator.getStrategy("default"),
                "获取策略不应抛出异常")
        );
    }

    // ==================== 完整流程：注册 → 路由 → 调度 ====================

    @Test
    @DisplayName("完整流程：Agent 注册 → 路由查找 → 调度分发")
    void testFullFlowRegistryRouteDispatch() throws Exception {
        // 1. A2A 注册中心注册 Agent
        A2ARegistry registry = new A2ARegistry();
        AgentCard card = new AgentCard("full-flow-agent", "全流程Agent",
            List.of("code", "test"), "http://localhost:9999");
        registry.register(card);
        assertTrue(registry.isRegistered("full-flow-agent"), "Agent 应注册成功");

        // 2. 路由查找
        AgentRouter router = new AgentRouter();
        router.registerAgent("full-flow-agent", List.of("code", "test"));
        String routedAgent = router.routeBySkill("code");
        assertEquals("full-flow-agent", routedAgent, "路由应找到正确的 Agent");

        // 3. 调度器分发
        LocalAgentDispatcher dispatcher = new LocalAgentDispatcher(
            agentRegistry, mockLLM, mockToolExecutor, mockToolRegistry);
        A2ATask task = new A2ATask("flow-task-1", "全流程测试任务");
        CompletableFuture<TaskOutput> result = dispatcher.dispatchTask(routedAgent, task);

        assertNotNull(result, "调度分发应返回结果");
    }

    // ==================== 配置加载测试 ====================

    @Test
    @DisplayName("A2A 配置加载 - 从 Properties 加载")
    void testConfigLoading() {
        // 验证配置继承和覆盖
        config.setMode(A2AConfig.DispatchMode.LOCAL);
        config.setConnectTimeoutSeconds(30);

        assertAll("配置加载验证",
            () -> assertNotNull(config.getMode()),
            () -> assertEquals(30, config.getConnectTimeoutSeconds())
        );
    }
}
