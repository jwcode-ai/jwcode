package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * IdeCommand - /ide 命令
 * 
 * 功能说明：
 * IDE 集成，管理与 IDE 的连接和集成设置。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/ide", description = "IDE 集成")
public class IdeCommand implements Runnable {
    
    @Option(names = {"-s", "--status"}, description = "显示 IDE 连接状态")
    private boolean showStatus;
    
    @Option(names = {"-c", "--connect"}, description = "连接到 IDE")
    private boolean connect;
    
    @Option(names = {"-d", "--disconnect"}, description = "断开 IDE 连接")
    private boolean disconnect;
    
    @Option(names = {"-p", "--port"}, description = "IDE 端口号", defaultValue = "6329")
    private int port;
    
    @Option(names = {"-i", "--ide"}, description = "IDE 类型 (vscode, intellij, eclipse)")
    private String ideType;
    
    private static boolean connected = false;
    private static String connectedIde = null;
    private static int connectedPort = 6329;
    
    @Override
    public void run() {
        if (showStatus || (!connect && !disconnect && ideType == null)) {
            showIdeStatus();
            return;
        }
        
        if (connect) {
            connectToIde();
        } else if (disconnect) {
            disconnectFromIde();
        }
    }
    
    private void showIdeStatus() {
        System.out.println("=== IDE 集成状态 ===");
        System.out.println();
        System.out.println("连接状态：" + (connected ? "已连接" : "未连接"));
        if (connected) {
            System.out.println("IDE 类型：" + connectedIde);
            System.out.println("端口：" + connectedPort);
        }
        System.out.println();
        System.out.println("支持的 IDE:");
        System.out.println("  - Visual Studio Code");
        System.out.println("  - IntelliJ IDEA");
        System.out.println("  - Eclipse");
    }
    
    private void connectToIde() {
        String targetIde = ideType != null ? ideType : "vscode";
        System.out.println("正在连接到 " + targetIde + " (端口：" + port + ")...");
        
        // 模拟连接
        connected = true;
        connectedIde = targetIde;
        connectedPort = port;
        
        System.out.println("已连接到 " + targetIde);
        System.out.println();
        System.out.println("IDE 集成功能:");
        System.out.println("  - 文件同步");
        System.out.println("  - 代码导航");
        System.out.println("  - 错误提示");
        System.out.println("  - 重构建议");
    }
    
    private void disconnectFromIde() {
        if (!connected) {
            System.out.println("未连接到任何 IDE");
            return;
        }
        
        System.out.println("正在断开与 " + connectedIde + " 的连接...");
        connected = false;
        connectedIde = null;
        
        System.out.println("已断开连接");
    }
    
    public static boolean isConnected() {
        return connected;
    }
    
    public static String getConnectedIde() {
        return connectedIde;
    }
}