package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.core.service.CostTrackerService;
import com.jwcode.core.service.CostTrackerService.CostEntry;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * /cost 命令 - 查看成本统计
 * 
 * 显示当前会话或指定时间段内的 API 使用成本。
 */
public class CostCommand implements Command {
    
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.####");
    
    private final CostTrackerService costTrackerService;
    
    public CostCommand() {
        this.costTrackerService = new CostTrackerService();
    }
    
    @Override
    public String getName() {
        return "cost";
    }
    
    @Override
    public String getDescription() {
        return "查看 API 使用成本统计";
    }
    
    @Override
    public String getUsage() {
        return "/cost [--today|--week|--month|--session] [--model <model_name>]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        CostFilter filter = parseArgs(args);
        
        // 获取成本数据
        CostData costData = getCostData(context, filter);
        
        // 构建输出
        StringBuilder output = new StringBuilder();
        output.append("=== API 成本统计 ===\n\n");
        
        if (filter.period != null) {
            output.append("时间段：").append(filter.period).append("\n");
        } else {
            output.append("时间段：当前会话\n");
        }
        
        if (filter.model != null) {
            output.append("模型：").append(filter.model).append("\n");
        }
        
        output.append("\n--- 使用详情 ---\n\n");
        
        // 显示 token 使用
        output.append("输入 Token: ").append(formatNumber(costData.inputTokens)).append("\n");
        output.append("输出 Token: ").append(formatNumber(costData.outputTokens)).append("\n");
        output.append("总 Token: ").append(formatNumber(costData.totalTokens)).append("\n");
        
        output.append("\n--- 成本详情 ---\n\n");
        
        // 显示成本
        output.append("输入成本：$").append(DECIMAL_FORMAT.format(costData.inputCost)).append("\n");
        output.append("输出成本：$").append(DECIMAL_FORMAT.format(costData.outputCost)).append("\n");
        output.append("总成本：$").append(DECIMAL_FORMAT.format(costData.totalCost)).append("\n");
        
        // 显示缓存命中（如果有）
        if (costData.cachedTokens > 0) {
            output.append("\n缓存命中 Token: ").append(formatNumber(costData.cachedTokens));
            output.append(" (节省 $").append(DECIMAL_FORMAT.format(costData.cachedSavings)).append(")\n");
        }
        
        // 显示预估月度成本（如果有足够数据）
        if (costData.daysTracked > 0) {
            double dailyAverage = costData.totalCost / costData.daysTracked;
            output.append("\n--- 预估 ---\n\n");
            output.append("日均成本：$").append(DECIMAL_FORMAT.format(dailyAverage)).append("\n");
            output.append("月度预估：$").append(DECIMAL_FORMAT.format(dailyAverage * 30)).append("\n");
        }
        
        // 添加预算警告（如果接近预算）
        if (costData.budgetLimit > 0 && costData.totalCost > costData.budgetLimit * 0.8) {
            output.append("\n⚠️  警告：已达到预算的 ").append((int)(costData.totalCost / costData.budgetLimit * 100)).append("%");
            output.append(" (预算：$").append(DECIMAL_FORMAT.format(costData.budgetLimit)).append(")");
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 解析命令参数
     */
    private CostFilter parseArgs(String args) {
        CostFilter filter = new CostFilter();
        
        if (args == null || args.trim().isEmpty()) {
            return filter;
        }
        
        String[] parts = args.trim().split("\\s+");
        
        for (int i = 0; i < parts.length; i++) {
            switch (parts[i]) {
                case "--today":
                    filter.period = "今天";
                    filter.startDate = LocalDate.now();
                    break;
                case "--week":
                    filter.period = "本周";
                    filter.startDate = LocalDate.now().minusDays(7);
                    break;
                case "--month":
                    filter.period = "本月";
                    filter.startDate = LocalDate.now().minusDays(30);
                    break;
                case "--session":
                    filter.period = "当前会话";
                    filter.sessionOnly = true;
                    break;
                case "--model":
                    if (i + 1 < parts.length) {
                        filter.model = parts[++i];
                    }
                    break;
            }
        }
        
        return filter;
    }
    
    /**
     * 获取成本数据（从 CostTrackerService 获取真实数据）
     */
    private CostData getCostData(CommandContext context, CostFilter filter) {
        CostData data = new CostData();
        
        // 从 CostTrackerService 获取真实成本数据
        double totalCostDollars = costTrackerService.getTotalCostDollars();
        List<CostEntry> recentHistory = costTrackerService.getCostHistory(100);
        
        if (!recentHistory.isEmpty()) {
            long totalInputTokens = 0;
            long totalOutputTokens = 0;
            for (CostEntry entry : recentHistory) {
                totalInputTokens += entry.inputTokens;
                totalOutputTokens += entry.outputTokens;
            }
            data.inputTokens = totalInputTokens;
            data.outputTokens = totalOutputTokens;
            data.totalTokens = totalInputTokens + totalOutputTokens;
            data.totalCost = totalCostDollars;
            
            // 估算输入/输出成本（按历史比例分配）
            if (data.totalTokens > 0) {
                double inputRatio = (double) totalInputTokens / data.totalTokens;
                data.inputCost = totalCostDollars * inputRatio;
                data.outputCost = totalCostDollars * (1 - inputRatio);
            }
            
            // 计算追踪天数（从最早记录到现在）
            if (recentHistory.size() >= 2) {
                CostEntry first = recentHistory.get(0);
                CostEntry last = recentHistory.get(recentHistory.size() - 1);
                try {
                    Instant firstTime = Instant.parse(first.timestamp);
                    Instant lastTime = Instant.parse(last.timestamp);
                    long daysBetween = java.time.Duration.between(firstTime, lastTime).toDays();
                    data.daysTracked = Math.max(1.0, (double) daysBetween);
                } catch (Exception e) {
                    data.daysTracked = 1.0;
                }
            } else {
                data.daysTracked = 1.0;
            }
            
            // 从 session 获取预算限制
            Map<String, Object> costInfo = context.getSession().getCostInfo();
            if (costInfo != null) {
                data.budgetLimit = getDoubleValue(costInfo, "budget_limit", 0.0);
            }
        } else {
            // 无历史数据时显示零值
            data.inputTokens = 0L;
            data.outputTokens = 0L;
            data.totalTokens = 0L;
            data.inputCost = 0.0;
            data.outputCost = 0.0;
            data.totalCost = 0.0;
            data.daysTracked = 1.0;
        }
        
        return data;
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
     * 从 Map 获取 long 值
     */
    private long getLongValue(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
    
    /**
     * 从 Map 获取 double 值
     */
    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 成本过滤器
     */
    private static class CostFilter {
        String period;
        LocalDate startDate;
        String model;
        boolean sessionOnly;
    }
    
    /**
     * 成本数据
     */
    private static class CostData {
        long inputTokens;
        long outputTokens;
        long totalTokens;
        double inputCost;
        double outputCost;
        double totalCost;
        long cachedTokens;
        double cachedSavings;
        double daysTracked;
        double budgetLimit;
    }
}