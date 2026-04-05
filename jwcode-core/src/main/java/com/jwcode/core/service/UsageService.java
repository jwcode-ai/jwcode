package com.jwcode.core.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UsageService - 使用量查询服务
 * 
 * 功能说明：
 * 统计和查询 API 使用情况，包括 Token 消耗、调用次数等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class UsageService {
    
    private final Map<String, UsageStats> dailyStats;
    private final Map<String, Integer> modelUsage;
    private UsageLimit limits;
    private String currentPeriod;
    
    public UsageService() {
        this.dailyStats = new ConcurrentHashMap<>();
        this.modelUsage = new ConcurrentHashMap<>();
        this.limits = new UsageLimit();
        this.currentPeriod = getCurrentPeriod();
    }
    
    /**
     * 记录 Token 使用
     */
    public void recordTokenUsage(String model, int tokens) {
        String today = getCurrentDate();
        
        dailyStats.computeIfAbsent(today, k -> new UsageStats());
        dailyStats.get(today).addTokens(tokens);
        
        modelUsage.merge(model, tokens, Integer::sum);
    }
    
    /**
     * 记录 API 调用
     */
    public void recordApiCall(String endpoint, int tokens) {
        String today = getCurrentDate();
        
        dailyStats.computeIfAbsent(today, k -> new UsageStats());
        dailyStats.get(today).addCall(endpoint, tokens);
    }
    
    /**
     * 获取今日使用量
     */
    public UsageStats getTodayUsage() {
        return dailyStats.getOrDefault(getCurrentDate(), new UsageStats());
    }
    
    /**
     * 获取指定日期使用量
     */
    public UsageStats getDateUsage(String date) {
        return dailyStats.getOrDefault(date, new UsageStats());
    }
    
    /**
     * 获取周期使用量
     */
    public UsageStats getPeriodUsage() {
        UsageStats total = new UsageStats();
        for (UsageStats stats : dailyStats.values()) {
            total.add(stats);
        }
        return total;
    }
    
    /**
     * 获取模型使用分布
     */
    public Map<String, Integer> getModelUsage() {
        return new HashMap<>(modelUsage);
    }
    
    /**
     * 检查配额
     */
    public boolean hasQuota() {
        UsageStats today = getTodayUsage();
        return today.totalTokens < limits.dailyTokenLimit;
    }
    
    /**
     * 获取剩余配额
     */
    public long getRemainingQuota() {
        UsageStats today = getTodayUsage();
        return Math.max(0, limits.dailyTokenLimit - today.totalTokens);
    }
    
    /**
     * 获取配额使用率
     */
    public double getQuotaUsagePercent() {
        UsageStats today = getTodayUsage();
        return (double) today.totalTokens / limits.dailyTokenLimit * 100;
    }
    
    /**
     * 设置配额限制
     */
    public void setLimits(UsageLimit limits) {
        this.limits = limits;
    }
    
    /**
     * 获取配额限制
     */
    public UsageLimit getLimits() {
        return limits;
    }
    
    /**
     * 异步获取使用报告
     */
    public CompletableFuture<UsageReport> generateReport() {
        return CompletableFuture.supplyAsync(() -> {
            UsageReport report = new UsageReport();
            report.period = currentPeriod;
            report.totalTokens = getPeriodUsage().totalTokens;
            report.totalCalls = getPeriodUsage().totalCalls;
            report.modelDistribution = getModelUsage();
            report.dailyAverage = getPeriodUsage().totalTokens / Math.max(1, dailyStats.size());
            report.peakDay = findPeakDay();
            return report;
        });
    }
    
    /**
     * 查找使用高峰日
     */
    private String findPeakDay() {
        String peakDay = null;
        int maxTokens = 0;
        
        for (Map.Entry<String, UsageStats> entry : dailyStats.entrySet()) {
            if (entry.getValue().totalTokens > maxTokens) {
                maxTokens = entry.getValue().totalTokens;
                peakDay = entry.getKey();
            }
        }
        
        return peakDay;
    }
    
    /**
     * 获取当前日期
     */
    private String getCurrentDate() {
        return java.time.LocalDate.now().toString();
    }
    
    /**
     * 获取当前周期
     */
    private String getCurrentPeriod() {
        return java.time.YearMonth.now().toString();
    }
    
    /**
     * 使用统计类
     */
    public static class UsageStats {
        public int totalTokens;
        public int totalCalls;
        public final Map<String, Integer> endpointCalls;
        
        public UsageStats() {
            this.endpointCalls = new HashMap<>();
        }
        
        public void addTokens(int tokens) {
            this.totalTokens += tokens;
        }
        
        public void addCall(String endpoint, int tokens) {
            this.totalCalls++;
            this.totalTokens += tokens;
            endpointCalls.merge(endpoint, 1, Integer::sum);
        }
        
        public void add(UsageStats other) {
            this.totalTokens += other.totalTokens;
            this.totalCalls += other.totalCalls;
            for (Map.Entry<String, Integer> entry : other.endpointCalls.entrySet()) {
                this.endpointCalls.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
    }
    
    /**
     * 使用限额类
     */
    public static class UsageLimit {
        public int dailyTokenLimit;
        public int monthlyTokenLimit;
        public int requestsPerMinute;
        
        public UsageLimit() {
            this.dailyTokenLimit = 100000;
            this.monthlyTokenLimit = 3000000;
            this.requestsPerMinute = 60;
        }
        
        public UsageLimit(int daily, int monthly, int rpm) {
            this.dailyTokenLimit = daily;
            this.monthlyTokenLimit = monthly;
            this.requestsPerMinute = rpm;
        }
    }
    
    /**
     * 使用报告类
     */
    public static class UsageReport {
        public String period;
        public int totalTokens;
        public int totalCalls;
        public Map<String, Integer> modelDistribution;
        public int dailyAverage;
        public String peakDay;
    }
}