package com.jwcode.core.agent;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 系统集成测试
 *
 * <p>测试 AgentManager 的核心功能：Agent 注册、获取、状态管理、启动/停止生命周期。</p>
 */
@DisplayName("Agent 系统集成测试")
public class AgentSystemIntegrationTest {

    private AgentManager agentManager;
    private Agent mockAgent1;
    private Agent mockAgent2;

    @BeforeEach
    void setUp() {
        agentManager = new AgentManager();

        mockAgent1 = Mockito.mock(Agent.class);
        Mockito.when(mockAgent1.getId()).thenReturn("agent-1");
        Mockito.when(mockAgent1.getName()).thenReturn("TestAgent1");

        mockAgent2 = Mockito.mock(Agent.class);
        Mockito.when(mockAgent2.getId()).thenReturn("agent-2");
        Mockito.when(mockAgent2.getName()).thenReturn("TestAgent2");
    }

    @AfterEach
    void tearDown() {
        // AgentManager no explicit shutdown
    }

    @Test
    @DisplayName("注册 Agent 后可通过 ID 获取")
    void testRegisterAndGetAgent() {
        agentManager.registerAgent(mockAgent1);

        Agent retrieved = agentManager.getAgent("agent-1");
        assertNotNull(retrieved, "已注册的 Agent 应能被获取");
        assertEquals("TestAgent1", retrieved.getName());
    }

    @Test
    @DisplayName("获取不存在的 Agent 返回 null")
    void testGetNonExistentAgentReturnsNull() {
        Agent result = agentManager.getAgent("non-existent");
        assertNull(result, "不存在的 Agent 应返回 null");
    }

    @Test
    @DisplayName("获取全部已注册 Agent")
    void testGetAllAgents() {
        agentManager.registerAgent(mockAgent1);
        agentManager.registerAgent(mockAgent2);

        List<Agent> all = agentManager.getAllAgents();
        assertEquals(2, all.size(), "应返回 2 个 Agent");
    }

    @Test
    @DisplayName("取消注册后 Agent 不可获取")
    void testUnregisterAgent() {
        agentManager.registerAgent(mockAgent1);
        assertNotNull(agentManager.getAgent("agent-1"));

        agentManager.unregisterAgent("agent-1");
        assertNull(agentManager.getAgent("agent-1"));
    }

    @Test
    @DisplayName("启动和停止 Agent 生命周期")
    void testStartAndStopAgent() throws Exception {
        agentManager.registerAgent(mockAgent1);

        CompletableFuture<Boolean> startFuture = agentManager.startAgent("agent-1");
        Boolean started = startFuture.get(5, TimeUnit.SECONDS);
        assertTrue(started, "Agent 应成功启动");

        CompletableFuture<Boolean> stopFuture = agentManager.stopAgent("agent-1");
        Boolean stopped = stopFuture.get(5, TimeUnit.SECONDS);
        assertTrue(stopped, "Agent 应成功停止");
    }

    @Test
    @DisplayName("获取活跃 Agent 列表")
    void testGetActiveAgents() {
        agentManager.registerAgent(mockAgent1);
        agentManager.registerAgent(mockAgent2);

        List<Agent> active = agentManager.getActiveAgents();
        assertNotNull(active);
    }

    @Test
    @DisplayName("获取 Agent 状态")
    void testGetAgentState() {
        agentManager.registerAgent(mockAgent1);

        AgentManager.AgentState state = agentManager.getAgentState("agent-1");
        assertNotNull(state, "Agent 应有初始状态");
        assertEquals("agent-1", state.agentId);
    }

    @Test
    @DisplayName("设置和获取 Agent 状态")
    void testSetAndGetAgentState() {
        agentManager.registerAgent(mockAgent1);

        agentManager.setAgentState("agent-1", "running", null);
        AgentManager.AgentState state = agentManager.getAgentState("agent-1");
        assertEquals("running", state.status);
    }

    @Test
    @DisplayName("获取 Agent 状态历史")
    void testGetAgentStateHistory() {
        agentManager.registerAgent(mockAgent1);
        List<String> history = agentManager.getAgentStateHistory("agent-1");
        assertNotNull(history);
    }

    @Test
    @DisplayName("重复注册同一 Agent 覆盖旧对象")
    void testReRegisterAgentOverwrites() {
        agentManager.registerAgent(mockAgent1);
        Agent anotherMock = Mockito.mock(Agent.class);
        Mockito.when(anotherMock.getId()).thenReturn("agent-1");
        Mockito.when(anotherMock.getName()).thenReturn("Overwritten");

        agentManager.registerAgent(anotherMock);
        Agent retrieved = agentManager.getAgent("agent-1");
        assertEquals("Overwritten", retrieved.getName());
    }
}
