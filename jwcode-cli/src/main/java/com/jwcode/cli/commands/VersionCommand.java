package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

/**
 * VersionCommand - 版本信息命令
 */
public class VersionCommand implements Command {
    
    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final String BUILD_DATE = "2026-04-05";
    
    @Override
    public String getName() {
        return "version";
    }
    
    @Override
    public String getDescription() {
        return "显示版本信息";
    }
    
    @Override
    public String getUsage() {
        return "version";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    JwCode 版本信息                           ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        sb.append("版本: ").append(VERSION).append("\n");
        sb.append("构建日期: ").append(BUILD_DATE).append("\n");
        sb.append("Java 版本: ").append(System.getProperty("java.version")).append("\n");
        sb.append("操作系统: ").append(System.getProperty("os.name")).append("\n");
        sb.append("\n");
        sb.append("GitHub: https://github.com/jwcode/jwcode\n");
        sb.append("文档: https://docs.jwcode.dev\n");
        
        return CommandResult.success(sb.toString());
    }
}
