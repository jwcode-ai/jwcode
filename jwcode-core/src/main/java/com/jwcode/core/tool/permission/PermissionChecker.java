package com.jwcode.core.tool.permission;

/**
 * 权限检查器接口
 */
public interface PermissionChecker {
    
    /**
     * 检查是否有权限执行操作
     * 
     * @param operation 操作类型（如 read, write, execute）
     * @param resource 资源路径
     * @return true 如果有权限
     */
    boolean hasPermission(String operation, String resource);
    
    /**
     * 检查文件读取权限
     */
    default boolean canReadFile(String filePath) {
        return hasPermission("read", filePath);
    }
    
    /**
     * 检查文件写入权限
     */
    default boolean canWriteFile(String filePath) {
        return hasPermission("write", filePath);
    }
    
    /**
     * 检查命令执行权限
     */
    default boolean canExecuteCommand(String command) {
        return hasPermission("execute", command);
    }
    
    /**
     * 检查是否需要用户确认
     */
    default boolean requiresApproval(String operation, String resource) {
        return false;
    }
}
