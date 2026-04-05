package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * CanUseToolFn - 权限检查函数接口
 * 
 * 功能说明：
 * 用于检查当前上下文是否允许使用指定工具的函数接口。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@FunctionalInterface
public interface CanUseToolFn {
    
    /**
     * 行为枚举
     */
    enum Behavior {
        ALLOW,
        DENY,
        ASK_USER
    }
    
    /**
     * 检查是否可以使用指定名称的工具
     * 
     * @param toolName 工具名称
     * @param args 工具参数
     * @return true 如果允许使用该工具
     */
    boolean canUseTool(String toolName, Object args);
    
    /**
     * 检查是否可以使用指定工具（返回详细结果）
     * 
     * @param toolName 工具名称
     * @param args 工具参数
     * @return 权限结果
     */
    default PermissionResult check(String toolName, Map<String, Object> args) {
        boolean allowed = canUseTool(toolName, args);
        return allowed ? PermissionResult.allow() : PermissionResult.deny("权限被拒绝");
    }
    
    /**
     * 异步检查是否可以使用指定工具
     * 
     * @param toolName 工具名称
     * @param args 工具参数
     * @return 包含检查结果的 CompletableFuture
     */
    default CompletableFuture<Boolean> canUseToolAsync(String toolName, Object args) {
        return CompletableFuture.completedFuture(canUseTool(toolName, args));
    }
    
    /**
     * 创建一个始终允许使用的实现
     * 
     * @return 允许所有工具的实现
     */
    static CanUseToolFn allowAll() {
        return (toolName, args) -> true;
    }
    
    /**
     * 创建一个始终拒绝使用的实现
     * 
     * @return 拒绝所有工具的实现
     */
    static CanUseToolFn denyAll() {
        return (toolName, args) -> false;
    }
    
    /**
     * 权限结果类
     */
    class PermissionResult {
        private final Behavior behavior;
        private final String reason;
        
        protected PermissionResult(Behavior behavior, String reason) {
            this.behavior = behavior;
            this.reason = reason;
        }
        
        public static PermissionResult allow() {
            return new PermissionResult(Behavior.ALLOW, null);
        }
        
        public static PermissionResult deny(String reason) {
            return new PermissionResult(Behavior.DENY, reason);
        }
        
        public static PermissionResult askUser(String reason) {
            return new PermissionResult(Behavior.ASK_USER, reason);
        }
        
        public boolean isAllowed() {
            return behavior == Behavior.ALLOW;
        }
        
        public boolean isDenied() {
            return behavior == Behavior.DENY;
        }
        
        public boolean isAskUser() {
            return behavior == Behavior.ASK_USER;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
