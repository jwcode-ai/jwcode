package com.jwcode.core.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.List;

/**
 * AgentRegistry 测试
 */
public class AgentRegistryTest {
    
    @Test
    void testDefaultAgents() {
        AgentRegistry registry = AgentRegistry.createDefault();
        
        // 检查默认 agents 已注册
        Collection<Agent> agents = registry.getAll();
        assertFalse(agents.isEmpty());
        
        // 检查默认 agent
        Agent defaultAgent = registry.get("default");
        assertNotNull(defaultAgent);
        
        // 检查当前 agent
        Agent current = registry.getCurrent();
        assertNotNull(current);
    }
    
    @Test
    void testGetAgent() {
        AgentRegistry registry = AgentRegistry.createDefault();
        
        Agent coder = registry.get("coder");
        assertNotNull(coder);
        assertEquals("coder", coder.getId());
        
        Agent debug = registry.get("debug");
        assertNotNull(debug);
        assertEquals("debug", debug.getId());
    }
    
    @Test
    void testSwitchAgent() {
        AgentRegistry registry = AgentRegistry.createDefault();
        
        // 切换到 coder
        boolean success = registry.switchTo("coder");
        assertTrue(success);
        assertEquals("coder", registry.getCurrent().getId());
        
        // 切换到不存在的 agent
        boolean fail = registry.switchTo("nonexistent");
        assertFalse(fail);
    }
    
    @Test
    void testListAgentIds() {
        AgentRegistry registry = AgentRegistry.createDefault();
        
        List<String> ids = registry.listAgentIds();
        assertTrue(ids.contains("default"));
        assertTrue(ids.contains("coder"));
        assertTrue(ids.contains("debug"));
    }
    
    @Test
    void testHasAgent() {
        AgentRegistry registry = AgentRegistry.createDefault();
        
        assertTrue(registry.hasAgent("default"));
        assertTrue(registry.hasAgent("coder"));
        assertFalse(registry.hasAgent("nonexistent"));
    }
}
