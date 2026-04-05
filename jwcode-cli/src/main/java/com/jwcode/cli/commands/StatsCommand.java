package com.jwcode.cli.commands;

import com.jwcode.core.service.AnalyticsService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * StatsCommand - /stats 命令
 * 
 * 功能说明：
 * 显示使用统计信息，包括使用时长、命令频率、Token 消耗等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/stats", description = "使用统计", 
         aliases = {"/statistics", "/usage"})
public class StatsCommand implements Runnable {
    
    @Option(names = {"-d", "--days"}, description = "统计天数", defaultValue = "7")
    private int days;
    
    @Option(names = {"-j", "--json"}, description = "以 JSON 格式输出")
    private boolean jsonOutput;
    
    @Option(names = {"-a", "--all"}, description = "显示所有统计信息")
    private boolean showAll;
    
    private final AnalyticsService analyticsService;
    
    public StatsCommand() {
        this.analyticsService = AnalyticsService.getInstance();
    }
    
    @Override
    public void run() {
        if (jsonOutput) {
            outputJson();
        } else {
            outputFormatted();
        }
    }
    
    /**
     * 格式化输出统计信息
     */
    private void outputFormatted() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║         JWCode 使用统计                ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        // 时间范围
        System.out.println("统计范围：过去 " + days + " 天");
        System.out.println();
        
        // 使用时长统计
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ 使用时长统计                        │");
        System.out.println("├─────────────────────────────────────┤");
        long totalMinutes = getTotalUsageMinutes();
        System.out.println("│ 总使用时长：" + formatDuration(totalMinutes));
        System.out.println("│ 日均使用：" + formatDuration(totalMinutes / days));
        System.out.println("│ 最长会话：" + formatDuration(getLongestSession()));
        System.out.println("└─────────────────────────────────────┘");
        System.out.println();
        
        // 命令使用统计
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ 命令使用统计                        │");
        System.out.println("├─────────────────────────────────────┤");
        Map<String, Integer> commandStats = getCommandUsageStats();
        for (Map.Entry<String, Integer> entry : commandStats.entrySet()) {
            System.out.printf("│ %-20s %5d 次        │\n", entry.getKey(), entry.getValue());
        }
        System.out.println("└─────────────────────────────────────┘");
        System.out.println();
        
        // Token 消耗统计
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ Token 消耗统计                       │");
        System.out.println("├─────────────────────────────────────┤");
        System.out.println("│ 总消耗：" + getTotalTokens() + " tokens");
        System.out.println("│ 日均消耗：" + (getTotalTokens() / days) + " tokens");
        System.out.println("└─────────────────────────────────────┘");
        System.out.println();
        
        // 活跃时段统计
        if (showAll) {
            System.out.println("┌─────────────────────────────────────┐");
            System.out.println("│ 活跃时段统计                        │");
            System.out.println("├─────────────────────────────────────┤");
            Map<String, Integer> hourlyStats = getHourlyUsageStats();
            for (int i = 0; i < 24; i++) {
                int count = hourlyStats.getOrDefault(String.valueOf(i), 0);
                String bar = "█".repeat(Math.min(count, 20));
                System.out.printf("│ %02d:00 %-20s %3d     │\n", i, bar, count);
            }
            System.out.println("└─────────────────────────────────────┘");
        }
    }
    
    /**
     * JSON 格式输出
     */
    private void outputJson() {
        System.out.println("{");
        System.out.println("  \"period\": {");
        System.out.println("    \"days\": " + days + ",");
        System.out.println("    \"startDate\": \"" + LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_DATE) + "\",");
        System.out.println("    \"endDate\": \"" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + "\"");
        System.out.println("  },");
        System.out.println("  \"usageDuration\": {");
        System.out.println("    \"totalMinutes\": " + getTotalUsageMinutes() + ",");
        System.out.println("    \"longestSession\": " + getLongestSession());
        System.out.println("  },");
        System.out.println("  \"tokenUsage\": {");
        System.out.println("    \"total\": " + getTotalTokens());
        System.out.println("  },");
        System.out.println("  \"commandUsage\": {");
        Map<String, Integer> commandStats = getCommandUsageStats();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : commandStats.entrySet()) {
            if (!first) System.out.println(",");
            first = false;
            System.out.print("    \"" + entry.getKey() + "\": " + entry.getValue());
        }
        System.out.println();
        System.out.println("  }");
        System.out.println("}");
    }
    
    /**
     * 获取总使用时长（分钟）
     */
    private long getTotalUsageMinutes() {
        // 模拟数据
        return days * 30 + 45;
    }
    
    /**
     * 获取最长会话时长（分钟）
     */
    private long getLongestSession() {
        // 模拟数据
        return 120;
    }
    
    /**
     * 获取命令使用统计
     */
    private Map<String, Integer> getCommandUsageStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("help", 25);
        stats.put("clear", 18);
        stats.put("status", 12);
        stats.put("login", 5);
        stats.put("exit", 8);
        return stats;
    }
    
    /**
     * 获取总 Token 消耗
     */
    private long getTotalTokens() {
        // 模拟数据
        return days * 1500;
    }
    
    /**
     * 获取小时使用统计
     */
    private Map<String, Integer> getHourlyUsageStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("9", 15);
        stats.put("10", 20);
        stats.put("11", 18);
        stats.put("14", 12);
        stats.put("15", 16);
        stats.put("16", 14);
        return stats;
    }
    
    /**
     * 格式化时长
     */
    private String formatDuration(long minutes) {
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        return hours + " 小时 " + mins + " 分钟";
    }
}