package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * /summary 命令 - 会话摘要
 * 
 * 生成当前会话的摘要，包括已完成的工作、使用的工具和成本统计。
 */
public class SummaryCommand implements Command {
    
    @Override
    public String getName() {
        return "summary";
    }
    
    @Override
    public String getDescription() {
        return "生成当前会话的摘要";
    }
    
    @Override
    public String getUsage() {
        return "/summary [--brief|--detailed] [--export]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        SummaryOptions options = parseArgs(args);
        
        StringBuilder output = new StringBuilder();
        
        // 会话头部
        output.append("=== 会话摘要 ===\n\n");
        output.append("生成时间：").append(
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ).append("\n");
        
        // 会话基本信息
        output.append("\n--- 会话信息 ---\n\n");
        
        Map<String, Object> sessionInfo = context.getSession().getSessionInfo();
        
        output.append("会话 ID: ").append(sessionInfo.getOrDefault("id", "未知")).append("\n");
        
        if (sessionInfo.containsKey("start_time")) {
            output.append("开始时间：").append(sessionInfo.get("start_time")).append("\n");
        }
        
        long durationMinutes = context.getSession().getDurationMinutes();
        output.append("持续时间：").append(formatDuration(durationMinutes)).append("\n");
        
        // 消息统计
        output.append("\n--- 消息统计 ---\n\n");
        
        int userMessages = context.getSession().getUserMessageCount();
        int assistantMessages = context.getSession().getAssistantMessageCount();
        int totalMessages = userMessages + assistantMessages;
        
        output.append("用户消息：").append(userMessages).append("\n");
        output.append("助手消息：").append(assistantMessages).append("\n");
        output.append("总消息数：").append(totalMessages).append("\n");
        
        // 工具使用统计
        output.append("\n--- 工具使用 ---\n\n");
        
        Map<String, Integer> toolUsage = context.getSession().getToolUsage();
        
        if (toolUsage.isEmpty()) {
            output.append("未使用工具\n");
        } else {
            output.append(String.format("%-25s %10s\n", "工具", "使用次数"));
            output.append("-".repeat(40)).append("\n");
            
            // 按使用次数排序
            List<Map.Entry<String, Integer>> sortedTools = new ArrayList<>(toolUsage.entrySet());
            sortedTools.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            
            for (Map.Entry<String, Integer> entry : sortedTools) {
                output.append(String.format("%-25s %10d\n", entry.getKey(), entry.getValue()));
            }
            
            output.append("\n总工具调用：").append(toolUsage.values().stream().mapToInt(i -> i).sum()).append(" 次\n");
        }
        
        // 成本统计
        output.append("\n--- 成本统计 ---\n\n");
        
        Map<String, Object> costInfo = context.getSession().getCostInfo();
        
        if (costInfo != null && !costInfo.isEmpty()) {
            output.append("输入 Token: ").append(formatNumber((Long) costInfo.getOrDefault("input_tokens", 0L))).append("\n");
            output.append("输出 Token: ").append(formatNumber((Long) costInfo.getOrDefault("output_tokens", 0L))).append("\n");
            output.append("总 Token: ").append(formatNumber((Long) costInfo.getOrDefault("total_tokens", 0L))).append("\n");
            output.append("\n估算成本：$").append(String.format("%.4f", costInfo.getOrDefault("total_cost", 0.0))).append("\n");
        } else {
            output.append("暂无成本数据\n");
        }
        
        // 文件操作统计
        output.append("\n--- 文件操作 ---\n\n");
        
        List<String> modifiedFiles = context.getSession().getModifiedFiles();
        List<String> readFiles = context.getSession().getReadFiles();
        
        output.append("读取文件：").append(readFiles.size()).append(" 个\n");
        output.append("修改文件：").append(modifiedFiles.size()).append(" 个\n");
        
        if (!modifiedFiles.isEmpty() && options.detailed) {
            output.append("\n修改的文件列表:\n");
            for (String file : modifiedFiles) {
                output.append("  - ").append(file).append("\n");
            }
        }
        
        // 任务完成情况
        output.append("\n--- 任务状态 ---\n\n");
        
        List<Map<String, Object>> tasks = context.getSession().getTasks();
        
        if (tasks.isEmpty()) {
            output.append("暂无任务\n");
        } else {
            int completedTasks = 0;
            int pendingTasks = 0;
            
            for (Map<String, Object> task : tasks) {
                String status = (String) task.get("status");
                if ("completed".equals(status)) {
                    completedTasks++;
                } else {
                    pendingTasks++;
                }
            }
            
            output.append("总任务数：").append(tasks.size()).append("\n");
            output.append("已完成：").append(completedTasks).append("\n");
            output.append("进行中：").append(pendingTasks).append("\n");
            
            if (options.detailed && !tasks.isEmpty()) {
                output.append("\n任务列表:\n");
                for (Map<String, Object> task : tasks) {
                    output.append("  - ").append(task.get("description"));
                    output.append(" [").append(task.get("status")).append("]\n");
                }
            }
        }
        
        // 会话亮点（如果有）
        output.append("\n--- 会话亮点 ---\n\n");
        
        List<String> highlights = context.getSession().getHighlights();
        
        if (highlights.isEmpty()) {
            output.append("暂无亮点记录\n");
        } else {
            for (int i = 0; i < highlights.size(); i++) {
                output.append((i + 1)).append(". ").append(highlights.get(i)).append("\n");
            }
        }
        
        // 导出选项
        if (options.export) {
            output.append("\n---\n\n");
            output.append("提示：可以使用 /export 命令将会话导出为文件。\n");
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 解析参数
     */
    private SummaryOptions parseArgs(String args) {
        SummaryOptions options = new SummaryOptions();
        
        if (args == null || args.trim().isEmpty()) {
            return options;
        }
        
        String[] parts = args.trim().split("\\s+");
        
        for (String part : parts) {
            if ("--brief".equals(part)) {
                options.brief = true;
            } else if ("--detailed".equals(part)) {
                options.detailed = true;
            } else if ("--export".equals(part)) {
                options.export = true;
            }
        }
        
        return options;
    }
    
    /**
     * 格式化时长
     */
    private String formatDuration(long minutes) {
        if (minutes < 60) {
            return minutes + " 分钟";
        } else {
            long hours = minutes / 60;
            long mins = minutes % 60;
            return hours + " 小时 " + mins + " 分钟";
        }
    }
    
    /**
     * 格式化数字
     */
    private String formatNumber(long num) {
        if (num >= 1000000) {
            return String.format("%.1fM", num / 1000000.0);
        } else if (num >= 1000) {
            return String.format("%.1fK", num / 1000.0);
        } else {
            return String.valueOf(num);
        }
    }
    
    /**
     * 摘要选项类
     */
    private static class SummaryOptions {
        boolean brief;
        boolean detailed;
        boolean export;
    }
}