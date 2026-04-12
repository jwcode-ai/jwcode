package com.jwcode.web;

import java.util.Scanner;

/**
 * Web UI 启动器
 */
public class WebLauncher {
    
    public static void main(String[] args) {
        int port = 8081;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号，使用默认端口 8080");
            }
        }
        
        try {
            WebServer server = new WebServer(port);
            server.start();
            
            System.out.println("\n═══════════════════════════════════════════════════");
            System.out.println("  🌐 JwCode Web 已启动");
            System.out.println("═══════════════════════════════════════════════════");
            System.out.println("  访问地址: http://localhost:" + port);
            System.out.println("  按 Enter 键停止服务器");
            System.out.println("═══════════════════════════════════════════════════\n");
            
            // 等待用户按 Enter
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            
            System.out.println("\n正在关闭服务器...");
            server.stop();
            System.out.println("服务器已关闭");
            
        } catch (Exception e) {
            System.err.println("启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
