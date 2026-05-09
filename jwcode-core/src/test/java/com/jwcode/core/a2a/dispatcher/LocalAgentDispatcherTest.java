package com.jwcode.core.a2a.dispatcher;

import com.jwcode.core.a2a.model.*;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * LocalAgentDispatcher 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalAgentDispatcher 单元测试")
class LocalAgentDispatcherTest {

    @Mock
    private AgentRegistry agentRegistry;
    @Mock
    private LLMService llmService;
    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private ToolExecutor toolExecutor;

    private LocalAgentDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new LocalAgentDispatcher(agentRegistry, llmService, toolRegistry, toolExecutor);
    }

    @Test
    @DisplayName("getName 应返回 LocalAgentDispatcher")
    void getName() {
        assertEquals("LocalAgentDispatcher", dispatcher.getName());
    }

    @Test
    @DisplayName("isAvailable 应返回 true")
    void isAvailable() {
        assertTrue(dispatcher.isAvailable());
    }

    @Test
    @DisplayName("getAvailableAgents 应返回注册的 Agent 列表")
    void getAvailableAgents() {
        List<AgentCard> agents = dispatcher.getAvailableAgents();
        assertNotNull(agents);
        assertFalse(agents.isEmpty(), "Should have default agents registered");

        // 验证关键 Agent 存在
        List<String> names = agents.stream().map(AgentCard::getName).toList();
        assertTrue(names.contains("Coder"));
        assertTrue(names.contains("Tester"));
        assertTrue(names.contains("Reviewer"));
        assertTrue(names.contains("Debug"));
        assertTrue(names.contains("Explorer"));
        assertTrue(names.contains("Architect"));
        assertTrue(names.contains("Documenter"));
    }

    @Test
    @DisplayName("findAgentBySkill 应返回正确的 Agent")
    void findAgentBySkill() {
        AgentCard coder = dispatcher.findAgentBySkill("implement-feature");
        assertNotNull(coder);
        assertEquals("Coder", coder.getName());

        AgentCard reviewer = dispatcher.findAgentBySkill("code-review");
        assertNotNull(reviewer);
        assertEquals("Reviewer", reviewer.getName());
    }

    @Test
    @DisplayName("findAgentBySkill 对不存在的 skill 应返回 null")
    void findAgentBySkillNotFound() {
        assertNull(dispatcher.findAgentBySkill("non-existent-skill"));
    }

    @Test
    @DisplayName("findAgentByType 应返回正确的 Agent")
    void findAgentByType() {
        AgentCard tester = dispatcher.findAgentByType("tester");
        assertNotNull(tester);
        assertEquals("Tester", tester.getName());
    }

    @Test
    @DisplayName("submitTask 对未知 Agent 应返回失败 Future")
    void submitTaskUnknownAgent() {
        A2ATask task = A2ATask.builder()
                .taskId("test-001")
                .skillId("implement-feature")
                .description("Test task")
                .build();

        CompletableFuture<TaskOutput> future = dispatcher.submitTask("UnknownAgent", task);
        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    @DisplayName("submitTask 在 LLMService 不可用时应该抛出异常")
    void submitTaskWithoutLLMService() {
        // 创建一个没有 LLMService 的 dispatcher
        LocalAgentDispatcher noLlmDispatcher = new LocalAgentDispatcher(agentRegistry);

        A2ATask task = A2ATask.builder()
                .taskId("test-002")
                .skillId("implement-feature")
                .description("Test task")
                .input(Map.of("files", List.of("src/main/Foo.java"), "spec", "Add a method"))
                .build();

        CompletableFuture<TaskOutput> future = noLlmDispatcher.submitTask("Coder", task);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(ex.getCause() instanceof IllegalStateException,
                "Should throw IllegalStateException when LLMService is null");
        assertTrue(ex.getCause().getMessage().contains("LLMService not available"),
                "Error message should mention LLMService not available");
    }

    @Test
    @DisplayName("submitTaskSync 在超时时应返回失败 TaskOutput")
    void submitTaskSyncTimeout() {
        A2ATask task = A2ATask.builder()
                .taskId("test-003")
                .skillId("implement-feature")
                .description("Test task")
                .build();

        // 使用没有 LLMService 的 dispatcher，submitTaskSync 会捕获异常
        LocalAgentDispatcher noLlmDispatcher = new LocalAgentDispatcher(agentRegistry);
        TaskOutput output = noLlmDispatcher.submitTaskSync("UnknownAgent", task);

        assertNotNull(output);
        assertFalse(output.isSuccess(), "Should return failure output");
    }

    @Test
    @DisplayName("getTaskStatus 应返回已提交的任务")
    void getTaskStatus() {
        A2ATask task = A2ATask.builder()
                .taskId("test-004")
                .skillId("implement-feature")
                .description("Test task")
                .build();

        dispatcher.submitTask("Coder", task);
        A2ATask retrieved = dispatcher.getTaskStatus("test-004");
        assertNotNull(retrieved);
        assertEquals("test-004", retrieved.getTaskId());
    }

    @Test
    @DisplayName("cancelTask 应能取消 pending 任务")
    void cancelTask() {
        A2ATask task = A2ATask.builder()
                .taskId("test-005")
                .skillId("implement-feature")
                .description("Test task")
                .build();

        dispatcher.submitTask("Coder", task);
        boolean cancelled = dispatcher.cancelTask("test-005");
        assertTrue(cancelled);
    }

    @Test
    @DisplayName("cancelTask 对不存在的任务应返回 false")
    void cancelNonExistentTask() {
        assertFalse(dispatcher.cancelTask("non-existent"));
    }

    @Test
    @DisplayName("AgentCard 应正确检测技能")
    void agentCardHasSkill() {
        AgentCard card = dispatcher.findAgentBySkill("implement-feature");
        assertNotNull(card);
        assertTrue(card.hasSkill("implement-feature"));
        assertFalse(card.hasSkill("non-existent"));
    }

    @Test
    @DisplayName("注册自定义 AgentCard")
    void registerCustomAgentCard() {
        AgentCard custom = AgentCard.builder()
                .name("CustomAgent")
                .description("Custom test agent")
                .agentType("custom")
                .skills(List.of(Skill.builder()
                        .id("custom-skill")
                        .name("Custom Skill")
                        .description("A custom skill")
                        .inputSchema(Map.of("param", "String"))
                        .outputSchema(Map.of("result", "String"))
                        .build()))
                .capabilities(Capabilities.defaultCapabilities())
                .version("1.0.0")
                .build();

        dispatcher.registerAgent(custom);
        assertNotNull(dispatcher.findAgentBySkill("custom-skill"));
        assertEquals("CustomAgent", dispatcher.findAgentByType("custom").getName());
    }
}
