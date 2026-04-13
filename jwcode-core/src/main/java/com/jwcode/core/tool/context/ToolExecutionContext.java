package com.jwcode.core.tool.context;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.permission.PermissionChecker;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行上下文 - Phase 2 增强版
 * 
 * 提供工具执行时所需的环境信息和状态
 * 新增对 AgentRegistry 和 LLMService 的支持
 */
public class ToolExecutionContext {
    
    private final Session session;
    private final Path workingDirectory;
    private final PermissionChecker permissionChecker;
    private final Map<String, Object> state;
    private final long startTime;
    private final boolean interactive;
    
    // Phase 2: Agent 和 LLM 服务
    private AgentRegistry agentRegistry;
    private LLMService llmService;
    
    public ToolExecutionContext(Session session, Path workingDirectory, 
                                 PermissionChecker permissionChecker) {
        this(session, workingDirectory, permissionChecker, true);
    }
    
    public ToolExecutionContext(Session session, Path workingDirectory, 
                                 PermissionChecker permissionChecker, boolean interactive) {
        this.session = session;
        this.workingDirectory = workingDirectory;
        this.permissionChecker = permissionChecker;
        this.state = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
        this.interactive = interactive;
    }
    
    /**
     * 获取当前会话
     */
    public Session getSession() {
        return session;
    }
    
    /**
     * 获取工作目录
     */
    public Path getWorkingDirectory() {
        return workingDirectory;
    }
    
    /**
     * 获取权限检查器
     */
    public PermissionChecker getPermissionChecker() {
        return permissionChecker;
    }
    
    /**
     * 获取状态存储
     */
    public Map<String, Object> getState() {
        return state;
    }
    
    /**
     * 获取执行开始时间
     */
    public long getStartTime() {
        return startTime;
    }
    
    /**
     * 获取已执行时间（毫秒）
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 检查是否为交互式会话
     */
    public boolean isInteractive() {
        return interactive;
    }
    
    /**
     * 检查是否有权限执行操作
     */
    public boolean hasPermission(String operation, String resource) {
        return permissionChecker == null || 
               permissionChecker.hasPermission(operation, resource);
    }
    
    /**
     * 存储临时数据
     */
    public void putState(String key, Object value) {
        state.put(key, value);
    }
    
    /**
     * 获取临时数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getState(String key) {
        return (T) state.get(key);
    }
    
    /**
     * 获取 Agent 注册表
     */
    public AgentRegistry getAgentRegistry() {
        return agentRegistry;
    }
    
    /**
     * 设置 Agent 注册表
     */
    public void setAgentRegistry(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }
    
    /**
     * 获取 LLM 服务
     */
    public LLMService getLLMService() {
        return llmService;
    }
    
    /**
     * 设置 LLM 服务
     */
    public void setLLMService(LLMService llmService) {
        this.llmService = llmService;
    }
    
    /**
     * 是否有 Agent 注册表
     */
    public boolean hasAgentRegistry() {
        return agentRegistry != null;
    }
    
    /**
     * 是否有 LLM 服务
     */
    public boolean hasLLMService() {
        return llmService != null;
    }
    
    /**
     * 创建子上下文
     */
    public ToolExecutionContext createChildContext() {
        ToolExecutionContext child = new ToolExecutionContext(
            session, workingDirectory, permissionChecker, interactive
        );
        child.state.putAll(this.state);
        child.agentRegistry = this.agentRegistry;
        child.llmService = this.llmService;
        return child;
    }
    
    /**
     * Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Session session;
        private Path workingDirectory;
        private PermissionChecker permissionChecker;
        private boolean interactive = true;
        private AgentRegistry agentRegistry;
        private LLMService llmService;
        
        public Builder session(Session session) {
            this.session = session;
            return this;
        }
        
        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }
        
        public Builder permissionChecker(PermissionChecker permissionChecker) {
            this.permissionChecker = permissionChecker;
            return this;
        }
        
        public Builder interactive(boolean interactive) {
            this.interactive = interactive;
            return this;
        }
        
        public Builder agentRegistry(AgentRegistry agentRegistry) {
            this.agentRegistry = agentRegistry;
            return this;
        }
        
        public Builder llmService(LLMService llmService) {
            this.llmService = llmService;
            return this;
        }
        
        public ToolExecutionContext build() {
            ToolExecutionContext context = new ToolExecutionContext(
                session, workingDirectory, permissionChecker, interactive
            );
            context.agentRegistry = this.agentRegistry;
            context.llmService = this.llmService;
            return context;
        }
    }
}
