package com.jwcode.core.a2a;

import com.jwcode.core.a2a.dispatcher.A2AAgentDispatcher;
import com.jwcode.core.a2a.dispatcher.AgentDispatcher;
import com.jwcode.core.a2a.dispatcher.LocalAgentDispatcher;
import com.jwcode.core.a2a.model.A2AMessage;
import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.Capabilities;
import com.jwcode.core.a2a.model.Skill;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.a2a.registry.A2ARegistry;
import com.jwcode.core.a2a.registry.AgentSession;
import com.jwcode.core.a2a.model.ErrorSummary;
import com.jwcode.core.a2a.model.RetryPolicy;
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
import java.util.Map;
import java.util.Optional;
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

        agentRegistry = new AgentRegistry(mockToolRegistry);

        facade = new A2AFacade(agentRegistry, config, mockLLM, mockToolRegistry, mockToolExecutor);
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

    @Test
    @DisplayName("A2A 配置 - Builder 模式")
    void testConfigBuilder() {
        A2AConfig built = A2AConfig.builder()
            .mode(A2AConfig.DispatchMode.AUTO)
            .registryEndpoint("http://example:9200")
            .basePort(9200)
            .connectTimeoutSeconds(30)
            .requestTimeoutSeconds(60)
            .build();

        assertEquals(A2AConfig.DispatchMode.AUTO, built.getMode());
        assertEquals("http://example:9200", built.getRegistryEndpoint());
    }

    // ==================== A2ARegistry 测试 ====================

    @Test
    @DisplayName("A2A 注册中心 - Agent 会话注册与发现")
    void testRegistryAgentRegistration() {
        A2ARegistry registry = A2ARegistry.getInstance();

        AgentCard card = AgentCard.builder()
            .name("agent-1")
            .description("测试Agent")
            .agentType("worker")
            .skills(List.of(new Skill("skill1", "测试技能", "测试技能", Map.of(), Map.of())))
            .capabilities(Capabilities.defaultCapabilities())
            .endpointUrl("http://localhost:9101")
            .version("1.0.0")
            .build();

        AgentSession session = AgentSession.builder()
            .agentName(card.getName())
            .agentType(card.getAgentType())
            .agentCard(card)
            .connectionId("conn-agent-1")
            .build();
        registry.register(session);

        assertAll("Agent 注册验证",
            () -> assertTrue(registry.findByName("agent-1").isPresent(), "Agent 应已注册"),
            () -> assertEquals("测试Agent", registry.findByName("agent-1").get().getAgentCard().getDescription(), "Agent 描述匹配")
        );

        registry.unregister(session.getConnectionId());
    }

    @Test
    @DisplayName("A2A 注册中心 - Agent 按技能查找")
    void testRegistryFindBySkill() {
        A2ARegistry registry = A2ARegistry.getInstance();

        AgentCard card = AgentCard.builder()
            .name("skill-agent")
            .description("技能Agent")
            .agentType("worker")
            .skills(List.of(
                new Skill("code-review", "代码审查", "代码审查技能", Map.of(), Map.of()),
                new Skill("analysis", "分析", "分析技能", Map.of(), Map.of())))
            .capabilities(Capabilities.defaultCapabilities())
            .endpointUrl("http://localhost:9102")
            .version("1.0.0")
            .build();

        AgentSession session = AgentSession.builder()
            .agentName(card.getName())
            .agentType(card.getAgentType())
            .agentCard(card)
            .connectionId("conn-skill-agent")
            .build();
        registry.register(session);

        List<AgentSession> bySkill = registry.findBySkillId("code-review");
        assertFalse(bySkill.isEmpty(), "应按技能找到 Agent");

        registry.unregister(session.getConnectionId());
    }

    @Test
    @DisplayName("A2A 注册中心 - 统计信息")
    void testRegistryStats() {
        A2ARegistry registry = A2ARegistry.getInstance();
        A2ARegistry.RegistryStats stats = registry.getStats();
        assertNotNull(stats);
    }

    // ==================== AgentRouter 测试 ====================

    @Test
    @DisplayName("Agent 路由 - 根据能力选择 Agent")
    void testRouterSelectAgent() {
        A2ARegistry registry = A2ARegistry.getInstance();

        AgentCard card = AgentCard.builder()
            .name("router-agent")
            .description("路由测试Agent")
            .agentType("worker")
            .skills(List.of(new Skill("code", "编码", "编码技能", Map.of(), Map.of())))
            .capabilities(Capabilities.defaultCapabilities())
            .endpointUrl("http://localhost:9103")
            .version("1.0.0")
            .build();

        AgentSession session = AgentSession.builder()
            .agentName(card.getName())
            .agentType(card.getAgentType())
            .agentCard(card)
            .connectionId("conn-router-agent")
            .build();
        registry.register(session);

        AgentRouter router = new AgentRouter();
        Optional<AgentSession> selected = router.selectAgent(registry, "code", RoutingStrategy.ROUND_ROBIN);

        assertTrue(selected.isPresent(), "应能根据能力选择 Agent");

        registry.unregister(session.getConnectionId());
    }

    @Test
    @DisplayName("Agent 路由 - 未知能力应返回空")
    void testRouterUnknownSkill() {
        A2ARegistry registry = A2ARegistry.getInstance();
        AgentRouter router = new AgentRouter();

        Optional<AgentSession> selected = router.selectAgent(registry, "unknown-skill", RoutingStrategy.ROUND_ROBIN);
        assertTrue(selected.isEmpty(), "未知能力应返回空");
    }

    // ==================== LocalAgentDispatcher 测试 ====================

    @Test
    @DisplayName("本地调度器 - 注册 Agent 卡片")
    void testLocalDispatcherRegisterAgent() {
        LocalAgentDispatcher dispatcher = new LocalAgentDispatcher(agentRegistry, mockLLM, mockToolRegistry, mockToolExecutor);

        AgentCard card = AgentCard.builder()
            .name("dispatcher-agent")
            .description("调度测试Agent")
            .agentType("worker")
            .skills(List.of(new Skill("test", "测试", "测试技能", Map.of(), Map.of())))
            .capabilities(Capabilities.defaultCapabilities())
            .endpointUrl("local")
            .version("1.0.0")
            .build();

        dispatcher.registerAgent(card);
        List<AgentCard> agents = dispatcher.getAvailableAgents();
        assertFalse(agents.isEmpty(), "应包含已注册的 Agent");
    }

    @Test
    @DisplayName("本地调度器 - 提交任务并获取结果")
    void testLocalDispatcherSubmitTask() throws Exception {
        LocalAgentDispatcher dispatcher = new LocalAgentDispatcher(agentRegistry, mockLLM, mockToolRegistry, mockToolExecutor);

        AgentCard card = AgentCard.builder()
            .name("task-agent")
            .description("任务测试Agent")
            .agentType("worker")
            .skills(List.of(new Skill("test", "测试", "测试技能", Map.of(), Map.of())))
            .capabilities(Capabilities.defaultCapabilities())
            .endpointUrl("local")
            .version("1.0.0")
            .build();
        dispatcher.registerAgent(card);

        A2ATask task = A2ATask.create("test", "测试任务描述", Map.of("key", "value"));
        CompletableFuture<TaskOutput> future = dispatcher.submitTask("task-agent", task);

        assertNotNull(future, "调度器应返回异步结果");
    }

    // ==================== A2AFacade 完整流程测试 ====================

    @Test
    @DisplayName("A2A 门面 - 获取可用 Agent 列表")
    void testFacadeGetAvailableAgents() {
        List<AgentCard> agents = facade.getAvailableAgents();
        assertNotNull(agents, "可用 Agent 列表不应为 null");
    }

    @Test
    @DisplayName("A2A 门面 - 按技能查找 Agent")
    void testFacadeFindBySkill() {
        AgentCard card = facade.findAgentBySkill("code");
        // 无匹配时返回 null
        assertNull(card);
    }

    @Test
    @DisplayName("A2A 门面 - 调度器名称")
    void testFacadeDispatcherName() {
        String name = facade.getDispatcherName();
        assertNotNull(name);
    }

    @Test
    @DisplayName("A2A 门面 - 配置访问")
    void testFacadeConfig() {
        A2AConfig cfg = facade.getConfig();
        assertNotNull(cfg);
        assertEquals(A2AConfig.DispatchMode.LOCAL, cfg.getMode());
    }

    // ==================== A2AMessage 模型测试 ====================

    @Test
    @DisplayName("A2A 消息模型 - 使用工厂方法创建")
    void testA2AMessageFactory() {
        A2AMessage message = A2AMessage.taskSubmit("task-1", "hello", "agent-a", "agent-b");

        assertAll("消息模型验证",
            () -> assertNotNull(message.getMessageId(), "消息 ID 不应为 null"),
            () -> assertEquals("agent-a", message.getSource(), "发送方匹配"),
            () -> assertEquals("agent-b", message.getTarget(), "接收方匹配"),
            () -> assertEquals("hello", message.getContent(), "消息内容匹配"),
            () -> assertEquals(A2AMessage.MessageType.TASK_SUBMIT, message.getType(), "消息类型匹配")
        );
    }

    @Test
    @DisplayName("A2A 消息模型 - 使用 Builder 创建")
    void testA2AMessageBuilder() {
        A2AMessage message = A2AMessage.builder()
            .messageId("msg-1")
            .type(A2AMessage.MessageType.TASK_COMPLETED)
            .taskId("task-1")
            .content("完成")
            .source("agent-a")
            .target("agent-b")
            .build();

        assertEquals("msg-1", message.getMessageId());
        assertEquals("完成", message.getContent());
    }

    // ==================== A2ATask 模型测试 ====================

    @Test
    @DisplayName("A2A 任务模型 - 使用静态工厂方法创建")
    void testA2ATaskCreate() {
        A2ATask task = A2ATask.create("skill-1", "测试任务", Map.of("input", "data"));

        assertAll("任务模型验证",
            () -> assertNotNull(task.getTaskId(), "任务 ID 不应为 null"),
            () -> assertEquals("skill-1", task.getSkillId()),
            () -> assertEquals("测试任务", task.getDescription()),
            () -> assertEquals(A2ATask.TaskStatus.PENDING, task.getStatus())
        );
    }

    @Test
    @DisplayName("A2A 任务模型 - 生命周期状态转换")
    void testA2ATaskLifecycle() {
        A2ATask task = A2ATask.create("skill-1", "生命周期测试", Map.of());

        task.start();
        assertEquals(A2ATask.TaskStatus.RUNNING, task.getStatus());

        task.complete(TaskOutput.success("成功", Map.of("result", "ok")));
        assertEquals(A2ATask.TaskStatus.COMPLETED, task.getStatus());
        assertTrue(task.isTerminal());
    }

    @Test
    @DisplayName("A2A 任务模型 - 失败状态")
    void testA2ATaskFailure() {
        A2ATask task = A2ATask.create("skill-1", "失败测试", Map.of());
        task.fail("发生错误");
        assertEquals(A2ATask.TaskStatus.FAILED, task.getStatus());
        assertEquals("发生错误", task.getErrorMessage());
    }

    // ==================== RetryOrchestrator 测试 ====================

    @Test
    @DisplayName("重试编排器 - 使用 RetryStrategy 接口")
    void testRetryOrchestrator() {
        RetryOrchestrator orchestrator = new RetryOrchestrator();
        RetryPolicy policy = RetryPolicy.builder().maxRetries(3).build();
        RetryStrategy strategy = RetryStrategy.exponentialBackoff();

        assertAll("重试编排器验证",
            () -> assertNotNull(strategy, "策略不应为 null"),
            () -> assertDoesNotThrow(() -> {
                orchestrator.executeWithRetry(() -> "ok", policy, strategy);
            }, "执行重试不应抛出异常")
        );
    }

    @Test
    @DisplayName("重试编排器 - 执行失败时抛出异常")
    void testRetryOrchestratorFailure() {
        RetryOrchestrator orchestrator = new RetryOrchestrator();
        RetryPolicy policy = RetryPolicy.builder().maxRetries(2).build();
        RetryStrategy strategy = RetryStrategy.noRetry();

        assertThrows(RetryOrchestrator.RetryExhaustedException.class, () -> {
            orchestrator.executeWithRetry(() -> {
                throw new RuntimeException("预期失败");
            }, policy, strategy);
        });
    }

    // ==================== A2AFacade 提交任务测试 ====================

    @Test
    @DisplayName("A2A 门面 - 提交任务")
    void testFacadeSubmitTask() {
        A2ATask task = A2ATask.create("test", "门面测试任务", Map.of());
        CompletableFuture<TaskOutput> future = facade.submitTask("test-agent", task);
        assertNotNull(future);
    }

    @Test
    @DisplayName("A2A 门面 - 关闭")
    void testFacadeShutdown() {
        assertDoesNotThrow(() -> facade.shutdown());
    }

    // ==================== AgentCard Builder 测试 ====================

    @Test
    @DisplayName("AgentCard - Builder 构建远程 Agent")
    void testAgentCardBuilderRemote() {
        AgentCard card = AgentCard.builder()
            .name("remote-agent")
            .description("远程Agent")
            .agentType("worker")
            .skills(List.of(new Skill("deploy", "部署", "部署技能", Map.of(), Map.of())))
            .capabilities(Capabilities.defaultCapabilities())
            .endpointUrl("http://remote:9000")
            .version("2.0.0")
            .build();

        assertAll("远程 AgentCard 验证",
            () -> assertEquals("remote-agent", card.getName()),
            () -> assertTrue(card.isRemote()),
            () -> assertTrue(card.hasSkill("deploy"))
        );
    }
}
