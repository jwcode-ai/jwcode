package com.jwcode.core.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * McpConfig - MCP 配置
 * 
 * 功能说明：
 * 管理 MCP 服务器的配置信息，包括服务器列表、启用状态、连接参数等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpConfig {
    
    private boolean enabled;
    private final Map<String, McpServerConfig> servers;
    private final Map<String, Object> defaultSettings;
    private int connectionTimeout;
    private int maxRetries;
    
    public McpConfig() {
        this.enabled = true;
        this.servers = new HashMap<>();
        this.defaultSettings = new HashMap<>();
        this.connectionTimeout = 30000; // 30 秒
        this.maxRetries = 3;
    }
    
    /**
     * 检查是否启用了 MCP
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 设置 MCP 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * 添加服务器配置
     */
    public void addServer(String name, McpServerConfig config) {
        servers.put(name, config);
    }
    
    /**
     * 移除服务器配置
     */
    public void removeServer(String name) {
        servers.remove(name);
    }
    
    /**
     * 获取服务器配置
     */
    public McpServerConfig getServer(String name) {
        return servers.get(name);
    }
    
    /**
     * 获取所有服务器配置
     */
    public Map<String, McpServerConfig> getAllServers() {
        return new HashMap<>(servers);
    }
    
    /**
     * 获取启用的服务器列表
     */
    public List<String> getEnabledServers() {
        List<String> enabledServers = new ArrayList<>();
        for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
            if (entry.getValue().isEnabled()) {
                enabledServers.add(entry.getKey());
            }
        }
        return enabledServers;
    }
    
    /**
     * 设置默认配置
     */
    public void setDefaultSetting(String key, Object value) {
        defaultSettings.put(key, value);
    }
    
    /**
     * 获取默认配置
     */
    public Object getDefaultSetting(String key) {
        return defaultSettings.get(key);
    }
    
    /**
     * 获取连接超时（毫秒）
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    /**
     * 设置连接超时（毫秒）
     */
    public void setConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }
    
    /**
     * 获取最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * 设置最大重试次数
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    /**
     * 服务器配置类
     */
    public static class McpServerConfig {
        private final String name;
        private final String type; // stdio, sse, websocket
        private final String command;
        private final List<String> args;
        private final Map<String, String> env;
        private boolean enabled;
        private boolean autoConnect;
        
        public McpServerConfig(String name, String type, String command) {
            this.name = name;
            this.type = type;
            this.command = command;
            this.args = new ArrayList<>();
            this.env = new HashMap<>();
            this.enabled = true;
            this.autoConnect = false;
        }
        
        public String getName() {
            return name;
        }
        
        public String getType() {
            return type;
        }
        
        public String getCommand() {
            return command;
        }
        
        public List<String> getArgs() {
            return args;
        }
        
        public void addArg(String arg) {
            this.args.add(arg);
        }
        
        public Map<String, String> getEnv() {
            return env;
        }
        
        public void setEnv(String key, String value) {
            this.env.put(key, value);
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public boolean isAutoConnect() {
            return autoConnect;
        }
        
        public void setAutoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
        }
    }
}