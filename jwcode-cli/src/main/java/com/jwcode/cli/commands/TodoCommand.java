package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

import java.util.ArrayList;
import java.util.List;

/**
 * TodoCommand - 待办事项管理命令
 */
public class TodoCommand implements Command {
    
    private static final List<String> todos = new ArrayList<>();
    
    @Override
    public String getName() { return "todo"; }
    
    @Override
    public String getDescription() { return "管理待办事项"; }
    
    @Override
    public String getUsage() { return "/todo [add|list|done] [内容]"; }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                      待办事项                              ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        
        if (args == null || args.trim().isEmpty()) {
            // 列出待办
            if (todos.isEmpty()) {
                sb.append("暂无待办事项\n");
            } else {
                sb.append("待办列表:\n");
                for (int i = 0; i < todos.size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(todos.get(i)).append("\n");
                }
            }
            sb.append("\n使用 '/todo add 内容' 添加待办\n");
            return CommandResult.success(sb.toString());
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String action = parts[0];
        String content = parts.length > 1 ? parts[1] : "";
        
        switch (action) {
            case "add":
            case "a":
                if (!content.isEmpty()) {
                    todos.add(content);
                    sb.append("✓ 已添加: ").append(content).append("\n");
                } else {
                    sb.append("请提供待办内容\n");
                }
                break;
            case "list":
            case "ls":
                if (todos.isEmpty()) {
                    sb.append("暂无待办事项\n");
                } else {
                    sb.append("待办列表:\n");
                    for (int i = 0; i < todos.size(); i++) {
                        sb.append("  ").append(i + 1).append(". ").append(todos.get(i)).append("\n");
                    }
                }
                break;
            case "done":
            case "d":
                try {
                    int index = Integer.parseInt(content) - 1;
                    if (index >= 0 && index < todos.size()) {
                        String removed = todos.remove(index);
                        sb.append("✓ 已完成: ").append(removed).append("\n");
                    } else {
                        sb.append("无效的序号\n");
                    }
                } catch (NumberFormatException e) {
                    sb.append("请提供有效的序号\n");
                }
                break;
            case "clear":
            case "c":
                int count = todos.size();
                todos.clear();
                sb.append("✓ 已清除 ").append(count).append(" 项待办\n");
                break;
            default:
                // 默认添加
                todos.add(args.trim());
                sb.append("✓ 已添加: ").append(args.trim()).append("\n");
        }
        
        return CommandResult.success(sb.toString());
    }
}
