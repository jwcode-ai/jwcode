package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.web.WebServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebCommand - 启动 Web UI（前后端集成）
 * 
 * 用法: web [port]
 * 
 * 启动内容：
 * 1. 后端 API Server (WebServer) - 端口 8080
 * 2. 前端开发服务器 (npm run dev) - 端口 5173
 * 3. 自动打开浏览器
 * 
 * 注意：Web 服务在同 JVM 中运行，Ctrl+C 可完全关闭所有进程
 */
public class WebCommand implements Command {
    
    private static final CliLogger logger = CliLogger.getInstance();
    private static final AtomicBoolean serverRunning = new AtomicBoolean(false);
    
    // WebServer 实例（在同 JVM 中运行）
    private WebServer webServer;
    // 前端进程（仍需独立启动 npm）
    private Process frontendProcess;
    
    @Override
    public String getName() {
        return "web";
    }
    
    @Override
    public String getDescription() {
        return "启动 Web UI 界面（前后端集成）";
    }
    
    @Override
    public String getUsage() {
        return "web [port]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        int apiPort = 8080;
        int frontendPort = 5173;
        
        if (args != null && !args.trim().isEmpty()) {
            try {
                apiPort = Integer.parseInt(args.trim());
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号: " + args);
            }
        }
        
        // 检查是否已经在运行
        if (serverRunning.get()) {
            System.out.println("Web 服务已在运行中...");
            return CommandResult.success("Web 服务已在运行中");
        }
        
        try {
            printHeader();
            
            // 1. 启动后端 API Server（在同 JVM 中）
            startApiServer(apiPort);
            
            // 2. 启动前端
            startFrontend(frontendPort);
            
            // 3. 等待服务就绪
            waitForServices(apiPort, frontendPort);
            
            // 4. 打开浏览器
            openBrowser(frontendPort);
            
            printSuccess(frontendPort);
            
            serverRunning.set(true);
            
            // 保持运行，等待 Ctrl+C
            Thread.currentThread().join();
            
            return CommandResult.success("Web 服务已停止");
            
        } catch (InterruptedException e) {
            // 收到中断信号，正常退出
            System.out.println("\n收到停止信号，正在关闭...");
            stopServices();
            return CommandResult.success("Web 服务已停止");
        } catch (Exception e) {
            logger.error("启动 Web 服务失败", e);
            stopServices();
            return CommandResult.error("启动 Web 服务失败: " + e.getMessage());
        }
    }
    
    private void printHeader() {
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("  🌐 正在启动 JwCode Web...");
        System.out.println("═══════════════════════════════════════════════════");
    }
    
    private void startApiServer(int port) throws Exception {
        System.out.print("  📦 启动后端 API Server...");
        
        // 在同 JVM 中启动 WebServer
        webServer = new WebServer(port);
        webServer.start();
        
        System.out.println(" ✅");
    }
    
    private void startFrontend(int port) throws Exception {
        System.out.print("  🎨 启动前端开发服务器...");
        
        // 查找 jwcode-web 目录
        String userDir = System.getProperty("user.dir");
        java.nio.file.Path webPath = java.nio.file.Paths.get(userDir, "jwcode-web");
        
        if (!java.nio.file.Files.exists(webPath)) {
            // 尝试父目录
            webPath = java.nio.file.Paths.get(userDir, "..", "jwcode-web");
        }
        
        if (!java.nio.file.Files.exists(webPath)) {
            System.out.println(" ⚠️  (jwcode-web 目录未找到，跳过)");
            return;
        }
        
        // Windows 上使用 cmd /c npm.cmd 启动
        ProcessBuilder pb;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb = new ProcessBuilder(
                "cmd", "/c", "npm.cmd", "run", "dev", "--", "--port", String.valueOf(port)
            );
        } else {
            pb = new ProcessBuilder(
                "npm", "run", "dev", "--", "--port", String.valueOf(port)
            );
        }
        pb.directory(webPath.toFile());
        pb.redirectErrorStream(true);
        // WebServer 使用端口 8080 (HTTP) + 8081 (WebSocket)
        pb.environment().put("VITE_API_URL", "http://localhost:" + port);
        pb.environment().put("VITE_WS_URL", "ws://localhost:" + (port + 1));
        
        frontendProcess = pb.start();
        
        // 异步读取输出
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(frontendProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("    [Web] " + line);
                }
            } catch (Exception e) {
                // 忽略
            }
        }).start();
        
        System.out.println(" ✅");
    }
    
    private void waitForServices(int apiPort, int frontendPort) throws Exception {
        System.out.print("  ⏳ 等待服务就绪...");
        
        // 等待后端 API
        int retries = 30;
        while (retries > 0) {
            try {
                URL url = new URL("http://localhost:" + apiPort + "/api/health");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (conn.getResponseCode() == 200) {
                    break;
                }
            } catch (Exception e) {
                // 继续等待
            }
            retries--;
            Thread.sleep(500);
        }
        
        if (retries == 0) {
            System.out.println(" ⚠️  (后端 API 启动超时)");
        } else {
            System.out.print(" ✅");
        }
        
        // 等待前端（检查端口）
        retries = 30;
        while (retries > 0) {
            try {
                URL url = new URL("http://localhost:" + frontendPort);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (conn.getResponseCode() == 200 || conn.getResponseCode() == 304) {
                    break;
                }
            } catch (Exception e) {
                // 继续等待
            }
            retries--;
            Thread.sleep(500);
        }
        
        if (retries == 0) {
            System.out.println(" ⚠️  (前端启动超时)");
        } else {
            System.out.print(" ✅\n");
        }
    }
    
    private void openBrowser(int port) {
        System.out.print("  🌐 正在打开浏览器...");
        
        try {
            String url = "http://localhost:" + port;
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("win")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec("open " + url);
            } else {
                Runtime.getRuntime().exec("xdg-open " + url);
            }
            
            System.out.println(" ✅");
        } catch (Exception e) {
            System.out.println(" ⚠️  (无法自动打开浏览器，请手动访问)");
        }
    }
    
    private void printSuccess(int port) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  ✅ JwCode Web 已启动！");
        System.out.println("  📍 访问地址: http://localhost:" + port);
        System.out.println("  💡 按 Ctrl+C 停止服务");
        System.out.println("═══════════════════════════════════════════════════\n");
    }
    
    private void stopServices() {
        serverRunning.set(false);
        
        System.out.println("\n正在停止 Web 服务...");
        
        // 停止后端 WebServer（同 JVM）
        if (webServer != null) {
            webServer.stop();
            System.out.println("  ✅ 后端 API 已停止");
        }
        
        // 停止前端进程
        if (frontendProcess != null && frontendProcess.isAlive()) {
            frontendProcess.destroyForcibly();
            System.out.println("  ✅ 前端已停止");
        }
    }
}
