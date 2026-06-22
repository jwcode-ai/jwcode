package com.jwcode.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registry for built-in and configured agents.
 */
public class AgentRegistry {

    private static final Logger logger = Logger.getLogger(AgentRegistry.class.getName());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;
    private Agent currentAgent;

    public AgentRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        registerDefaultAgents();
    }

    private void registerDefaultAgents() {
        register(new OrchestratorAgent());

        currentAgent = get("orchestrator");
    }

    public void register(Agent agent) {
        if (agents.containsKey(agent.getId())) {
            logger.fine("Agent already registered, skipping: " + agent.getId());
            return;
        }
        agents.put(agent.getId(), agent);
        logger.info("Registered Agent: " + agent.getId());
    }

    public void loadFromFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("Agent definition file does not exist: " + path);
        }

        AgentDefinition definition = JSON_MAPPER.readValue(file, AgentDefinition.class);
        register(createAgentFromDefinition(definition));
    }

    private Agent createAgentFromDefinition(AgentDefinition def) {
        return new ConfigurableAgent(
            def.getId(),
            def.getName(),
            def.getDescription(),
            def.getSystemPrompt(),
            resolveTools(def.getTools()),
            def.getConfig(),
            convertModelConfig(def.getModel()),
            def.getParent(),
            def.getDisallowedTools()
        );
    }

    private List<Tool<?, ?, ?>> resolveTools(List<String> toolNames) {
        List<Tool<?, ?, ?>> tools = new ArrayList<>();
        if (toolNames == null) {
            return tools;
        }

        for (String name : toolNames) {
            Tool<?, ?, ?> tool = toolRegistry.getTool(name);
            if (tool != null) {
                tools.add(tool);
            } else {
                logger.warning("Tool does not exist: " + name);
            }
        }
        return tools;
    }

    private Agent.ModelConfig convertModelConfig(AgentDefinition.ModelConfig model) {
        if (model == null) {
            return null;
        }
        return new Agent.ModelConfig(
            model.getName(),
            model.getTemperature(),
            model.getMaxTokens()
        );
    }

    public Agent get(String id) {
        if (id == null || id.isBlank()) {
            return agents.get("orchestrator");
        }

        Agent agent = agents.get(id);
        if (agent != null) {
            return agent;
        }

        return null;
    }

    public boolean switchTo(String id) {
        Agent agent = agents.get(id);
        if (agent == null) {
            logger.warning("Agent does not exist: " + id);
            return false;
        }

        currentAgent = agent;
        logger.info("Switched to Agent: " + id);
        return true;
    }

    public Agent getCurrent() {
        return currentAgent;
    }

    public Collection<Agent> getAll() {
        return Collections.unmodifiableCollection(agents.values());
    }

    public List<String> listAgentIds() {
        return List.copyOf(agents.keySet());
    }

    public boolean hasAgent(String id) {
        return agents.containsKey(id);
    }

    public static AgentRegistry createDefault() {
        return new AgentRegistry(ToolRegistry.createDefault());
    }
}
