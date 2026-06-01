package com.jwcode.core.tool.context;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.WorkspaceGuard;
import com.jwcode.core.tool.permission.PermissionChecker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 工具执行上下文 - Phase 2 增强版
 * 
 * 提供工具执行时所需的环境信息和状态
 * 新增对 AgentRegistry 和 LLMService 的支持
 * 
 * <p>【工作区安全】 包含 {@link WorkspaceGuard} 用于校验所有文件操作
 * 不超出工作目录范围。</p>
 */
public class ToolExecutionContext {
    
    private static final Logger logger = Logger.getLogger(ToolExecutionContext.class.getName());

    private final Session session;
    private final Path workingDirectory;
    private final PermissionChecker permissionChecker;
    private final Map<String, Object> state;
    private final long startTime;
    private final boolean interactive;
    
    // Phase 2: Agent 和 LLM 服务
    private AgentRegistry agentRegistry;
    private LLMService llmService;
    
    // 工作区安全守卫
    private WorkspaceGuard workspaceGuard;

    // 工作区守卫绕过开关（允许临时取消工作目录限制）
    private boolean bypassWorkspaceGuard = false;
    
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
        // 自动初始化工作区守卫
        this.workspaceGuard = initWorkspaceGuard(workingDirectory);
    }
    
    /**
     * 自动从 workingDirectory 初始化 WorkspaceGuard。
     */
    private static WorkspaceGuard initWorkspaceGuard(Path workingDirectory) {
        if (workingDirectory != null && java.nio.file.Files.isDirectory(workingDirectory)) {
            try {
                return new WorkspaceGuard(workingDirectory);
            } catch (Exception e) {
                logger.warning("[ToolExecutionContext] 无法初始化 WorkspaceGuard: " + e.getMessage());
                return null;
            }
        }
        if (workingDirectory != null) {
            logger.fine("[ToolExecutionContext] workingDirectory 不是有效目录，跳过 WorkspaceGuard 初始化");
        }
        return null;
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
    
    // ==================== 工作区安全 ====================
    
    /**
     * 获取工作区守卫。
     * 
     * @return WorkspaceGuard 实例，可能为 null（如果未配置工作目录）
     */
    public WorkspaceGuard getWorkspaceGuard() {
        return workspaceGuard;
    }
    
    /**
     * 设置工作区守卫（覆盖自动初始化的守卫）。
     */
    public void setWorkspaceGuard(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }
    
    /**
     * 是否有工作区守卫。
     */
    public boolean hasWorkspaceGuard() {
        return workspaceGuard != null;
    }

    /**
     * 是否绕过工作区守卫（允许访问工作目录外的路径）。
     */
    public boolean isBypassWorkspaceGuard() {
        return bypassWorkspaceGuard;
    }

    /**
     * 设置是否绕过工作区守卫。
     */
    public void setBypassWorkspaceGuard(boolean bypassWorkspaceGuard) {
        this.bypassWorkspaceGuard = bypassWorkspaceGuard;
    }
    
    /**
     * 校验路径是否在工作区内。
     * 这是一个便捷方法，委托给 WorkspaceGuard。
     * 
     * @param targetPath 要校验的路径
     * @param toolName   工具名称
     * @throws WorkspaceGuard.WorkspaceAccessException 如果路径不在工作区内
     */
    public void validatePath(Path targetPath, String toolName) {
        if (workspaceGuard != null && !bypassWorkspaceGuard) {
            workspaceGuard.validateOrThrow(targetPath, toolName);
        }
    }
    
    /**
     * 解析并校验路径（便捷方法）。
     * 
     * @param rawPath   原始路径（可以是相对路径）
     * @param toolName  工具名称
     * @return 解析后的已验证绝对路径
     * @throws WorkspaceGuard.WorkspaceAccessException 如果路径不在工作区内
     */
    public Path resolveAndValidate(String rawPath, String toolName) {
        if (workspaceGuard != null && !bypassWorkspaceGuard) {
            Path workingDir = workingDirectory != null ? workingDirectory : Path.of("").toAbsolutePath();
            return workspaceGuard.resolveAndValidate(rawPath, workingDir, toolName);
        }
        // 没有守卫或被绕过时，回退到简单解析
        Path path = Path.of(rawPath);
        if (!path.isAbsolute() && workingDirectory != null) {
            path = workingDirectory.resolve(path);
        }
        return path.normalize().toAbsolutePath();
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
        child.workspaceGuard = this.workspaceGuard;
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
