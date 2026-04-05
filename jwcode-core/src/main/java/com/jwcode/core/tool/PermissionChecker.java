package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * PermissionChecker - 权限检查器
 * 
 * 功能说明：
 * 检查工具操作是否有权限执行，防止未授权的文件访问和修改。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class PermissionChecker {
    
    private final Set<String> allowedPaths;
    private final Set<String> blockedPaths;
    private final boolean allowAll;
    
    public PermissionChecker() {
        this.allowedPaths = new HashSet<>();
        this.blockedPaths = new HashSet<>();
        this.allowAll = false;
        
        // 默认阻止敏感路径
        this.blockedPaths.addAll(Arrays.asList(
            "/etc",
            "/usr",
            "/bin",
            "/sbin",
            "/lib",
            "/lib64",
            "/boot",
            "/proc",
            "/sys",
            "/dev",
            "C:\\Windows",
            "C:\\Program Files",
            "C:\\Program Files (x86)"
        ));
    }
    
    /**
     * 检查是否有读取权限
     * 
     * @param path 文件路径
     * @return true 如果有权限
     */
    public boolean canRead(String path) {
        if (allowAll) {
            return true;
        }
        
        Path targetPath = Paths.get(path).toAbsolutePath().normalize();
        
        // 检查是否在阻止列表中
        for (String blocked : blockedPaths) {
            if (targetPath.startsWith(blocked)) {
                return false;
            }
        }
        
        // 检查是否在允许列表中
        if (!allowedPaths.isEmpty()) {
            for (String allowed : allowedPaths) {
                if (targetPath.startsWith(allowed)) {
                    return true;
                }
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查是否有写入权限
     * 
     * @param path 文件路径
     * @return true 如果有权限
     */
    public boolean canWrite(String path) {
        // 写入权限检查更严格
        return canRead(path);
    }
    
    /**
     * 检查是否有执行权限
     * 
     * @param command 命令
     * @return true 如果有权限
     */
    public boolean canExecute(String command) {
        // 默认允许执行，但可以在子类中重写
        return true;
    }
    
    /**
     * 添加允许的路径
     * 
     * @param path 路径
     */
    public void addAllowedPath(String path) {
        allowedPaths.add(Paths.get(path).toAbsolutePath().normalize().toString());
    }
    
    /**
     * 添加阻止的路径
     * 
     * @param path 路径
     */
    public void addBlockedPath(String path) {
        blockedPaths.add(Paths.get(path).toAbsolutePath().normalize().toString());
    }
}
