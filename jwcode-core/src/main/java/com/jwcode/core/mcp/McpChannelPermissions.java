package com.jwcode.core.mcp;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * McpChannelPermissions - MCP 通道权限管理
 * 
 * 功能说明：
 * 管理 MCP 通道的访问权限，控制客户端对工具、资源和提示的访问。
 * 支持基于角色的权限控制（RBAC）和细粒度的权限配置。
 * 
 * 核心特性：
 * - 基于角色的权限控制
 * - 工具访问权限管理
 * - 资源访问权限管理
 * - 提示访问权限管理
 * - 动态权限更新
 * 
 * 上下文关系：
 * - 被 McpClient 引用进行权限检查
 * - 与 McpConnectionManager 协作
 * - 为 MCP 请求提供权限验证
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpChannelPermissions {
    
    /**
     * 权限映射表（通道 ID -> 权限配置）
     */
    private final Map<String, ChannelPermissionConfig> channelPermissions;
    
    /**
     * 角色定义映射表（角色名 -> 角色权限）
     */
    private final Map<String, RoleDefinition> roleDefinitions;
    
    /**
     * 默认角色
     */
    private String defaultRole;
    
    /**
     * 构造函数
     */
    public McpChannelPermissions() {
        this.channelPermissions = new ConcurrentHashMap<>();
        this.roleDefinitions = new ConcurrentHashMap<>();
        this.defaultRole = "viewer";
        
        // 注册默认角色
        registerDefaultRoles();
    }
    
    /**
     * 注册默认角色
     */
    private void registerDefaultRoles() {
        // 管理员角色：完全访问权限
        roleDefinitions.put("admin", new RoleDefinition(
                "admin",
                "完全访问权限",
                PermissionLevel.FULL,
                Set.of("*"),
                Set.of("*"),
                Set.of("*")
        ));
        
        // 开发者角色：读写权限
        roleDefinitions.put("developer", new RoleDefinition(
                "developer",
                "开发者权限",
                PermissionLevel.READ_WRITE,
                Set.of("*"),
                Set.of("*"),
                Set.of("*")
        ));
        
        // 查看者角色：只读权限
        roleDefinitions.put("viewer", new RoleDefinition(
                "viewer",
                "只读权限",
                PermissionLevel.READ_ONLY,
                Set.of(),
                Set.of("*"),
                Set.of("*")
        ));
        
        // 受限角色：最小权限
        roleDefinitions.put("restricted", new RoleDefinition(
                "restricted",
                "受限权限",
                PermissionLevel.RESTRICTED,
                Set.of(),
                Set.of(),
                Set.of()
        ));
    }
    
    /**
     * 获取通道的权限配置
     * 
     * @param channelId 通道 ID
     * @return 权限配置
     */
    public ChannelPermissionConfig getChannelPermission(String channelId) {
        return channelPermissions.computeIfAbsent(channelId, k -> 
                new ChannelPermissionConfig(channelId, defaultRole));
    }
    
    /**
     * 设置通道权限
     * 
     * @param channelId 通道 ID
     * @param config 权限配置
     */
    public void setChannelPermission(String channelId, ChannelPermissionConfig config) {
        channelPermissions.put(channelId, config);
    }
    
    /**
     * 检查工具访问权限
     * 
     * @param channelId 通道 ID
     * @param toolName 工具名称
     * @return true 如果允许访问
     */
    public boolean canAccessTool(String channelId, String toolName) {
        ChannelPermissionConfig config = getChannelPermission(channelId);
        RoleDefinition role = roleDefinitions.get(config.getRole());
        
        if (role == null) {
            return false;
        }
        
        // 检查显式允许的工具
        if (role.getAllowedTools().contains("*") || role.getAllowedTools().contains(toolName)) {
            return true;
        }
        
        // 检查显式拒绝的工具
        if (role.getDeniedTools().contains(toolName)) {
            return false;
        }
        
        // 根据权限级别判断
        switch (role.getPermissionLevel()) {
            case FULL:
            case READ_WRITE:
                return true;
            case READ_ONLY:
                return isReadOnlyTool(toolName);
            case RESTRICTED:
            default:
                return false;
        }
    }
    
    /**
     * 检查资源访问权限
     * 
     * @param channelId 通道 ID
     * @param resourceUri 资源 URI
     * @return true 如果允许访问
     */
    public boolean canAccessResource(String channelId, String resourceUri) {
        ChannelPermissionConfig config = getChannelPermission(channelId);
        RoleDefinition role = roleDefinitions.get(config.getRole());
        
        if (role == null) {
            return false;
        }
        
        // 检查显式允许的资源
        if (role.getAllowedResources().contains("*") || 
            matchesPattern(resourceUri, role.getAllowedResources())) {
            return true;
        }
        
        // 检查显式拒绝的资源
        if (matchesPattern(resourceUri, role.getDeniedResources())) {
            return false;
        }
        
        // 根据权限级别判断
        switch (role.getPermissionLevel()) {
            case FULL:
            case READ_WRITE:
            case READ_ONLY:
                return true;
            case RESTRICTED:
            default:
                return false;
        }
    }
    
    /**
     * 检查提示访问权限
     * 
     * @param channelId 通道 ID
     * @param promptName 提示名称
     * @return true 如果允许访问
     */
    public boolean canAccessPrompt(String channelId, String promptName) {
        ChannelPermissionConfig config = getChannelPermission(channelId);
        RoleDefinition role = roleDefinitions.get(config.getRole());
        
        if (role == null) {
            return false;
        }
        
        // 检查显式允许的提示
        if (role.getAllowedPrompts().contains("*") || role.getAllowedPrompts().contains(promptName)) {
            return true;
        }
        
        // 检查显式拒绝的提示
        if (role.getDeniedPrompts().contains(promptName)) {
            return false;
        }
        
        // 根据权限级别判断
        return role.getPermissionLevel() != PermissionLevel.RESTRICTED;
    }
    
    /**
     * 检查是否为只读工具
     */
    private boolean isReadOnlyTool(String toolName) {
        Set<String> readOnlyTools = Set.of(
                "read", "grep", "glob", "search", "list", "get",
                "FileReadTool", "GrepTool", "GlobTool", "WebSearchTool"
        );
        return readOnlyTools.contains(toolName) || toolName.toLowerCase().contains("read");
    }
    
    /**
     * 检查 URI 是否匹配模式列表
     */
    private boolean matchesPattern(String uri, Set<String> patterns) {
        for (String pattern : patterns) {
            if (pattern.equals("*") || uri.matches(pattern.replace("*", ".*"))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 注册角色定义
     * 
     * @param role 角色定义
     */
    public void registerRole(RoleDefinition role) {
        roleDefinitions.put(role.getName(), role);
    }
    
    /**
     * 获取角色定义
     * 
     * @param roleName 角色名称
     * @return 角色定义
     */
    public RoleDefinition getRoleDefinition(String roleName) {
        return roleDefinitions.get(roleName);
    }
    
    /**
     * 获取所有角色
     * 
     * @return 角色列表
     */
    public List<RoleDefinition> getAllRoles() {
        return new ArrayList<>(roleDefinitions.values());
    }
    
    /**
     * 设置默认角色
     * 
     * @param roleName 角色名称
     */
    public void setDefaultRole(String roleName) {
        if (roleDefinitions.containsKey(roleName)) {
            this.defaultRole = roleName;
        }
    }
    
    /**
     * 获取默认角色
     * 
     * @return 默认角色名称
     */
    public String getDefaultRole() {
        return defaultRole;
    }
    
    /**
     * 更新通道角色
     * 
     * @param channelId 通道 ID
     * @param roleName 角色名称
     */
    public void updateChannelRole(String channelId, String roleName) {
        ChannelPermissionConfig config = getChannelPermission(channelId);
        config.setRole(roleName);
    }
    
    /**
     * 移除通道权限
     * 
     * @param channelId 通道 ID
     */
    public void removeChannelPermission(String channelId) {
        channelPermissions.remove(channelId);
    }
    
    /**
     * 清除所有权限配置
     */
    public void clearAllPermissions() {
        channelPermissions.clear();
        roleDefinitions.clear();
        registerDefaultRoles();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 权限级别枚举
     */
    public enum PermissionLevel {
        /** 完全访问 */
        FULL,
        /** 读写权限 */
        READ_WRITE,
        /** 只读权限 */
        READ_ONLY,
        /** 受限权限 */
        RESTRICTED
    }
    
    /**
     * 角色定义类
     */
    public static class RoleDefinition {
        private final String name;
        private final String description;
        private final PermissionLevel permissionLevel;
        private final Set<String> allowedTools;
        private final Set<String> allowedResources;
        private final Set<String> allowedPrompts;
        private final Set<String> deniedTools;
        private final Set<String> deniedResources;
        private final Set<String> deniedPrompts;
        
        /**
         * 构造函数
         */
        public RoleDefinition(String name, String description, PermissionLevel permissionLevel,
                              Set<String> allowedTools, Set<String> allowedResources,
                              Set<String> allowedPrompts) {
            this(name, description, permissionLevel, allowedTools, allowedResources, allowedPrompts,
                 new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
        
        /**
         * 完整构造函数
         */
        public RoleDefinition(String name, String description, PermissionLevel permissionLevel,
                              Set<String> allowedTools, Set<String> allowedResources,
                              Set<String> allowedPrompts, Set<String> deniedTools,
                              Set<String> deniedResources, Set<String> deniedPrompts) {
            this.name = name;
            this.description = description;
            this.permissionLevel = permissionLevel;
            this.allowedTools = new HashSet<>(allowedTools);
            this.allowedResources = new HashSet<>(allowedResources);
            this.allowedPrompts = new HashSet<>(allowedPrompts);
            this.deniedTools = new HashSet<>(deniedTools);
            this.deniedResources = new HashSet<>(deniedResources);
            this.deniedPrompts = new HashSet<>(deniedPrompts);
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public PermissionLevel getPermissionLevel() {
            return permissionLevel;
        }
        
        public Set<String> getAllowedTools() {
            return allowedTools;
        }
        
        public Set<String> getAllowedResources() {
            return allowedResources;
        }
        
        public Set<String> getAllowedPrompts() {
            return allowedPrompts;
        }
        
        public Set<String> getDeniedTools() {
            return deniedTools;
        }
        
        public Set<String> getDeniedResources() {
            return deniedResources;
        }
        
        public Set<String> getDeniedPrompts() {
            return deniedPrompts;
        }
        
        /**
         * 构建器
         */
        public static Builder builder() {
            return new Builder();
        }
        
        /**
         * 构建器类
         */
        public static class Builder {
            private String name;
            private String description;
            private PermissionLevel permissionLevel = PermissionLevel.RESTRICTED;
            private Set<String> allowedTools = new HashSet<>();
            private Set<String> allowedResources = new HashSet<>();
            private Set<String> allowedPrompts = new HashSet<>();
            private Set<String> deniedTools = new HashSet<>();
            private Set<String> deniedResources = new HashSet<>();
            private Set<String> deniedPrompts = new HashSet<>();
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder description(String description) {
                this.description = description;
                return this;
            }
            
            public Builder permissionLevel(PermissionLevel level) {
                this.permissionLevel = level;
                return this;
            }
            
            public Builder allowedTools(Set<String> tools) {
                this.allowedTools = new HashSet<>(tools);
                return this;
            }
            
            public Builder allowedResources(Set<String> resources) {
                this.allowedResources = new HashSet<>(resources);
                return this;
            }
            
            public Builder allowedPrompts(Set<String> prompts) {
                this.allowedPrompts = new HashSet<>(prompts);
                return this;
            }
            
            public Builder deniedTools(Set<String> tools) {
                this.deniedTools = new HashSet<>(tools);
                return this;
            }
            
            public Builder deniedResources(Set<String> resources) {
                this.deniedResources = new HashSet<>(resources);
                return this;
            }
            
            public Builder deniedPrompts(Set<String> prompts) {
                this.deniedPrompts = new HashSet<>(prompts);
                return this;
            }
            
            public RoleDefinition build() {
                return new RoleDefinition(name, description, permissionLevel, 
                        allowedTools, allowedResources, allowedPrompts,
                        deniedTools, deniedResources, deniedPrompts);
            }
        }
    }
    
    /**
     * 通道权限配置类
     */
    public static class ChannelPermissionConfig {
        private final String channelId;
        private String role;
        private Map<String, Object> customPermissions;
        
        public ChannelPermissionConfig(String channelId, String role) {
            this.channelId = channelId;
            this.role = role;
            this.customPermissions = new HashMap<>();
        }
        
        public String getChannelId() {
            return channelId;
        }
        
        public String getRole() {
            return role;
        }
        
        public void setRole(String role) {
            this.role = role;
        }
        
        public Map<String, Object> getCustomPermissions() {
            return customPermissions;
        }
        
        public void setCustomPermissions(Map<String, Object> permissions) {
            this.customPermissions = permissions;
        }
        
        public void addCustomPermission(String key, Object value) {
            this.customPermissions.put(key, value);
        }
        
        public Object getCustomPermission(String key) {
            return customPermissions.get(key);
        }
    }
}