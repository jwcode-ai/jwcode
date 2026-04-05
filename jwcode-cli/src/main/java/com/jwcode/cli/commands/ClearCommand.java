package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

/**
 * /clear 命令 - 清除上下文
 * 
 * 清除当前会话的上下文，可以选择保留部分信息。
 */
public class ClearCommand implements Command {
    
    @Override
    public String getName() {
        return "clear";
    }
    
    @Override
    public String getDescription() {
        return "清除当前会话的上下文";
    }
    
    @Override
    public String getUsage() {
        return "/clear [--messages] [--tools] [--memory] [--all]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        ClearOptions options = parseArgs(args);
        
        StringBuilder output = new StringBuilder();
        output.append("清除操作\n\n");
        
        boolean cleared = false;
        
        // 清除消息
        if (options.messages || options.all) {
            context.getSession().clearMessages();
            output.append("✓ 已清除消息历史\n");
            cleared = true;
        }
        
        // 清除工具历史
        if (options.tools || options.all) {
            context.getSession().clearToolHistory();
            output.append("✓ 已清除工具使用历史\n");
            cleared = true;
        }
        
        // 清除记忆
        if (options.memory || options.all) {
            context.getSession().clearMemory();
            output.append("✓ 已清除会话记忆\n");
            cleared = true;
        }
        
        // 如果没有指定选项，默认只清除屏幕
        if (!cleared) {
            output.append("提示：使用以下选项清除不同类型的内容：\n");
            output.append("  --messages  清除消息历史\n");
            output.append("  --tools     清除工具使用历史\n");
            output.append("  --memory    清除会话记忆\n");
            output.append("  --all       清除所有内容\n");
            output.append("\n或者使用 /clear screen 仅清屏。\n");
        } else if (options.all) {
            output.append("\n⚠️  已清除所有会话数据，此操作不可恢复。\n");
        }
        
        // 清屏（如果终端支持）
        if (options.screen || (!options.messages && !options.tools && !options.memory && !options.all)) {
            clearScreen();
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 解析参数
     */
    private ClearOptions parseArgs(String args) {
        ClearOptions options = new ClearOptions();
        
        if (args == null || args.trim().isEmpty()) {
            return options;
        }
        
        String[] parts = args.trim().split("\\s+");
        
        for (String part : parts) {
            if ("--messages".equals(part) || "-m".equals(part)) {
                options.messages = true;
            } else if ("--tools".equals(part) || "-t".equals(part)) {
                options.tools = true;
            } else if ("--memory".equals(part) || "--mem".equals(part)) {
                options.memory = true;
            } else if ("--all".equals(part) || "-a".equals(part)) {
                options.all = true;
            } else if ("screen".equals(part)) {
                options.screen = true;
            }
        }
        
        return options;
    }
    
    /**
     * 清屏
     */
    private void clearScreen() {
        // 尝试使用 ANSI 转义码清屏
        System.out.print("\033[2J\033[H");
        System.out.flush();
        
        // 如果 ANSI 不可用，尝试使用系统命令
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (Exception e) {
            // 忽略清屏失败
        }
    }
    
    /**
     * 清除选项类
     */
    private static class ClearOptions {
        boolean messages;
        boolean tools;
        boolean memory;
        boolean all;
        boolean screen;
    }
}