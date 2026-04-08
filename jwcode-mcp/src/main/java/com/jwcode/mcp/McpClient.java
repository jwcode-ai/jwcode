package com.jwcode.mcp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * McpClient - MCP 客户端接口
 * 
 * 功能说明：
 * Model Context Protocol (MCP) 客户端，用于连接和管理 MCP 服务器。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface McpClient {
    
    CompletableFuture<Void> connect();
    
    CompletableFuture<Void> disconnect();
    
    boolean isConnected();
    
    String getServerName();
    
    CompletableFuture<List<McpTool>> listTools();
    
    CompletableFuture<McpToolResult> callTool(String toolName, Map<String, Object> args);
    
    CompletableFuture<List<McpResource>> listResources();
    
    CompletableFuture<McpResourceContent> readResource(String uri);
    
    CompletableFuture<Void> subscribeResource(String uri);
    
    CompletableFuture<Void> unsubscribeResource(String uri);
    
    McpServerInfo getServerInfo();
}