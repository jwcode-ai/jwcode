package com.jwcode.mcp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * McpClientInterface - MCP 客户端接口。
 * 
 * <p>Model Context Protocol (MCP) 客户端，用于连接和管理 MCP 服务器。此接口
 * 定义了 MCP 协议的基本操作。实际实现位于 jwcode-core 模块的 McpClient 类中。</p>
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface McpClientInterface {
    
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