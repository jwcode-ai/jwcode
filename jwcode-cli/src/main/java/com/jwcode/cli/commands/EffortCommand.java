package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * EffortCommand - /effort 命令
 * 
 * 功能说明：
 * 设置 AI 努力程度，控制回答的详细程度和深度。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/effort", description = "设置努力程度")
public class EffortCommand implements Runnable {
    
    @Option(names = {"-l", "--level"}, description = "努力级别 (1-5, 默认：3)")
    private Integer level;
    
    @Option(names = {"-s", "--status"}, description = "显示当前状态")
    private boolean status;
    
    private static int currentLevel = 3;
    
    @Override
    public void run() {
        if (status || level == null) {
            showStatus();
            return;
        }
        
        if (level < 1 || level > 5) {
            System.out.println("错误：努力级别必须在 1-5 之间");
            return;
        }
        
        currentLevel = level;
        System.out.println("努力级别已设置为：" + currentLevel);
        System.out.println(getLevelDescription(currentLevel));
    }
    
    private void showStatus() {
        System.out.println("=== 努力程度设置 ===");
        System.out.println();
        System.out.println("当前级别：" + currentLevel);
        System.out.println(getLevelDescription(currentLevel));
        System.out.println();
        System.out.println("级别说明:");
        System.out.println("  1 - 最低努力：最简单的回答");
        System.out.println("  2 - 低努力：简要回答");
        System.out.println("  3 - 中等努力：平衡的回答（默认）");
        System.out.println("  4 - 高努力：详细的回答");
        System.out.println("  5 - 最高努力：最全面深入的分析");
    }
    
    private String getLevelDescription(int level) {
        switch (level) {
            case 1: return "最低努力：最简单的回答";
            case 2: return "低努力：简要回答";
            case 3: return "中等努力：平衡的回答";
            case 4: return "高努力：详细的回答";
            case 5: return "最高努力：最全面深入的分析";
            default: return "未知级别";
        }
    }
    
    public static int getCurrentLevel() {
        return currentLevel;
    }
}