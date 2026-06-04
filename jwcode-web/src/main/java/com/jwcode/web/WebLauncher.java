package com.jwcode.web;

import com.jwcode.core.tool.ToolRegistry;
import java.util.Scanner;

/**
 * Web UI 启动器
 */
public class WebLauncher {
    
    public static void main(String[] args) {
        int httpPort = 8080;
        int wsPort = 8081;
        String workspaceDir = null;
        if (args.length > 0) {
            try {
                httpPort = Integer.parseInt(args[0]);
                wsPort = httpPort + 1;
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号，使用默认端口");
            }
        }
        if (args.length > 1) {
            try {
                wsPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("无效的 WebSocket 端口号");
            }
        }
        if (args.length > 2) {
            workspaceDir = args[2];
            System.setProperty("user.dir", workspaceDir);
        }

        try {
            WebServer server = new WebServer(httpPort, wsPort, ToolRegistry.createDefault(), workspaceDir);
            server.start();

            System.out.println("\n═══════════════════════════════════════════════════");
            System.out.println("  JWCode Backend Ready");
            System.out.println("═══════════════════════════════════════════════════");
            System.out.println("  HTTP API:   http://localhost:" + httpPort);
            System.out.println("  WebSocket:  ws://localhost:" + wsPort + "/ws");
            System.out.println("  Web UI:     http://localhost:" + httpPort);
            System.out.println("═══════════════════════════════════════════════════\n");

            // 添加 shutdown hook 处理 SIGINT/SIGTERM
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down...");
                server.stop();
                System.out.println("Server stopped.");
            }));

            // 保持主线程存活，由 shutdown hook 处理退出
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Startup failed: " + e.getMessage());
        }
    }
}
