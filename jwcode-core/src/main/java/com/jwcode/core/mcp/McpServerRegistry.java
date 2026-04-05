package com.jwcode.core.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * McpServerRegistry - MCP 服务器注册表
 * 
 * 功能说明：
 * 管理所有 MCP 服务器的注册信息，包括服务器元数据、工具列表、资源列表等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpServerRegistry {
    
    private final Map<String, ServerInfo> registeredServers;
    private final Map<String, List<ToolInfo>> serverTools;
    private final Map<String, List<ResourceInfo>> serverResources;
    
    public McpServerRegistry() {
        this.registeredServers = new ConcurrentHashMap<>();
        this.serverTools = new ConcurrentHashMap<>();
        this.serverResources = new ConcurrentHashMap<>();
    }
    
    /**
     * 注册服务器
     */
    public void registerServer(String name, ServerInfo info) {
        registeredServers.put(name, info);
    }
    
    /**
     * 取消注册服务器
     */
    public void unregisterServer(String name) {
        registeredServers.remove(name);
        serverTools.remove(name);
        serverResources.remove(name);
    }
    
    /**
     * 获取服务器信息
     */
    public ServerInfo getServerInfo(String name) {
        return registeredServers.get(name);
    }
    
    /**
     * 获取所有注册的服务器
     */
    public List<String> getAllServers() {
        return new ArrayList<>(registeredServers.keySet());
    }
    
    /**
     * 注册工具
     */
    public void registerTool(String serverName, ToolInfo tool) {
        serverTools.computeIfAbsent(serverName, k -> new ArrayList<>()).add(tool);
    }
    
    /**
     * 获取服务器的工具列表
     */
    public List<ToolInfo> getTools(String serverName) {
        return serverTools.getOrDefault(serverName, new ArrayList<>());
    }
    
    /**
     * 获取特定工具
     */
    public ToolInfo getTool(String serverName, String toolName) {
        List<ToolInfo> tools = serverTools.get(serverName);
        if (tools != null) {
            for (ToolInfo tool : tools) {
                if (tool.name.equals(toolName)) {
                    return tool;
                }
            }
        }
        return null;
    }
    
    /**
     * 注册资源
     */
    public void registerResource(String serverName, ResourceInfo resource) {
        serverResources.computeIfAbsent(serverName, k -> new ArrayList<>()).add(resource);
    }
    
    /**
     * 获取服务器的资源列表
     */
    public List<ResourceInfo> getResources(String serverName) {
        return serverResources.getOrDefault(serverName, new ArrayList<>());
    }
    
    /**
     * 获取特定资源
     */
    public ResourceInfo getResource(String serverName, String resourceUri) {
        List<ResourceInfo> resources = serverResources.get(serverName);
        if (resources != null) {
            for (ResourceInfo resource : resources) {
                if (resource.uri.equals(resourceUri)) {
                    return resource;
                }
            }
        }
        return null;
    }
    
    /**
     * 搜索工具
     */
    public List<ToolInfo> searchTools(String query) {
        List<ToolInfo> results = new ArrayList<>();
        for (List<ToolInfo> tools : serverTools.values()) {
            for (ToolInfo tool : tools) {
                if (tool.name.toLowerCase().contains(query.toLowerCase()) ||
                    tool.description.toLowerCase().contains(query.toLowerCase())) {
                    results.add(tool);
                }
            }
        }
        return results;
    }
    
    /**
     * 搜索资源
     */
    public List<ResourceInfo> searchResources(String query) {
        List<ResourceInfo> results = new ArrayList<>();
        for (List<ResourceInfo> resources : serverResources.values()) {
            for (ResourceInfo resource : resources) {
                if (resource.name.toLowerCase().contains(query.toLowerCase()) ||
                    resource.description.toLowerCase().contains(query.toLowerCase())) {
                    results.add(resource);
                }
            }
        }
        return results;
    }
    
    /**
     * 服务器信息类
     */
    public static class ServerInfo {
        public String name;
        public String version;
        public String description;
        public Map<String, String> capabilities;
        
        public ServerInfo(String name, String version, String description) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.capabilities = new HashMap<>();
        }
        
        public void setCapability(String key, String value) {
            capabilities.put(key, value);
        }
        
        public boolean hasCapability(String key) {
            return capabilities.containsKey(key) && 
                   Boolean.parseBoolean(capabilities.get(key));
        }
    }
    
    /**
     * 工具信息类
     */
    public static class ToolInfo {
        public String name;
        public String description;
        public Map<String, Object> inputSchema;
        
        public ToolInfo(String name, String description) {
            this.name = name;
            this.description = description;
            this.inputSchema = new HashMap<>();
        }
        
        public void addParameter(String paramName, String type, String desc) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) 
                inputSchema.computeIfAbsent("properties", k -> new HashMap<>());
            Map<String, Object> paramInfo = new HashMap<>();
            paramInfo.put("type", type);
            paramInfo.put("description", desc);
            properties.put(paramName, paramInfo);
        }
    }
    
    /**
     * 资源信息类
     */
    public static class ResourceInfo {
        public String uri;
        public String name;
        public String description;
        public String mimeType;
        
        public ResourceInfo(String uri, String name, String description) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.mimeType = "text/plain";
        }
        
        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
    }
}