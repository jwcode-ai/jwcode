package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * RemoteCommand - /remote 命令
 * 
 * 功能说明：
 * 远程会话，管理远程连接和会话。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/remote", description = "远程会话")
public class RemoteCommand implements Runnable {
    
    @Option(names = {"-c", "--connect"}, description = "连接到远程主机")
    private boolean connect;
    
    @Option(names = {"-d", "--disconnect"}, description = "断开远程连接")
    private boolean disconnect;
    
    @Option(names = {"-s", "--status"}, description = "显示连接状态")
    private boolean showStatus;
    
    @Option(names = {"-H", "--host"}, description = "远程主机地址")
    private String host;
    
    @Option(names = {"-P", "--port"}, description = "远程端口", defaultValue = "22")
    private int port;
    
    @Option(names = {"-u", "--user"}, description = "用户名")
    private String user;
    
    private static boolean connected = false;
    private static String connectedHost = null;
    
    @Override
    public void run() {
        if (showStatus || (!connect && !disconnect && host == null)) {
            showRemoteStatus();
            return;
        }
        
        if (connect) {
            connectToRemote();
        } else if (disconnect) {
            disconnectFromRemote();
        }
    }
    
    private void showRemoteStatus() {
        System.out.println("=== 远程会话状态 ===");
        System.out.println();
        System.out.println("连接状态：" + (connected ? "已连接" : "未连接"));
        if (connected && connectedHost != null) {
            System.out.println("主机：" + connectedHost);
        }
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /remote -c -H <host> -u <user>  连接到远程主机");
        System.out.println("  /remote -d                       断开远程连接");
    }
    
    private void connectToRemote() {
        if (host == null) {
            System.out.println("错误：需要指定主机地址");
            System.out.println("用法：/remote -c -H <host> -u <user>");
            return;
        }
        
        String username = user != null ? user : "root";
        System.out.println("正在连接到 " + username + "@" + host + ":" + port + "...");
        
        // 模拟连接
        connected = true;
        connectedHost = host;
        
        System.out.println("已连接到 " + host);
        System.out.println();
        System.out.println("远程会话功能:");
        System.out.println("  - 执行远程命令");
        System.out.println("  - 文件传输");
        System.out.println("  - 远程调试");
    }
    
    private void disconnectFromRemote() {
        if (!connected) {
            System.out.println("未连接到任何远程主机");
            return;
        }
        
        System.out.println("正在断开与 " + connectedHost + " 的连接...");
        connected = false;
        connectedHost = null;
        
        System.out.println("已断开连接");
    }
    
    public static boolean isConnected() {
        return connected;
    }
    
    public static String getConnectedHost() {
        return connectedHost;
    }
}