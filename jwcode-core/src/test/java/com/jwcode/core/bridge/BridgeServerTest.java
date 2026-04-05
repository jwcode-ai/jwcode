package com.jwcode.core.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * BridgeServer 测试
 */
public class BridgeServerTest {
    
    private BridgeServer server;
    private static final int TEST_PORT = 18080;
    
    @BeforeEach
    void setUp() throws Exception {
        server = new BridgeServer(TEST_PORT);
        server.start();
        // 等待服务器启动
        Thread.sleep(100);
    }
    
    @AfterEach
    void tearDown() {
        server.stop();
    }
    
    @Test
    void testConnect() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/bridge/connect");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        
        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode);
        
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            String response = scanner.useDelimiter("\\A").next();
            assertTrue(response.contains("success"));
            assertTrue(response.contains("sessionId"));
        }
    }
    
    @Test
    void testStatus() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/bridge/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode);
        
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            String response = scanner.useDelimiter("\\A").next();
            assertTrue(response.contains("running"));
        }
    }
}
