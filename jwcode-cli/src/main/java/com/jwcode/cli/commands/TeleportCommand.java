package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * TeleportCommand - /teleport 命令
 * 
 * 功能说明：
 * 远程传输，快速切换到远程项目或环境。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/teleport", description = "远程传输")
public class TeleportCommand implements Runnable {
    
    @Parameters(index = "0", description = "目标位置或操作", arity = "0..1")
    private String destination;
    
    @Option(names = {"-l", "--list"}, description = "列出可用的远程位置")
    private boolean listOnly;
    
    @Option(names = {"-s", "--save"}, description = "保存当前位置")
    private boolean saveCurrent;
    
    @Option(names = {"-n", "--name"}, description = "位置名称")
    private String name;
    
    @Option(names = {"-p", "--path"}, description = "路径")
    private String path;
    
    @Option(names = {"-r", "--return"}, description = "返回原位置")
    private boolean returnToOrigin;
    
    private static String savedLocation = null;
    private static String currentLocation = null;
    
    @Override
    public void run() {
        if (listOnly) {
            listLocations();
            return;
        }
        
        if (saveCurrent) {
            saveLocation();
            return;
        }
        
        if (returnToOrigin) {
            returnToOrigin();
            return;
        }
        
        if (destination == null) {
            showTeleportStatus();
            return;
        }
        
        teleportTo(destination);
    }
    
    private void showTeleportStatus() {
        System.out.println("=== 远程传输状态 ===");
        System.out.println();
        System.out.println("当前位置：" + (currentLocation != null ? currentLocation : "本地"));
        System.out.println("保存位置：" + (savedLocation != null ? savedLocation : "无"));
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /teleport <location>  传输到指定位置");
        System.out.println("  /teleport -l          列出可用位置");
        System.out.println("  /teleport -s -n <name> 保存当前位置");
        System.out.println("  /teleport -r          返回原位置");
    }
    
    private void listLocations() {
        System.out.println("=== 可用的远程位置 ===");
        System.out.println();
        System.out.println("  production  - 生产环境");
        System.out.println("  staging     - 预发布环境");
        System.out.println("  dev         - 开发环境");
        System.out.println("  local       - 本地环境");
        System.out.println();
        if (savedLocation != null) {
            System.out.println("已保存位置：");
            System.out.println("  " + name + " - " + savedLocation);
        }
    }
    
    private void saveLocation() {
        String locationName = name != null ? name : "saved";
        savedLocation = currentLocation != null ? currentLocation : "local";
        System.out.println("已保存位置：" + locationName + " -> " + savedLocation);
    }
    
    private void teleportTo(String destination) {
        System.out.println("正在传输到：" + destination + "...");
        
        // 模拟传输
        currentLocation = destination;
        
        System.out.println("传输完成！");
        System.out.println("当前位置：" + destination);
        System.out.println();
        System.out.println("提示：使用 /teleport -r 返回原位置");
    }
    
    private void returnToOrigin() {
        if (savedLocation == null) {
            System.out.println("没有保存的位置");
            return;
        }
        
        System.out.println("正在返回：" + savedLocation + "...");
        currentLocation = savedLocation;
        System.out.println("已返回原位置");
    }
}