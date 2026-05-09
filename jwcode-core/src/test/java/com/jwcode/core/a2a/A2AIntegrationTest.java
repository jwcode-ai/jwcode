package com.jwcode.core.a2a;

import com.jwcode.core.a2a.dispatcher.A2AAgentDispatcher;
import com.jwcode.core.a2a.dispatcher.AgentDispatcher;
import com.jwcode.core.a2a.dispatcher.LocalAgentDispatcher;
import com.jwcode.core.a2a.model.*;
import com.jwcode.core.a2a.server.A2AServer;
import com.jwcode.core.agent.AgentRegistry;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A2A 协议集成测试
 *
 * <p>测试 A2A 协议的核心组件：模型、调度器、服务端、门面。</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class A2AIntegrationTest {

    // ==================== 模型测试 ====================

    @Test
    @Order(1)
    @DisplayName("AgentCard 模型创建和序列化")
    void testAgentCardModel() {
        Skill skill = Skill.builder()
            .id("implement-feature")
            .description("Implement a new feature")
            .inputSchema(Map.of("files", "List<String>", "spec", "String"))
            .outputSchema(Map.of("changedFiles", "List<String>"))
            .build();

        Capabilities capabilities = Capabilities.builder()
            .streaming(true)
            .pushNotifications(false)
            .maxConcurrentTasks(3)
            .build();

        AgentCard card = AgentCard.builder()
            .name("jwcode-coder")
            .agentType("Coder")
            .description("Java/TypeScript code writing expert")
            .version("1.0.0")
            .endpointUrl("http://localhost:9101")
            .skills(List.of(skill))
            .capabilities(capabilities)
            .build();

        assertNotNull(card);
        assertEquals("jwcode-coder", card.getName());
        assertEquals("Coder", card.getAgentType());
        assertEquals(1, card.getSkills().size());
        assertTrue(card.hasSkill("implement-feature"));
        assertFalse(card.hasSkill("non-existent"));
        assertTrue(card.getCapabilities().isStreaming());
    }

    @Test
    @Order(2)
    @DisplayName("A2ATask 生命周期测试")
    void testA2ATaskLifecycle() {
        A2ATask task = A2ATask.builder()
            .taskId("test-001")
            .skillId("implement-feature")
            .description("Implement login feature")
            .input(Map.of("files", List.of("AuthController.java")))
            .status(A2ATask.TaskStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        assertEquals(A2ATask.TaskStatus.PENDING, task.getStatus());
        assertFalse(task.isTerminal());

        task.start();
        assertEquals(A2ATask.TaskStatus.RUNNING, task.getStatus());
        assertFalse(task.isTerminal());

        TaskOutput output = TaskOutput.success("Login feature implemented", Map.of("files", List.of("AuthController.java")));
        task.complete(output);
        assertEquals(A2ATask.TaskStatus.COMPLETED, task.getStatus());
        assertTrue(task.isTerminal());
        assertNotNull(task.getOutput());
        assertEquals("Login feature implemented", task.getOutput().getSummary());
    }

    @Test
    @Order(3)
    @DisplayName("A2ATask 失败和取消状态")
    void testA2ATaskFailureAndCancel() {
        A2ATask task = A2ATask.builder()
            .taskId("test-002")
            .skillId("refactor-code")
            .description("Refactor legacy code")
            .input(Collections.emptyMap())
            .status(A2ATask.TaskStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        task.start();
        task.fail("NullPointerException in legacy module");
        assertEquals(A2ATask.TaskStatus.FAILED, task.getStatus());
        assertTrue(task.isTerminal());
        assertEquals("NullPointerException in legacy module", task.getErrorMessage());

        A2ATask task2 = A2ATask.builder()
            .taskId("test-003")
            .skillId("review-code")
            .description("Review PR")
            .input(Collections.emptyMap())
            .status(A2ATask.TaskStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        task2.start();
        task2.cancel();
        assertEquals(A2ATask.TaskStatus.CANCELLED, task2.getStatus());
        assertTrue(task2.isTerminal());
    }

    @Test
    @Order(4)
    @DisplayName("A2AMessage 创建")
    void testA2AMessage() {
        A2AMessage msg = A2AMessage.builder()
            .messageId("msg-001")
            .source("Orchestrator")
            .target("Coder")
            .content("Please implement the login feature")
            .type(A2AMessage.MessageType.TASK_SUBMIT)
            .timestamp(LocalDateTime.now())
            .build();

        assertNotNull(msg);
        assertEquals("msg-001", msg.getMessageId());
        assertEquals("Orchestrator", msg.getSource());
        assertEquals("Coder", msg.getTarget());
        assertEquals(A2AMessage.MessageType.TASK_SUBMIT, msg.getType());
    }

    // ==================== 调度器测试 ====================

    @Test
    @Order(5)
    @DisplayName("LocalAgentDispatcher 基本功能")
    void testLocalAgentDispatcher() {
        AgentRegistry registry = AgentRegistry.createDefault();
        AgentDispatcher dispatcher = new LocalAgentDispatcher(registry);

        assertNotNull(dispatcher.getAvailableAgents());
        assertTrue(dispatcher.isAvailable());
        assertEquals("LocalAgentDispatcher", dispatcher.getName());

        // 查找 Agent
        AgentCard coderCard = dispatcher.findAgentByType("coder");
        assertNotNull(coderCard);
        assertEquals("coder", coderCard.getAgentType());

        AgentCard skillCard = dispatcher.findAgentBySkill("implement-feature");
        assertNotNull(skillCard);
    }

    @Test
    @Order(6)
    @DisplayName("LocalAgentDispatcher 任务提交 — 无 LLMService 时应返回失败")
    void testLocalAgentDispatcherTaskSubmit() {
        AgentRegistry registry = AgentRegistry.createDefault();
        AgentDispatcher dispatcher = new LocalAgentDispatcher(registry);

        A2ATask task = A2ATask.builder()
            .taskId("local-test-001")
            .skillId("implement-feature")
            .description("Test task")
            .input(Map.of("goal", "test"))
            .status(A2ATask.TaskStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // 没有 LLMService 时 submitTaskSync 应返回失败而非假成功
        TaskOutput output = dispatcher.submitTaskSync("Coder", task);
        assertNotNull(output);
        assertFalse(output.isSuccess(), "Without LLMService, task should fail, not silently succeed");
    }

    @Test
    @Order(7)
    @DisplayName("LocalAgentDispatcher 异步任务 — 无 LLMService 时应抛出异常")
    void testLocalAgentDispatcherAsync() throws Exception {
        AgentRegistry registry = AgentRegistry.createDefault();
        AgentDispatcher dispatcher = new LocalAgentDispatcher(registry);

        A2ATask task = A2ATask.builder()
            .taskId("local-async-001")
            .skillId("implement-feature")
            .description("Async test task")
            .input(Map.of("goal", "async test"))
            .status(A2ATask.TaskStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        CompletableFuture<TaskOutput> future = dispatcher.submitTask("Coder", task);
        // 没有 LLMService 时应抛出 IllegalStateException
        assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    // ==================== A2A 配置测试 ====================

    @Test
    @Order(8)
    @DisplayName("A2AConfig 默认配置")
    void testA2AConfigDefaults() {
        A2AConfig config = new A2AConfig();
        assertEquals(A2AConfig.DispatchMode.LOCAL, config.getMode());
        assertFalse(config.shouldUseRemote());
        assertTrue(config.shouldFallbackToLocal());
    }

    @Test
    @Order(9)
    @DisplayName("A2AConfig Builder")
    void testA2AConfigBuilder() {
        A2AConfig config = A2AConfig.builder()
            .mode(A2AConfig.DispatchMode.AUTO)
            .registryEndpoint("http://localhost:9200")
            .basePort(9200)
            .connectTimeoutSeconds(5)
            .requestTimeoutSeconds(15)
            .build();

        assertEquals(A2AConfig.DispatchMode.AUTO, config.getMode());
        assertEquals("http://localhost:9200", config.getRegistryEndpoint());
        assertEquals(9200, config.getBasePort());
        assertTrue(config.shouldUseRemote());
        assertTrue(config.shouldFallbackToLocal());
    }

    // ==================== A2A Server 测试 ====================

    private static A2AServer testServer;

    @BeforeAll
    @DisplayName("启动 A2A Server")
    static void startTestServer() {
        Skill skill = Skill.builder()
            .id("implement-feature")
            .description("Implement a feature")
            .inputSchema(Map.of("spec", "String"))
            .outputSchema(Map.of("result", "String"))
            .build();

        Capabilities capabilities = Capabilities.builder()
            .streaming(true)
            .pushNotifications(false)
            .build();

        AgentCard card = AgentCard.builder()
            .name("test-coder")
            .agentType("Coder")
            .description("Test Coder Agent")
            .version("1.0.0")
            .endpointUrl("http://localhost:9199")
            .skills(List.of(skill))
            .capabilities(capabilities)
            .build();

        testServer = new A2AServer("TestCoder", 9199, card);
        testServer.setTaskHandler(task -> {
            // 模拟任务执行
            Thread.sleep(200);
            return TaskOutput.success("Test task completed: " + task.getDescription());
        });
        testServer.start();
    }

    @AfterAll
    @DisplayName("停止 A2A Server")
    static void stopTestServer() {
        if (testServer != null) {
            testServer.stop();
        }
    }

    @Test
    @Order(10)
    @DisplayName("A2A Server 健康检查")
    void testServerHealthCheck() {
        assertNotNull(testServer);
        assertEquals("TestCoder", testServer.getAgentName());
        assertEquals(9199, testServer.getPort());
    }

    // ==================== A2A Facade 测试 ====================

    @Test
    @Order(11)
    @DisplayName("A2AFacade 本地模式")
    void testA2AFacadeLocal() {
        AgentRegistry registry = AgentRegistry.createDefault();
        A2AConfig config = A2AConfig.builder()
            .mode(A2AConfig.DispatchMode.LOCAL)
            .build();

        A2AFacade facade = new A2AFacade(registry, config);
        assertNotNull(facade);
        assertFalse(facade.isRemoteMode());
        assertEquals("LocalAgentDispatcher", facade.getDispatcherName());

        // 查找 Agent
        AgentCard coder = facade.findAgentByType("Coder");
        assertNotNull(coder);

        // 提交任务
        A2ATask task = A2ATask.builder()
            .taskId("facade-test-001")
            .skillId("implement-feature")
            .description("Facade test task")
            .input(Map.of("goal", "test"))
            .status(A2ATask.TaskStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        TaskOutput output = facade.submitTaskSync("Coder", task);
        assertNotNull(output);
    }

    @Test
    @Order(12)
    @DisplayName("A2AFacade 自动模式（远程不可用时回退本地）")
    void testA2AFacadeAutoFallback() {
        AgentRegistry registry = AgentRegistry.createDefault();
        A2AConfig config = A2AConfig.builder()
            .mode(A2AConfig.DispatchMode.AUTO)
            .registryEndpoint("http://localhost:9999") // 不存在的地址
            .build();

        A2AFacade facade = new A2AFacade(registry, config);
        assertNotNull(facade);
        // 远程不可用，应回退到本地
        assertFalse(facade.isRemoteMode());
        assertEquals("LocalAgentDispatcher", facade.getDispatcherName());
    }

    @Test
    @Order(13)
    @DisplayName("A2AFacade 关闭")
    void testA2AFacadeShutdown() {
        AgentRegistry registry = AgentRegistry.createDefault();
        A2AFacade facade = new A2AFacade(registry);
        assertDoesNotThrow(() -> facade.shutdown());
    }
}
