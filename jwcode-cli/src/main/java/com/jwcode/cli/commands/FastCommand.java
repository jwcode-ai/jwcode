package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * FastCommand - /fast 命令
 * 
 * 功能说明：
 * 快速模式，使用更快的模型或降低回答质量以换取速度。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/fast", description = "快速模式")
public class FastCommand implements Runnable {
    
    @Option(names = {"-e", "--enable"}, description = "启用快速模式")
    private boolean enable;
    
    @Option(names = {"-d", "--disable"}, description = "禁用快速模式")
    private boolean disable;
    
    @Option(names = {"-s", "--status"}, description = "查看当前状态")
    private boolean status;
    
    private static boolean fastModeEnabled = false;
    
    @Override
    public void run() {
        if (status || (!enable && !disable)) {
            showStatus();
            return;
        }
        
        if (enable) {
            fastModeEnabled = true;
            System.out.println("快速模式已启用 - 将使用更快的响应策略");
        } else if (disable) {
            fastModeEnabled = false;
            System.out.println("快速模式已禁用 - 将使用标准响应策略");
        }
    }
    
    private void showStatus() {
        System.out.println("快速模式：" + (fastModeEnabled ? "已启用" : "已禁用"));
        System.out.println();
        System.out.println("启用快速模式后：");
        System.out.println("  - 使用更简洁的提示词");
        System.out.println("  - 减少不必要的分析");
        System.out.println("  - 优先响应速度而非深度");
    }
    
    public static boolean isFastModeEnabled() {
        return fastModeEnabled;
    }
}