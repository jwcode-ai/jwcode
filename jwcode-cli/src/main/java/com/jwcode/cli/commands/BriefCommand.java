package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * BriefCommand - /brief 命令
 * 
 * 功能说明：
 * 简短模式，简化输出。启用后 AI 将提供更简洁的回答。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/brief", description = "简短模式，简化输出")
public class BriefCommand implements Runnable {
    
    @Option(names = {"-e", "--enable"}, description = "启用简短模式")
    private boolean enable;
    
    @Option(names = {"-d", "--disable"}, description = "禁用简短模式")
    private boolean disable;
    
    @Option(names = {"-s", "--status"}, description = "查看当前状态")
    private boolean status;
    
    private static boolean briefModeEnabled = false;
    
    @Override
    public void run() {
        if (status || (!enable && !disable)) {
            showStatus();
            return;
        }
        
        if (enable) {
            briefModeEnabled = true;
            System.out.println("简短模式已启用 - AI 将提供简洁的回答");
        } else if (disable) {
            briefModeEnabled = false;
            System.out.println("简短模式已禁用 - AI 将提供详细的回答");
        }
    }
    
    private void showStatus() {
        System.out.println("简短模式：" + (briefModeEnabled ? "已启用" : "已禁用"));
    }
    
    public static boolean isBriefModeEnabled() {
        return briefModeEnabled;
    }
}