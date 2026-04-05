package com.jwcode.core.tool.context;

import com.jwcode.core.session.Session;
import com.jwcode.core.tool.permission.PermissionChecker;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行上下文
 * 
 * 提供工具执行时所需的环境信息和状态
 */
public class ToolExecutionContext {
    
    private final Session session;
    private final Path workingDirectory;
    private final PermissionChecker permissionChecker;
    private final Map<String, Object> state;
    private final long startTime;
    private final boolean interactive;
    
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
     * 创建子上下文
     */
    public ToolExecutionContext createChildContext() {
        ToolExecutionContext child = new ToolExecutionContext(
            session, workingDirectory, permissionChecker, interactive
        );
        child.state.putAll(this.state);
        return child;
    }
}