package com.jwcode.core.mcp;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 集成测试
 *
 * <p>测试 MCP 连接管理、配置管理核心功能。</p>
 */
@DisplayName("MCP 集成测试")
public class MCPIntegrationTest {

    private McpConnectionManager connectionManager;
    private McpConfig config;
    private McpConnectionManager.ConnectionListener mockListener;

    @BeforeEach
    void setUp() {
        config = new McpConfig();
        mockListener = Mockito.mock(McpConnectionManager.ConnectionListener.class);
        connectionManager = new McpConnectionManager(mockListener);
    }

    @Test
    @DisplayName("注册 MCP 服务器配置")
    void testRegisterServer() {
        var serverConfig = new McpConfig.McpServerConfig("test-server", "stdio", "echo");
        connectionManager.registerServer("test-server", serverConfig);
        // 注册成功（不抛异常）
    }

    @Test
    @DisplayName("连接 MCP 服务器")
    void testConnectToServer() throws Exception {
        var serverConfig = new McpConfig.McpServerConfig("test-server", "stdio", "echo");
        connectionManager.registerServer("test-server", serverConfig);

        CompletableFuture<Boolean> future = connectionManager.connect("test-server");
        Boolean result = future.get(10, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    @DisplayName("断开 MCP 服务器连接")
    void testDisconnectServer() throws Exception {
        var serverConfig = new McpConfig.McpServerConfig("test-server", "stdio", "echo");
        connectionManager.registerServer("test-server", serverConfig);

        connectionManager.connect("test-server").get(10, TimeUnit.SECONDS);
        CompletableFuture<Void> disconnectFuture = connectionManager.disconnect("test-server");
        disconnectFuture.get(10, TimeUnit.SECONDS);
        // 断开成功（不抛异常）
    }

    @Test
    @DisplayName("重新连接 MCP 服务器")
    void testReconnectServer() throws Exception {
        var serverConfig = new McpConfig.McpServerConfig("test-server", "stdio", "echo");
        connectionManager.registerServer("test-server", serverConfig);

        CompletableFuture<Boolean> future = connectionManager.reconnect("test-server");
        Boolean result = future.get(10, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    @DisplayName("断开所有 MCP 服务器连接")
    void testDisconnectAll() throws Exception {
        var cfg1 = new McpConfig.McpServerConfig("server-1", "stdio", "echo");
        var cfg2 = new McpConfig.McpServerConfig("server-2", "stdio", "echo");
        connectionManager.registerServer("server-1", cfg1);
        connectionManager.registerServer("server-2", cfg2);

        CompletableFuture<Void> future = connectionManager.disconnectAll();
        future.get(10, TimeUnit.SECONDS);
        // 全部断开成功（不抛异常）
    }

    @Test
    @DisplayName("获取连接状态")
    void testGetConnectionStatus() {
        var config = new McpConfig.McpServerConfig("test-server", "stdio", "echo");
        connectionManager.registerServer("test-server", config);

        McpConnectionManager.ConnectionStatus status = connectionManager.getConnectionStatus("test-server");
        assertNotNull(status, "已注册服务器应返回连接状态");
    }

    @Test
    @DisplayName("获取全部连接状态")
    void testGetAllConnectionStatuses() {
        var cfg1 = new McpConfig.McpServerConfig("server-1", "stdio", "echo");
        var cfg2 = new McpConfig.McpServerConfig("server-2", "stdio", "echo");
        connectionManager.registerServer("server-1", cfg1);
        connectionManager.registerServer("server-2", cfg2);

        Map<String, McpConnectionManager.ConnectionStatus> statuses = connectionManager.getAllConnectionStatuses();
        assertEquals(2, statuses.size());
    }

    @Test
    @DisplayName("获取已连接服务器列表")
    void testGetConnectedServers() {
        var config = new McpConfig.McpServerConfig("test-server", "stdio", "echo");
        connectionManager.registerServer("test-server", config);

        List<String> connected = connectionManager.getConnectedServers();
        assertNotNull(connected);
    }

    // ========== McpConfig 测试 ==========

    @Test
    @DisplayName("McpConfig 默认构造")
    void testMcpConfigDefaults() {
        McpConfig cfg = new McpConfig();
        assertTrue(cfg.isEnabled());
        assertEquals(30000, cfg.getConnectionTimeout());
        assertEquals(3, cfg.getMaxRetries());
    }

    @Test
    @DisplayName("McpConfig 启用/禁用")
    void testMcpConfigEnabled() {
        McpConfig cfg = new McpConfig();
        cfg.setEnabled(false);
        assertFalse(cfg.isEnabled());
    }

    @Test
    @DisplayName("McpConfig 超时设置")
    void testMcpConfigTimeout() {
        McpConfig cfg = new McpConfig();
        cfg.setConnectionTimeout(60000);
        assertEquals(60000, cfg.getConnectionTimeout());
    }

    @Test
    @DisplayName("McpConfig 重试设置")
    void testMcpConfigRetries() {
        McpConfig cfg = new McpConfig();
        cfg.setMaxRetries(5);
        assertEquals(5, cfg.getMaxRetries());
    }

    @Test
    @DisplayName("McpServerConfig 构造")
    void testMcpServerConfig() {
        var serverCfg = new McpConfig.McpServerConfig("my-server", "stdio", "node");
        assertEquals("my-server", serverCfg.name());
        assertEquals("stdio", serverCfg.type());
        assertEquals("node", serverCfg.command());
        assertTrue(serverCfg.enabled());
    }

    @Test
    @DisplayName("McpServerConfig 默认启用")
    void testMcpServerConfigDefaultEnabled() {
        var serverCfg = new McpConfig.McpServerConfig("s1", "sse", "python");
        assertTrue(serverCfg.enabled());
    }
}
