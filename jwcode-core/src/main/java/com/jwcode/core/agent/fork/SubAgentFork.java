package com.jwcode.core.agent.fork;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentManager;
import com.jwcode.core.llm.*;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionFork;
import com.jwcode.core.tool.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SubAgent Fork - 子 Agent Fork 机制
 * 
 * 功能说明：
 * 从父 Agent Fork 出子 Agent，实现上下文继承和隔离。
 * 子 Agent 可以看到父 Agent 的历史对话，但执行状态完全隔离。
 * 
 * 继承关系：
 * - 会话历史：继承（深拷贝）
 * - 工具集：继承（引用）
 * - 工作目录：继承（引用）
 * - 系统提示：可自定义
 * - 模型配置：继承并可覆盖
 * 
 * 使用场景：
 * 1. 并行执行多个子任务
 * 2. 隔离执行环境
 * 3. 临时切换 Agent 角色
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SubAgentFork {
    
    private final Agent parentAgent;
    private final Session parentSession;
    private final ToolRegistry toolRegistry;
    private final List<Tool<?, ?, ?>> tools;
    
    private String taskDescription;
    private String agentType = "general";
    private String customSystemPrompt;
    private Map<String, Object> contextData = new HashMap<>();
    private LLMQueryEngine.EngineConfig engineConfig;
    private long timeoutMs = 60000;
    
    public SubAgentFork(Agent parentAgent, Session parentSession, ToolRegistry toolRegistry) {
        this.parentAgent = parentAgent;
        this.parentSession = parentSession;
        this.toolRegistry = toolRegistry;
        this.tools = new ArrayList<>();
        this.engineConfig = LLMQueryEngine.EngineConfig.defaultConfig();
    }
    
    /**
     * 设置任务描述
     */
    public SubAgentFork withTask(String description) {
        this.taskDescription = description;
        return this;
    }
    
    /**
     * 设置 Agent 类型
     */
    public SubAgentFork withAgentType(String type) {
        this.agentType = type;
        return this;
    }
    
    /**
     * 设置自定义系统提示
     */
    public SubAgentFork withSystemPrompt(String prompt) {
        this.customSystemPrompt = prompt;
        return this;
    }
    
    /**
     * 添加上下文数据
     */
    public SubAgentFork withContext(String key, Object value) {
        this.contextData.put(key, value);
        return this;
    }
    
    /**
     * 添加工具
     */
    public SubAgentFork withTool(Tool<?, ?, ?> tool) {
        this.tools.add(tool);
        return this;
    }
    
    /**
     * 继承父 Agent 的所有工具
     */
    public SubAgentFork withParentTools() {
        this.tools.addAll(parentAgent.getTools());
        return this;
    }
    
    /**
     * 设置引擎配置
     */
    public SubAgentFork withEngineConfig(LLMQueryEngine.EngineConfig config) {
        this.engineConfig = config;
        return this;
    }
    
    /**
     * 设置超时时间
     */
    public SubAgentFork withTimeout(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }
    
    /**
     * 执行 Fork 并创建子 Agent
     */
    public SubAgentResult execute() {
        // 1. Fork Session
        Session forkedSession = SessionFork.from(parentSession, taskDescription)
            .withContext("agentType", agentType)
            .withContext("parentAgentId", parentAgent.getId())
            .withContext("taskDescription", taskDescription)
            .execute();
        
        // 2. 添加上下文数据到 Session
        for (Map.Entry<String, Object> entry : contextData.entrySet()) {
            forkedSession.setMetadata(entry.getKey(), entry.getValue());
        }
        
        // 3. 创建子 Agent
        ForkedAgent childAgent = new ForkedAgent(
            UUID.randomUUID().toString(),
            agentType + "-" + System.currentTimeMillis(),
            taskDescription,
            buildSystemPrompt(),
            tools.isEmpty() ? parentAgent.getTools() : tools,
            parentAgent.getConfig(),
            parentAgent.getModelConfig(),
            parentAgent.getId()
        );
        
        // 4. 创建 LLMQueryEngine
        // TODO: 需要从配置创建 LLMFactory
        LLMFactory llmFactory = LLMFactory.createDefault();
        LLMQueryEngine engine = llmFactory.createQueryEngine(forkedSession);
        
        // 5. 返回结果
        return new SubAgentResult(childAgent, forkedSession, engine, timeoutMs);
    }
    
    /**
     * 异步执行
     */
    public CompletableFuture<SubAgentResult> executeAsync() {
        return CompletableFuture.supplyAsync(this::execute);
    }
    
    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        if (customSystemPrompt != null) {
            return customSystemPrompt;
        }
        
        return String.format("""
            你是一个专业的 %s Agent。
            
            当前任务：%s
            
            你已经继承了父 Agent 的上下文，可以访问历史对话记录。
            请专注于完成当前任务，任务完成后返回结果。
            
            父 Agent ID: %s
            """, agentType, taskDescription, parentAgent.getId());
    }
    
    /**
     * Forked Agent 实现
     */
    public static class ForkedAgent implements Agent {
        private final String id;
        private final String name;
        private final String description;
        private final String systemPrompt;
        private final List<Tool<?, ?, ?>> tools;
        private final Map<String, Object> config;
        private final ModelConfig modelConfig;
        private final String parentAgentId;
        
        public ForkedAgent(String id, String name, String description, 
                          String systemPrompt, List<Tool<?, ?, ?>> tools,
                          Map<String, Object> config, ModelConfig modelConfig,
                          String parentAgentId) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.systemPrompt = systemPrompt;
            this.tools = tools;
            this.config = new HashMap<>(config);
            this.modelConfig = modelConfig;
            this.parentAgentId = parentAgentId;
        }
        
        @Override
        public String getId() { return id; }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public String getDescription() { return description; }
        
        @Override
        public String getSystemPrompt() { return systemPrompt; }
        
        @Override
        public List<Tool<?, ?, ?>> getTools() { return tools; }
        
        @Override
        public Map<String, Object> getConfig() { return config; }
        
        @Override
        public ModelConfig getModelConfig() { return modelConfig; }
        
        @Override
        public boolean canUseTool(String toolName) {
            return tools.stream().anyMatch(t -> t.getName().equals(toolName));
        }
        
        @Override
        public String getParentAgentId() { return parentAgentId; }
    }
    
    /**
     * 子 Agent 执行结果
     */
    public static class SubAgentResult {
        private final ForkedAgent agent;
        private final Session session;
        private final LLMQueryEngine engine;
        private final long timeoutMs;
        
        public SubAgentResult(ForkedAgent agent, Session session, 
                             LLMQueryEngine engine, long timeoutMs) {
            this.agent = agent;
            this.session = session;
            this.engine = engine;
            this.timeoutMs = timeoutMs;
        }
        
        public ForkedAgent getAgent() { return agent; }
        public Session getSession() { return session; }
        public LLMQueryEngine getEngine() { return engine; }
        public long getTimeoutMs() { return timeoutMs; }
        
        /**
         * 在子 Agent 中执行任务
         */
        public CompletableFuture<String> run(String prompt) {
            return engine.query(prompt)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        Message message = result.getMessage();
                        // 提取文本内容
                        StringBuilder sb = new StringBuilder();
                        for (Message.ContentBlock block : message.getContent()) {
                            if (block instanceof Message.TextContent) {
                                sb.append(((Message.TextContent) block).getText());
                            }
                        }
                        return sb.toString();
                    } else {
                        return "Error: " + result.getErrorMessage();
                    }
                })
                .orTimeout(timeoutMs / 1000, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(throwable -> "Failed: " + throwable.getMessage());
        }
    }
    
    /**
     * 便捷方法：从父 Agent Fork
     */
    public static SubAgentFork from(Agent parent, Session session, ToolRegistry registry) {
        return new SubAgentFork(parent, session, registry);
    }
}
