package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CompactCommand - /compact 命令
 * 
 * 功能说明：
 * 压缩会话历史，减少上下文占用。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/compact", description = "压缩会话历史")
public class CompactCommand implements Runnable {
    
    @Option(names = {"-f", "--force"}, description = "强制压缩，不确认")
    private boolean force;
    
    @Option(names = {"-l", "--level"}, description = "压缩级别 (1-3, 默认：2)", defaultValue = "2")
    private int level;
    
    @Override
    public void run() {
        System.out.println("=== 压缩会话历史 ===");
        System.out.println();
        System.out.println("压缩级别：" + level);
        System.out.println();
        
        if (!force) {
            System.out.println("此操作将压缩当前会话历史，减少上下文占用。");
            System.out.println("压缩后，部分历史消息将被摘要替代。");
            System.out.println();
        }
        
        System.out.println("正在压缩会话...");
        System.out.println("(压缩功能需要 AI 后端支持)");
    }
}