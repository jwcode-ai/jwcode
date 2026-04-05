package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * RewindCommand - /rewind 命令
 * 
 * 功能说明：
 * 回退会话，返回到之前的对话状态。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/rewind", description = "回退会话")
public class RewindCommand implements Runnable {
    
    @Option(names = {"-n", "--num"}, description = "回退的消息数量", defaultValue = "1")
    private int numMessages;
    
    @Option(names = {"-s", "--status"}, description = "显示可回退点")
    private boolean showStatus;
    
    @Option(names = {"-y", "--yes"}, description = "确认回退，不提示")
    private boolean autoConfirm;
    
    private static int currentPosition = 0;
    
    @Override
    public void run() {
        if (showStatus) {
            showRewindStatus();
            return;
        }
        
        if (currentPosition <= 0) {
            System.out.println("没有可回退的位置");
            return;
        }
        
        if (!autoConfirm) {
            System.out.println("这将回退 " + numMessages + " 条消息");
            System.out.println("回退后，之后的对话将被清除。");
            System.out.println();
        }
        
        currentPosition = Math.max(0, currentPosition - numMessages);
        System.out.println("已回退 " + numMessages + " 条消息");
        System.out.println("当前位置：" + currentPosition);
    }
    
    private void showRewindStatus() {
        System.out.println("=== 会话回退状态 ===");
        System.out.println();
        System.out.println("当前位置：" + currentPosition);
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /rewind -n <num>  回退指定数量的消息");
        System.out.println("  /rewind -s        显示回退状态");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  /rewind -n 3  回退 3 条消息");
    }
    
    public static int getCurrentPosition() {
        return currentPosition;
    }
    
    public static void advancePosition() {
        currentPosition++;
    }
}