package com.jwcode.core.planner.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * AILearningMemory - AI 学习记忆
 * 
 * 记录历史任务执行数据，用于优化未来的任务分析和分解。
 * 特性：
 * 1. 任务模式识别 - 识别相似任务
 * 2. 执行结果反馈 - 记录实际执行数据
 * 3. 自适应优化 - 根据历史优化预估
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AILearningMemory {
    
    private static final Logger log = LoggerFactory.getLogger(AILearningMemory.class);
    
    private static final Path MEMORY_DIR = Paths.get(
        System.getProperty("user.home"), ".jwcode", "learning"
    );
    
    private final ObjectMapper objectMapper;
    private final Map<String, TaskPattern> patternCache;
    
    public AILearningMemory() {
        this.objectMapper = new ObjectMapper();
        this.patternCache = new ConcurrentHashMap<>();
        
        // 加载历史数据
        loadPatterns();
    }
    
    /**
     * 记录任务执行结果
     * 
     * @param description 任务描述
     * @param analysis 任务分析
     * @param actualDurationMs 实际耗时
     * @param success 是否成功
     * @param subTaskCount 实际子任务数
     */
    public void recordExecution(String description, TaskAnalysis analysis, 
                                long actualDurationMs, boolean success, int subTaskCount) {
        try {
            String patternId = generatePatternId(description);
            
            TaskPattern pattern = patternCache.computeIfAbsent(patternId, k -> 
                TaskPattern.builder()
                    .id(k)
                    .description(description)
                    .keywordSignatures(extractKeywords(description))
                    .build()
            );
            
            // 更新统计
            pattern.recordExecution(
                analysis != null ? analysis.getComplexity().getOverallScore() : 5,
                analysis != null ? analysis.getEstimation().getEstimatedTimeMs() : actualDurationMs,
                actualDurationMs,
                success,
                subTaskCount
            );
            
            // 异步保存
            savePatternAsync(pattern);
            
            log.info("[AILearningMemory] 记录任务执行: " + description.substring(0, Math.min(30, description.length())) + 
                "... 耗时=" + actualDurationMs + "ms, 成功=" + success);
            
        } catch (Exception e) {
            log.warn("[AILearningMemory] 记录执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 查找相似任务模式
     * 
     * @param description 任务描述
     * @return 相似的任务模式列表（按相似度排序）
     */
    public List<TaskPattern> findSimilarPatterns(String description) {
        Set<String> keywords = extractKeywords(description);
        
        return patternCache.values().stream()
            .map(pattern -> new AbstractMap.SimpleEntry<>(pattern, 
                calculateSimilarity(keywords, pattern.getKeywordSignatures())))
            .filter(entry -> entry.getValue() > 0.3)  // 相似度阈值
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .map(AbstractMap.SimpleEntry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取优化建议
     * 
     * @param description 任务描述
     * @param currentEstimate 当前预估
     * @return 优化后的预估
     */
    public OptimizationSuggestion getOptimizationSuggestion(String description, long currentEstimate) {
        List<TaskPattern> similar = findSimilarPatterns(description);
        
        if (similar.isEmpty()) {
            return OptimizationSuggestion.builder()
                .suggestedEstimate(currentEstimate)
                .confidence(0.5)
                .reasoning("无历史数据参考")
                .build();
        }
        
        // 取最相似的几个模式计算平均值
        List<TaskPattern> topPatterns = similar.subList(0, Math.min(5, similar.size()));
        
        double avgActualTime = topPatterns.stream()
            .mapToLong(TaskPattern::getActualDurationMs)
            .average()
            .orElse(currentEstimate);
        
        double avgSuccessRate = topPatterns.stream()
            .mapToDouble(TaskPattern::getSuccessRate)
            .average()
            .orElse(0.5);
        
        // 加权平均（历史数据和当前预估）
        long suggestedEstimate = (long) ((avgActualTime * 0.6) + (currentEstimate * 0.4));
        
        return OptimizationSuggestion.builder()
            .suggestedEstimate(suggestedEstimate)
            .confidence(avgSuccessRate)
            .basedOnPatterns(topPatterns.stream().map(TaskPattern::getId).collect(Collectors.toList()))
            .reasoning("基于 " + topPatterns.size() + " 个相似任务的历史数据")
            .build();
    }
    
    /**
     * 计算任务成功率预测
     * 
     * @param description 任务描述
     * @param complexity 复杂度评分
     * @return 预测成功率
     */
    public double predictSuccessRate(String description, int complexity) {
        List<TaskPattern> similar = findSimilarPatterns(description);
        
        if (similar.isEmpty()) {
            // 无历史数据，基于复杂度估算
            return Math.max(0.3, 1.0 - (complexity * 0.08));
        }
        
        // 计算加权成功率
        double totalWeight = 0;
        double weightedSuccessRate = 0;
        
        for (TaskPattern pattern : similar.subList(0, Math.min(10, similar.size()))) {
            double similarity = calculateSimilarity(
                extractKeywords(description), 
                pattern.getKeywordSignatures()
            );
            
            weightedSuccessRate += pattern.getSuccessRate() * similarity;
            totalWeight += similarity;
        }
        
        return totalWeight > 0 ? weightedSuccessRate / totalWeight : 0.5;
    }
    
    /**
     * 提取关键词签名
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        
        // 分词并提取关键词
        String[] words = text.toLowerCase()
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", " ")
            .split("\\s+");
        
        for (String word : words) {
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }
        
        // 提取技术关键词
        String[] techKeywords = {
            "refactor", "重构", "debug", "调试", "fix", "修复",
            "create", "创建", "implement", "实现", "test", "测试",
            "optimize", "优化", "analyze", "分析", "deploy", "部署",
            "java", "python", "javascript", "spring", "database", "api",
            "class", "method", "function", "interface", "module"
        };
        
        String lowerText = text.toLowerCase();
        for (String tech : techKeywords) {
            if (lowerText.contains(tech.toLowerCase())) {
                keywords.add(tech);
            }
        }
        
        return keywords;
    }
    
    /**
     * 计算相似度（Jaccard 系数）
     */
    private double calculateSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty()) return 0;
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return (double) intersection.size() / union.size();
    }
    
    /**
     * 生成模式 ID
     */
    private String generatePatternId(String description) {
        return "pattern_" + Math.abs(description.hashCode());
    }
    
    /**
     * 加载历史模式
     */
    private void loadPatterns() {
        try {
            if (!Files.exists(MEMORY_DIR)) {
                Files.createDirectories(MEMORY_DIR);
                return;
            }
            
            File[] files = MEMORY_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null) return;
            
            for (File file : files) {
                try {
                    TaskPattern pattern = objectMapper.readValue(file, TaskPattern.class);
                    patternCache.put(pattern.getId(), pattern);
                } catch (Exception e) {
                    log.warn("加载模式失败: " + file + " - " + e.getMessage());
                }
            }
            
            log.info("[AILearningMemory] 加载了 " + patternCache.size() + " 个历史模式");
            
        } catch (Exception e) {
            log.warn("[AILearningMemory] 加载历史数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 异步保存模式
     */
    private void savePatternAsync(TaskPattern pattern) {
        CompletableFuture.runAsync(() -> {
            try {
                if (!Files.exists(MEMORY_DIR)) {
                    Files.createDirectories(MEMORY_DIR);
                }
                
                Path filePath = MEMORY_DIR.resolve(pattern.getId() + ".json");
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(filePath.toFile(), pattern);
                    
            } catch (Exception e) {
                log.warn("保存模式失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 获取统计信息
     */
    public MemoryStats getStats() {
        int totalPatterns = patternCache.size();
        int totalExecutions = patternCache.values().stream()
            .mapToInt(TaskPattern::getExecutionCount)
            .sum();
        
        double avgSuccessRate = patternCache.values().stream()
            .mapToDouble(TaskPattern::getSuccessRate)
            .average()
            .orElse(0);
        
        return MemoryStats.builder()
            .totalPatterns(totalPatterns)
            .totalExecutions(totalExecutions)
            .averageSuccessRate(avgSuccessRate)
            .build();
    }
    
    /**
     * 清除所有记忆
     */
    public void clear() {
        patternCache.clear();
        try {
            Files.list(MEMORY_DIR)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception e) {
                        log.warn("删除记忆文件失败: " + p);
                    }
                });
        } catch (Exception e) {
            log.warn("清除记忆失败: " + e.getMessage());
        }
    }
    
    // ==================== 数据类 ====================
    
    public static class TaskPattern {
        private String id;
        private String description;
        private Set<String> keywordSignatures;
        private int executionCount = 0;
        private int successCount = 0;
        private long estimatedDurationMs;
        private long actualDurationMs;
        private int estimatedSubTasks;
        private int actualSubTasks;
        private int complexityScore;
        private List<ExecutionRecord> history = new ArrayList<>();
        private long lastUpdated = System.currentTimeMillis();
        
        public String getId() { return id; }
        public String getDescription() { return description; }
        public Set<String> getKeywordSignatures() { return keywordSignatures; }
        public int getExecutionCount() { return executionCount; }
        public int getSuccessCount() { return successCount; }
        public long getEstimatedDurationMs() { return estimatedDurationMs; }
        public long getActualDurationMs() { return actualDurationMs; }
        public int getEstimatedSubTasks() { return estimatedSubTasks; }
        public int getActualSubTasks() { return actualSubTasks; }
        public int getComplexityScore() { return complexityScore; }
        public List<ExecutionRecord> getHistory() { return history; }
        public long getLastUpdated() { return lastUpdated; }
        
        public void setId(String id) { this.id = id; }
        public void setDescription(String description) { this.description = description; }
        public void setKeywordSignatures(Set<String> keywordSignatures) { this.keywordSignatures = keywordSignatures; }
        public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public void setEstimatedDurationMs(long estimatedDurationMs) { this.estimatedDurationMs = estimatedDurationMs; }
        public void setActualDurationMs(long actualDurationMs) { this.actualDurationMs = actualDurationMs; }
        public void setEstimatedSubTasks(int estimatedSubTasks) { this.estimatedSubTasks = estimatedSubTasks; }
        public void setActualSubTasks(int actualSubTasks) { this.actualSubTasks = actualSubTasks; }
        public void setComplexityScore(int complexityScore) { this.complexityScore = complexityScore; }
        public void setHistory(List<ExecutionRecord> history) { this.history = history; }
        public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
        
        public void recordExecution(int complexity, long estimatedTime, long actualTime, 
                                   boolean success, int subTaskCount) {
            this.executionCount++;
            if (success) this.successCount++;
            
            this.complexityScore = (this.complexityScore * (executionCount - 1) + complexity) / executionCount;
            this.estimatedDurationMs = (this.estimatedDurationMs * (executionCount - 1) + estimatedTime) / executionCount;
            this.actualDurationMs = (this.actualDurationMs * (executionCount - 1) + actualTime) / executionCount;
            
            history.add(ExecutionRecord.builder()
                .timestamp(System.currentTimeMillis())
                .estimatedTimeMs(estimatedTime)
                .actualTimeMs(actualTime)
                .success(success)
                .subTaskCount(subTaskCount)
                .build());
            
            // 限制历史记录数量
            if (history.size() > 100) {
                history = history.subList(history.size() - 50, history.size());
            }
            
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public double getSuccessRate() {
            return executionCount > 0 ? (double) successCount / executionCount : 0;
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private String id;
            private String description;
            private Set<String> keywordSignatures;
            private int executionCount = 0;
            private int successCount = 0;
            private long estimatedDurationMs;
            private long actualDurationMs;
            private int estimatedSubTasks;
            private int actualSubTasks;
            private int complexityScore;
            private List<ExecutionRecord> history = new ArrayList<>();
            private long lastUpdated = System.currentTimeMillis();
            
            public Builder id(String v) { this.id = v; return this; }
            public Builder description(String v) { this.description = v; return this; }
            public Builder keywordSignatures(Set<String> v) { this.keywordSignatures = v; return this; }
            public Builder executionCount(int v) { this.executionCount = v; return this; }
            public Builder successCount(int v) { this.successCount = v; return this; }
            public Builder estimatedDurationMs(long v) { this.estimatedDurationMs = v; return this; }
            public Builder actualDurationMs(long v) { this.actualDurationMs = v; return this; }
            public Builder estimatedSubTasks(int v) { this.estimatedSubTasks = v; return this; }
            public Builder actualSubTasks(int v) { this.actualSubTasks = v; return this; }
            public Builder complexityScore(int v) { this.complexityScore = v; return this; }
            public Builder history(List<ExecutionRecord> v) { this.history = v; return this; }
            public Builder lastUpdated(long v) { this.lastUpdated = v; return this; }
            
            public TaskPattern build() {
                TaskPattern p = new TaskPattern();
                p.id = this.id;
                p.description = this.description;
                p.keywordSignatures = this.keywordSignatures;
                p.executionCount = this.executionCount;
                p.successCount = this.successCount;
                p.estimatedDurationMs = this.estimatedDurationMs;
                p.actualDurationMs = this.actualDurationMs;
                p.estimatedSubTasks = this.estimatedSubTasks;
                p.actualSubTasks = this.actualSubTasks;
                p.complexityScore = this.complexityScore;
                p.history = this.history;
                p.lastUpdated = this.lastUpdated;
                return p;
            }
        }
    }
    
    public static class ExecutionRecord {
        private long timestamp;
        private long estimatedTimeMs;
        private long actualTimeMs;
        private boolean success;
        private int subTaskCount;
        
        public long getTimestamp() { return timestamp; }
        public long getEstimatedTimeMs() { return estimatedTimeMs; }
        public long getActualTimeMs() { return actualTimeMs; }
        public boolean isSuccess() { return success; }
        public int getSubTaskCount() { return subTaskCount; }
        
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public void setEstimatedTimeMs(long estimatedTimeMs) { this.estimatedTimeMs = estimatedTimeMs; }
        public void setActualTimeMs(long actualTimeMs) { this.actualTimeMs = actualTimeMs; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setSubTaskCount(int subTaskCount) { this.subTaskCount = subTaskCount; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private long timestamp;
            private long estimatedTimeMs;
            private long actualTimeMs;
            private boolean success;
            private int subTaskCount;
            
            public Builder timestamp(long v) { this.timestamp = v; return this; }
            public Builder estimatedTimeMs(long v) { this.estimatedTimeMs = v; return this; }
            public Builder actualTimeMs(long v) { this.actualTimeMs = v; return this; }
            public Builder success(boolean v) { this.success = v; return this; }
            public Builder subTaskCount(int v) { this.subTaskCount = v; return this; }
            
            public ExecutionRecord build() {
                ExecutionRecord r = new ExecutionRecord();
                r.timestamp = this.timestamp;
                r.estimatedTimeMs = this.estimatedTimeMs;
                r.actualTimeMs = this.actualTimeMs;
                r.success = this.success;
                r.subTaskCount = this.subTaskCount;
                return r;
            }
        }
    }
    
    public static class OptimizationSuggestion {
        private long suggestedEstimate;
        private double confidence;
        private List<String> basedOnPatterns;
        private String reasoning;
        
        public long getSuggestedEstimate() { return suggestedEstimate; }
        public double getConfidence() { return confidence; }
        public List<String> getBasedOnPatterns() { return basedOnPatterns; }
        public String getReasoning() { return reasoning; }
        
        public void setSuggestedEstimate(long suggestedEstimate) { this.suggestedEstimate = suggestedEstimate; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public void setBasedOnPatterns(List<String> basedOnPatterns) { this.basedOnPatterns = basedOnPatterns; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private long suggestedEstimate;
            private double confidence;
            private List<String> basedOnPatterns;
            private String reasoning;
            
            public Builder suggestedEstimate(long v) { this.suggestedEstimate = v; return this; }
            public Builder confidence(double v) { this.confidence = v; return this; }
            public Builder basedOnPatterns(List<String> v) { this.basedOnPatterns = v; return this; }
            public Builder reasoning(String v) { this.reasoning = v; return this; }
            
            public OptimizationSuggestion build() {
                OptimizationSuggestion s = new OptimizationSuggestion();
                s.suggestedEstimate = this.suggestedEstimate;
                s.confidence = this.confidence;
                s.basedOnPatterns = this.basedOnPatterns;
                s.reasoning = this.reasoning;
                return s;
            }
        }
    }
    
    public static class MemoryStats {
        private int totalPatterns;
        private int totalExecutions;
        private double averageSuccessRate;
        
        public int getTotalPatterns() { return totalPatterns; }
        public int getTotalExecutions() { return totalExecutions; }
        public double getAverageSuccessRate() { return averageSuccessRate; }
        
        public void setTotalPatterns(int totalPatterns) { this.totalPatterns = totalPatterns; }
        public void setTotalExecutions(int totalExecutions) { this.totalExecutions = totalExecutions; }
        public void setAverageSuccessRate(double averageSuccessRate) { this.averageSuccessRate = averageSuccessRate; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private int totalPatterns;
            private int totalExecutions;
            private double averageSuccessRate;
            
            public Builder totalPatterns(int v) { this.totalPatterns = v; return this; }
            public Builder totalExecutions(int v) { this.totalExecutions = v; return this; }
            public Builder averageSuccessRate(double v) { this.averageSuccessRate = v; return this; }
            
            public MemoryStats build() {
                MemoryStats s = new MemoryStats();
                s.totalPatterns = this.totalPatterns;
                s.totalExecutions = this.totalExecutions;
                s.averageSuccessRate = this.averageSuccessRate;
                return s;
            }
        }
    }
}
