package com.jwcode.core.agent;

import org.junit.jupiter.api.*;

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
    private AgentManager.Agent agent1;
    private AgentManager.Agent agent2;

    @BeforeEach
    void setUp() {
        agentManager = new AgentManager();

        agent1 = new AgentManager.Agent("agent-1", "TestAgent1", AgentManager.AgentType.GENERAL);
        agent2 = new AgentManager.Agent("agent-2", "TestAgent2", AgentManager.AgentType.CODING);
    }

    @AfterEach
    void tearDown() {
        // AgentManager no explicit shutdown
    }

    @Test
    @DisplayName("注册 Agent 后可通过 ID 获取")
    void testRegisterAndGetAgent() {
        agentManager.registerAgent(agent1);

        AgentManager.Agent retrieved = agentManager.getAgent("agent-1");
        assertNotNull(retrieved, "已注册的 Agent 应能被获取");
        assertEquals("TestAgent1", retrieved.name);
    }

    @Test
    @DisplayName("获取不存在的 Agent 返回 null")
    void testGetNonExistentAgentReturnsNull() {
        AgentManager.Agent result = agentManager.getAgent("non-existent");
        assertNull(result, "不存在的 Agent 应返回 null");
    }

    @Test
    @DisplayName("获取全部已注册 Agent")
    void testGetAllAgents() {
        agentManager.registerAgent(agent1);
        agentManager.registerAgent(agent2);

        List<AgentManager.Agent> all = agentManager.getAllAgents();
        // AgentManager 默认有 5 个内置 Agent + 新注册的 2 个 = 7
        assertEquals(7, all.size(), "应返回 7 个 Agent（5 内置 + 2 新注册）");
    }

    @Test
    @DisplayName("取消注册后 Agent 不可获取")
    void testUnregisterAgent() {
        agentManager.registerAgent(agent1);
        assertNotNull(agentManager.getAgent("agent-1"));

        agentManager.unregisterAgent("agent-1");
        assertNull(agentManager.getAgent("agent-1"));
    }

    @Test
    @DisplayName("启动和停止 Agent 生命周期")
    void testStartAndStopAgent() throws Exception {
        agentManager.registerAgent(agent1);

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
        agentManager.registerAgent(agent1);
        agentManager.registerAgent(agent2);

        List<AgentManager.Agent> active = agentManager.getActiveAgents();
        assertNotNull(active);
    }

    @Test
    @DisplayName("获取 Agent 状态")
    void testGetAgentState() {
        agentManager.registerAgent(agent1);

        AgentManager.AgentState state = agentManager.getAgentState("agent-1");
        assertNotNull(state, "Agent 应有初始状态");
        assertEquals("agent-1", state.agentId);
    }

    @Test
    @DisplayName("设置和获取 Agent 状态")
    void testSetAndGetAgentState() {
        agentManager.registerAgent(agent1);

        agentManager.setAgentState("agent-1", "running", null);
        AgentManager.AgentState state = agentManager.getAgentState("agent-1");
        assertEquals("running", state.status);
    }

    @Test
    @DisplayName("获取 Agent 状态历史")
    void testGetAgentStateHistory() {
        agentManager.registerAgent(agent1);
        List<String> history = agentManager.getAgentStateHistory("agent-1");
        assertNotNull(history);
    }

    @Test
    @DisplayName("重复注册同一 Agent 覆盖旧对象")
    void testReRegisterAgentOverwrites() {
        agentManager.registerAgent(agent1);
        AgentManager.Agent overwritten = new AgentManager.Agent("agent-1", "Overwritten", AgentManager.AgentType.GENERAL);

        agentManager.registerAgent(overwritten);
        AgentManager.Agent retrieved = agentManager.getAgent("agent-1");
        assertEquals("Overwritten", retrieved.name);
    }

    @Test
    @DisplayName("清除 Agent 状态历史")
    void testClearAgentStateHistory() {
        agentManager.registerAgent(agent1);
        agentManager.setAgentState("agent-1", "running", null);
        agentManager.clearAgentStateHistory("agent-1");

        List<String> history = agentManager.getAgentStateHistory("agent-1");
        assertTrue(history.isEmpty(), "清除后状态历史应为空");
    }

    @Test
    @DisplayName("AgentManager 带监听器构造")
    void testAgentManagerWithListener() {
        AgentManager.AgentListener listener = new AgentManager.AgentListener() {
            @Override public void onAgentRegistered(AgentManager.Agent agent) {}
            @Override public void onAgentUnregistered(AgentManager.Agent agent) {}
            @Override public void onAgentStarted(AgentManager.Agent agent) {}
            @Override public void onAgentStopped(AgentManager.Agent agent) {}
            @Override public void onAgentError(AgentManager.Agent agent, Throwable error) {}
        };
        AgentManager am = new AgentManager(listener);
        assertNotNull(am);
    }
}
