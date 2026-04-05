package com.jwcode.core.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CostTrackerService - 成本跟踪服务
 * 
 * 功能说明：
 * 计算和跟踪 API 调用成本，支持多种模型的定价配置。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CostTrackerService {
    
    private final Map<String, ModelPricing> modelPricingMap;
    private final List<CostEntry> costHistory;
    private final AtomicLong totalCost;
    private final ExecutorService executor;
    private final int maxHistorySize;
    
    // 默认定价（每 1000 tokens）
    private static final double DEFAULT_INPUT_PRICE = 0.0005;
    private static final double DEFAULT_OUTPUT_PRICE = 0.0015;
    
    public CostTrackerService() {
        this.modelPricingMap = new ConcurrentHashMap<>();
        this.costHistory = new ArrayList<>();
        this.totalCost = new AtomicLong(0); // 以微美元为单位
        this.executor = Executors.newSingleThreadExecutor();
        this.maxHistorySize = 1000;
        
        // 初始化默认模型定价
        initDefaultPricing();
    }
    
    private void initDefaultPricing() {
        // Claude 模型定价
        modelPricingMap.put("claude-sonnet-4-20250514", new ModelPricing(0.003, 0.015));
        modelPricingMap.put("claude-3-5-sonnet-20241022", new ModelPricing(0.003, 0.015));
        modelPricingMap.put("claude-3-5-haiku-20241022", new ModelPricing(0.0008, 0.004));
        modelPricingMap.put("claude-3-opus-20240229", new ModelPricing(0.015, 0.075));
        
        // GPT 模型定价
        modelPricingMap.put("gpt-4o", new ModelPricing(0.005, 0.015));
        modelPricingMap.put("gpt-4-turbo", new ModelPricing(0.01, 0.03));
        modelPricingMap.put("gpt-3.5-turbo", new ModelPricing(0.0005, 0.0015));
    }
    
    /**
     * 添加或更新模型定价
     */
    public void setModelPricing(String modelId, double inputPrice, double outputPrice) {
        modelPricingMap.put(modelId, new ModelPricing(inputPrice, outputPrice));
    }
    
    /**
     * 获取模型定价
     */
    public ModelPricing getModelPricing(String modelId) {
        ModelPricing pricing = modelPricingMap.get(modelId);
        if (pricing == null) {
            // 返回默认定价
            return new ModelPricing(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE);
        }
        return pricing;
    }
    
    /**
     * 记录 API 调用成本
     */
    public void recordCost(String modelId, int inputTokens, int outputTokens) {
        executor.submit(() -> {
            ModelPricing pricing = getModelPricing(modelId);
            long costMicros = pricing.calculateCost(inputTokens, outputTokens);
            
            CostEntry entry = new CostEntry();
            entry.modelId = modelId;
            entry.inputTokens = inputTokens;
            entry.outputTokens = outputTokens;
            entry.costMicros = costMicros;
            entry.timestamp = Instant.now().toString();
            
            synchronized (costHistory) {
                costHistory.add(entry);
                if (costHistory.size() > maxHistorySize) {
                    costHistory.remove(0);
                }
            }
            
            totalCost.addAndGet(costMicros);
        });
    }
    
    /**
     * 计算成本
     */
    public long calculateCost(String modelId, int inputTokens, int outputTokens) {
        ModelPricing pricing = getModelPricing(modelId);
        return pricing.calculateCost(inputTokens, outputTokens);
    }
    
    /**
     * 获取总成本（微美元）
     */
    public long getTotalCostMicros() {
        return totalCost.get();
    }
    
    /**
     * 获取总成本（美元）
     */
    public double getTotalCostDollars() {
        return totalCost.get() / 1_000_000.0;
    }
    
    /**
     * 获取成本历史
     */
    public List<CostEntry> getCostHistory(int limit) {
        synchronized (costHistory) {
            int size = costHistory.size();
            int start = Math.max(0, size - limit);
            return new ArrayList<>(costHistory.subList(start, size));
        }
    }
    
    /**
     * 获取所有成本历史
     */
    public List<CostEntry> getAllCostHistory() {
        synchronized (costHistory) {
            return new ArrayList<>(costHistory);
        }
    }
    
    /**
     * 按模型统计成本
     */
    public Map<String, ModelCostSummary> getCostByModel() {
        Map<String, ModelCostSummary> summary = new ConcurrentHashMap<>();
        
        synchronized (costHistory) {
            for (CostEntry entry : costHistory) {
                ModelCostSummary modelSummary = summary.computeIfAbsent(
                        entry.modelId, k -> new ModelCostSummary(k));
                modelSummary.addCost(entry.costMicros);
                modelSummary.addTokens(entry.inputTokens, entry.outputTokens);
            }
        }
        
        return summary;
    }
    
    /**
     * 重置成本统计
     */
    public void reset() {
        totalCost.set(0);
        synchronized (costHistory) {
            costHistory.clear();
        }
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * 模型定价配置
     */
    public static class ModelPricing {
        public final double inputPricePer1K;  // 每 1000 输入 tokens 的价格（美元）
        public final double outputPricePer1K; // 每 1000 输出 tokens 的价格（美元）
        
        public ModelPricing(double inputPricePer1K, double outputPricePer1K) {
            this.inputPricePer1K = inputPricePer1K;
            this.outputPricePer1K = outputPricePer1K;
        }
        
        /**
         * 计算成本（返回微美元）
         */
        public long calculateCost(int inputTokens, int outputTokens) {
            double inputCost = (inputTokens / 1000.0) * inputPricePer1K;
            double outputCost = (outputTokens / 1000.0) * outputPricePer1K;
            double totalCost = inputCost + outputCost;
            // 转换为微美元（1 美元 = 1,000,000 微美元）
            return (long) (totalCost * 1_000_000);
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "inputPricePer1K", inputPricePer1K,
                    "outputPricePer1K", outputPricePer1K
            );
        }
    }
    
    /**
     * 成本条目
     */
    public static class CostEntry {
        public String timestamp;
        public String modelId;
        public int inputTokens;
        public int outputTokens;
        public long costMicros;
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "timestamp", timestamp,
                    "modelId", modelId,
                    "inputTokens", inputTokens,
                    "outputTokens", outputTokens,
                    "costMicros", costMicros,
                    "costDollars", costMicros / 1_000_000.0
            );
        }
    }
    
    /**
     * 模型成本摘要
     */
    public static class ModelCostSummary {
        public final String modelId;
        public long totalCostMicros;
        public int totalInputTokens;
        public int totalOutputTokens;
        public int requestCount;
        
        public ModelCostSummary(String modelId) {
            this.modelId = modelId;
        }
        
        public void addCost(long costMicros) {
            this.totalCostMicros += costMicros;
            this.requestCount++;
        }
        
        public void addTokens(int inputTokens, int outputTokens) {
            this.totalInputTokens += inputTokens;
            this.totalOutputTokens += outputTokens;
        }
        
        public double getTotalCostDollars() {
            return totalCostMicros / 1_000_000.0;
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "modelId", modelId,
                    "totalCostMicros", totalCostMicros,
                    "totalCostDollars", getTotalCostDollars(),
                    "totalInputTokens", totalInputTokens,
                    "totalOutputTokens", totalOutputTokens,
                    "requestCount", requestCount
            );
        }
    }
}