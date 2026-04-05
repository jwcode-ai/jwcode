package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * ContextCommand - /context 命令
 * 
 * 功能说明：
 * 管理上下文窗口，查看和调整上下文使用情况。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/context", description = "管理上下文窗口")
public class ContextCommand implements Runnable {
    
    @Option(names = {"-s", "--status"}, description = "显示上下文使用状态")
    private boolean showStatus;
    
    @Option(names = {"-l", "--limit"}, description = "设置上下文限制（tokens）")
    private Integer limit;
    
    @Option(names = {"-c", "--clear"}, description = "清除上下文历史")
    private boolean clear;
    
    private static int contextLimit = 200000;
    private static int contextUsed = 0;
    
    @Override
    public void run() {
        if (showStatus || (!clear && limit == null)) {
            showContextStatus();
            return;
        }
        
        if (clear) {
            contextUsed = 0;
            System.out.println("上下文历史已清除");
        }
        
        if (limit != null) {
            contextLimit = limit;
            System.out.println("上下文限制已设置为：" + contextLimit + " tokens");
        }
    }
    
    private void showContextStatus() {
        System.out.println("=== 上下文窗口状态 ===");
        System.out.println();
        System.out.println("已使用：" + contextUsed + " tokens");
        System.out.println("限制：" + contextLimit + " tokens");
        System.out.println("剩余：" + (contextLimit - contextUsed) + " tokens");
        System.out.println("使用率：" + (contextUsed * 100 / contextLimit) + "%");
    }
    
    public static int getContextLimit() {
        return contextLimit;
    }
    
    public static int getContextUsed() {
        return contextUsed;
    }
    
    public static void addContextUsed(int tokens) {
        contextUsed += tokens;
    }
}