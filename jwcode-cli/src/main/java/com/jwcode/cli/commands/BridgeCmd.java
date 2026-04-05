package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.core.bridge.BridgeServer;
import com.jwcode.core.bridge.BridgeClient;

import java.util.Scanner;

/**
 * Bridge 命令 - 桥接模式（远程执行支持）
 * 
 * 用法：
 *   bridge start [port]    - 启动桥接服务器
 *   bridge connect <url>   - 连接到远程服务器
 *   bridge status <url>    - 查看服务器状态
 */
public class BridgeCmd implements Command {
    
    private volatile BridgeServer runningServer;
    
    @Override
    public String getName() {
        return "bridge";
    }
    
    @Override
    public String getDescription() {
        return "桥接模式 - 远程执行支持";
    }
    
    @Override
    public String getUsage() {
        return "bridge start [port] | bridge connect <url> | bridge status <url>";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            return CommandResult.error(getUsage());
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String subCommand = parts[0];
        String subArgs = parts.length > 1 ? parts[1] : "";
        
        switch (subCommand.toLowerCase()) {
            case "start":
                return handleStart(subArgs);
            case "connect":
                return handleConnect(subArgs);
            case "stop":
                return handleStop();
            case "status":
                return handleStatus(subArgs);
            default:
                return CommandResult.error("未知子命令: " + subCommand);
        }
    }
    
    /**
     * 启动桥接服务器
     */
    private CommandResult handleStart(String args) {
        int port = 8080;
        if (!args.isEmpty()) {
            try {
                port = Integer.parseInt(args.trim());
            } catch (NumberFormatException e) {
                return CommandResult.error("无效端口号: " + args);
            }
        }
        
        try {
            runningServer = new BridgeServer(port);
            runningServer.start();
            
            StringBuilder output = new StringBuilder();
            output.append("\n");
            output.append(CliLogger.GREEN + "✓ 桥接服务器已启动" + CliLogger.RESET).append("\n");
            output.append("  URL: http://localhost:" + port).append("\n");
            output.append("  端点:").append("\n");
            output.append("    POST /bridge/connect   - 创建会话").append("\n");
            output.append("    POST /bridge/message   - 发送消息").append("\n");
            output.append("    GET  /bridge/stream    - SSE 流").append("\n");
            output.append("    GET  /bridge/status    - 服务器状态").append("\n");
            output.append("\n");
            output.append("使用 'bridge stop' 停止服务器").append("\n");
            
            // 在后台保持运行，直到调用 stop
            // 注意：这里不阻塞，服务器在后台运行
            
            return CommandResult.success(output.toString());
            
        } catch (Exception e) {
            return CommandResult.error("启动失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止服务器
     */
    private CommandResult handleStop() {
        if (runningServer == null) {
            return CommandResult.error("服务器未运行");
        }
        
        runningServer.stop();
        runningServer = null;
        return CommandResult.success(CliLogger.GREEN + "✓ 服务器已停止" + CliLogger.RESET);
    }
    
    /**
     * 连接到远程服务器
     */
    private CommandResult handleConnect(String args) {
        if (args.isEmpty()) {
            return CommandResult.error("请指定服务器地址，例如: http://localhost:8080");
        }
        
        String serverUrl = args.trim();
        BridgeClient client = new BridgeClient(serverUrl);
        
        CliLogger.logInfo("正在连接到 " + serverUrl + "...");
        
        if (!client.connect()) {
            return CommandResult.error("连接失败");
        }
        
        StringBuilder output = new StringBuilder();
        output.append(CliLogger.GREEN + "✓ 已连接" + CliLogger.RESET).append("\n");
        output.append("  会话ID: " + client.getSessionId()).append("\n");
        output.append("\n输入消息 (exit 退出):\n");
        
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();
            
            if ("exit".equalsIgnoreCase(input)) {
                break;
            }
            
            String response = client.sendMessage(input);
            System.out.println("< " + response);
        }
        
        client.disconnect();
        return CommandResult.success("已断开连接");
    }
    
    /**
     * 查看服务器状态
     */
    private CommandResult handleStatus(String args) {
        if (runningServer != null) {
            return CommandResult.success(
                CliLogger.GREEN + "✓ 服务器运行中" + CliLogger.RESET + "\n端口: 8080"
            );
        } else {
            return CommandResult.success("服务器未运行");
        }
    }
}
