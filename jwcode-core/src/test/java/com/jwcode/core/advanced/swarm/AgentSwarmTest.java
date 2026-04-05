package com.jwcode.core.advanced.swarm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent Swarm 测试
 */
public class AgentSwarmTest {
    
    @Test
    void testRefactorTask() {
        AgentSwarm swarm = new AgentSwarm();
        
        String task = "refactor all API calls to use async/await";
        Object context = null;
        
        AgentSwarm.SwarmExecutionResult result = swarm.executeComplexTask(task, context);
        
        assertNotNull(result);
        assertTrue(result.getSubTaskCount() > 0);
        assertTrue(result.getAgentCount() > 0);
        assertTrue(result.getDurationMs() > 0);
        assertTrue(result.getSpeedup() > 0);
        
        System.out.println("=== Agent Swarm 重构任务测试 ===");
        System.out.println(result.formatReport());
        System.out.println("\n最终结果:\n" + result.getFinalResult());
    }
    
    @Test
    void testFeatureTask() {
        AgentSwarm swarm = new AgentSwarm();
        
        String task = "implement user authentication feature";
        Object context = null;
        
        AgentSwarm.SwarmExecutionResult result = swarm.executeComplexTask(task, context);
        
        assertNotNull(result);
        assertTrue(result.getSubTaskCount() >= 4);
        
        System.out.println("\n=== Agent Swarm 功能开发测试 ===");
        System.out.println(result.formatReport());
    }
    
    @Test
    void testStats() {
        AgentSwarm swarm = new AgentSwarm();
        
        swarm.executeComplexTask("test task", null);
        
        AgentSwarm.SwarmStats stats = swarm.getStats();
        assertNotNull(stats);
        
        System.out.println("\n=== Agent Swarm 统计 ===");
        System.out.println("总 Agents: " + stats.getTotalAgents());
        System.out.println("活跃 Agents: " + stats.getActiveAgents());
        System.out.println("完成任务: " + stats.getCompletedTasks());
    }
}
