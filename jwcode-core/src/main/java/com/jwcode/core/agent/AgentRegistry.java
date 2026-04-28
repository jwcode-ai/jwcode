package com.jwcode.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Agent 注册表 - 管理所有 Agent
 * 
 * 功能：
 * - 注册/注销 Agent
 * - 从配置文件加载 Agent
 * - Agent 查找和切换
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
    
    /**
     * 注册默认 Agent
     *
     * <p>严格分层架构：
     * <ul>
     *   <li><b>Orchestrator</b>（主Agent）：唯一入口，只做拆解调度</li>
     *   <li><b>Worker Agents</b>（子Agent）：执行具体工作</li>
     * </ul>
     */
    private void registerDefaultAgents() {
        // ===== 主控 Agent（唯一入口）=====
        register(new OrchestratorAgent());

        // ===== 专业子Agent（Worker）=====
        register(new CoderAgent());
        register(new DebugAgent());
        register(new ReviewerAgent());
        register(new TestAgent());
        register(new DocAgent());
        register(new ExploreAgent());
        register(new ArchitectAgent());

        // 注册通用 Agent（降级兜底）
        register(new DefaultAgent());

        // 设置默认 Agent 为 Orchestrator
        currentAgent = get("orchestrator");
    }
    
    /**
     * 注册 Agent
     */
    public void register(Agent agent) {
        agents.put(agent.getId(), agent);
        logger.info("注册 Agent: " + agent.getId());
    }
    
    /**
     * 从配置文件加载 Agent
     */
    public void loadFromFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("文件不存在: " + path);
        }
        
        AgentDefinition definition = JSON_MAPPER.readValue(file, AgentDefinition.class);
        
        Agent agent = createAgentFromDefinition(definition);
        register(agent);
    }
    
    /**
     * 从定义创建 Agent
     */
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
    
    /**
     * 解析工具列表
     */
    private List<Tool<?, ?, ?>> resolveTools(List<String> toolNames) {
        List<Tool<?, ?, ?>> tools = new ArrayList<>();
        if (toolNames == null) return tools;
        
        for (String name : toolNames) {
            Tool<?, ?, ?> tool = toolRegistry.getTool(name);
            if (tool != null) {
                tools.add(tool);
            } else {
                logger.warning("工具不存在: " + name);
            }
        }
        return tools;
    }
    
    private Agent.ModelConfig convertModelConfig(AgentDefinition.ModelConfig model) {
        if (model == null) return null;
        return new Agent.ModelConfig(
            model.getName(),
            model.getTemperature(),
            model.getMaxTokens()
        );
    }
    
    /**
     * 获取 Agent
     */
    public Agent get(String id) {
        return agents.get(id);
    }
    
    /**
     * 切换当前 Agent
     */
    public boolean switchTo(String id) {
        Agent agent = agents.get(id);
        if (agent == null) {
            logger.warning("Agent 不存在: " + id);
            return false;
        }
        
        currentAgent = agent;
        logger.info("切换到 Agent: " + id);
        return true;
    }
    
    /**
     * 获取当前 Agent
     */
    public Agent getCurrent() {
        return currentAgent;
    }
    
    /**
     * 获取所有 Agent
     */
    public Collection<Agent> getAll() {
        return Collections.unmodifiableCollection(agents.values());
    }
    
    /**
     * 列出所有 Agent ID
     */
    public List<String> listAgentIds() {
        return List.copyOf(agents.keySet());
    }
    
    /**
     * 检查是否存在
     */
    public boolean hasAgent(String id) {
        return agents.containsKey(id);
    }
    
    /**
     * 创建默认注册表
     */
    public static AgentRegistry createDefault() {
        return new AgentRegistry(ToolRegistry.createDefault());
    }
}
