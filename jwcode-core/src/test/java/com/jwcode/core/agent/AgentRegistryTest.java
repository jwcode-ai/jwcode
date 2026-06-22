package com.jwcode.core.agent;

import com.jwcode.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentRegistryTest {

    @Test
    void registersOnlyActiveBuiltInAgents() {
        AgentRegistry registry = new AgentRegistry(ToolRegistry.createDefault());

        assertEquals(Set.of("orchestrator"), Set.copyOf(registry.listAgentIds()));
        assertNull(registry.get("default"));
        assertNull(registry.get("coder"));
    }

    @Test
    void usesOrchestratorAsEmptyFallback() {
        AgentRegistry registry = new AgentRegistry(ToolRegistry.createDefault());

        assertInstanceOf(OrchestratorAgent.class, registry.get(null));
        assertInstanceOf(OrchestratorAgent.class, registry.get(""));
    }
}
