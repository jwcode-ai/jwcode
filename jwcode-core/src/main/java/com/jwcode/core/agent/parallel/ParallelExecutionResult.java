package com.jwcode.core.agent.parallel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 并行执行结果 - 聚合多个 SubAgentResult
 * 
 * 提供结果聚合、去重、排序等功能
 */
public class ParallelExecutionResult {
    
    // ==================== 核心属性 ====================
    
    private final List<SubAgentResult> results;
    private final long totalExecutionTimeMs;
    private final long timestamp;
    private final String executionId;
    
    // 统计信息
    private final int totalCount;
    private final int successCount;
    private final int failureCount;
    
    // 元数据
    private Map<String, Object> metadata = new HashMap<>();
    
    // ==================== 构造函数 ====================
    
    public ParallelExecutionResult() {
        this(new ArrayList<>(), 0);
    }
    
    public ParallelExecutionResult(List<SubAgentResult> results, long totalExecutionTimeMs) {
        this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
        this.totalExecutionTimeMs = totalExecutionTimeMs;
        this.timestamp = System.currentTimeMillis();
        this.executionId = "exec_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        this.totalCount = this.results.size();
        this.successCount = (int) this.results.stream().filter(SubAgentResult::isSuccess).count();
        this.failureCount = totalCount - successCount;
    }
    
    // ==================== 静态工厂 ====================
    
    public static ParallelExecutionResult empty() {
        return new ParallelExecutionResult();
    }
    
    public static ParallelExecutionResult of(SubAgentResult... results) {
        return new ParallelExecutionResult(Arrays.asList(results), 0);
    }
    
    public static ParallelExecutionResult of(List<SubAgentResult> results) {
        return new ParallelExecutionResult(results, 0);
    }
    
    public static ParallelExecutionResult success(List<SubAgentResult> results, long executionTimeMs) {
        return new ParallelExecutionResult(results, executionTimeMs);
    }
    
    public static ParallelExecutionResult failure(String error, long executionTimeMs) {
        SubAgentResult errorResult = SubAgentResult.failure("execution", error);
        return new ParallelExecutionResult(Collections.singletonList(errorResult), executionTimeMs);
    }
    
    // ==================== 基本访问方法 ====================
    
    public List<SubAgentResult> getResults() {
        return Collections.unmodifiableList(results);
    }
    
    public long getTotalExecutionTimeMs() {
        return totalExecutionTimeMs;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public int getSuccessCount() {
        return successCount;
    }
    
    public int getFailureCount() {
        return failureCount;
    }
    
    public double getSuccessRate() {
        return totalCount > 0 ? (double) successCount / totalCount * 100 : 0;
    }
    
    public boolean isAllSuccess() {
        return successCount == totalCount && totalCount > 0;
    }
    
    public boolean hasFailures() {
        return failureCount > 0;
    }
    
    public boolean isEmpty() {
        return results.isEmpty();
    }
    
    // ==================== 结果过滤 ====================
    
    /**
     * 获取成功的结果
     */
    public List<SubAgentResult> getSuccessfulResults() {
        return results.stream()
            .filter(SubAgentResult::isSuccess)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取失败的结果
     */
    public List<SubAgentResult> getFailedResults() {
        return results.stream()
            .filter(SubAgentResult::isFailed)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据条件过滤结果
     */
    public List<SubAgentResult> filter(Predicate<SubAgentResult> predicate) {
        return results.stream()
            .filter(predicate)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取指定任务的结果
     */
    public Optional<SubAgentResult> getResult(String taskId) {
        return results.stream()
            .filter(r -> r.getTaskId().equals(taskId))
            .findFirst();
    }
    
    /**
     * 获取指定 Agent 的结果
     */
    public List<SubAgentResult> getResultsByAgent(String agentId) {
        return results.stream()
            .filter(r -> agentId.equals(r.getAgentId()))
            .collect(Collectors.toList());
    }
    
    // ==================== 结果排序 ====================
    
    /**
     * 按执行时间排序
     */
    public ParallelExecutionResult sortByExecutionTime() {
        return sortByExecutionTime(false);
    }
    
    /**
     * 按执行时间排序
     * @param descending true=降序，false=升序
     */
    public ParallelExecutionResult sortByExecutionTime(boolean descending) {
        List<SubAgentResult> sorted = results.stream()
            .sorted((a, b) -> {
                int cmp = Long.compare(a.getExecutionTimeMs(), b.getExecutionTimeMs());
                return descending ? -cmp : cmp;
            })
            .collect(Collectors.toList());
        return new ParallelExecutionResult(sorted, totalExecutionTimeMs);
    }
    
    /**
     * 按开始时间排序
     */
    public ParallelExecutionResult sortByStartTime() {
        return sortByStartTime(false);
    }
    
    /**
     * 按开始时间排序
     * @param descending true=降序，false=升序
     */
    public ParallelExecutionResult sortByStartTime(boolean descending) {
        List<SubAgentResult> sorted = results.stream()
            .sorted((a, b) -> {
                int cmp = Long.compare(a.getStartTime(), b.getStartTime());
                return descending ? -cmp : cmp;
            })
            .collect(Collectors.toList());
        return new ParallelExecutionResult(sorted, totalExecutionTimeMs);
    }
    
    /**
     * 按任务ID排序
     */
    public ParallelExecutionResult sortByTaskId() {
        List<SubAgentResult> sorted = results.stream()
            .sorted(Comparator.comparing(SubAgentResult::getTaskId))
            .collect(Collectors.toList());
        return new ParallelExecutionResult(sorted, totalExecutionTimeMs);
    }
    
    /**
     * 自定义排序
     */
    public ParallelExecutionResult sort(Comparator<SubAgentResult> comparator) {
        List<SubAgentResult> sorted = results.stream()
            .sorted(comparator)
            .collect(Collectors.toList());
        return new ParallelExecutionResult(sorted, totalExecutionTimeMs);
    }
    
    // ==================== 结果去重 ====================
    
    /**
     * 根据任务ID去重（保留第一个）
     */
    public ParallelExecutionResult distinctByTaskId() {
        Map<String, SubAgentResult> unique = new LinkedHashMap<>();
        for (SubAgentResult result : results) {
            unique.putIfAbsent(result.getTaskId(), result);
        }
        return new ParallelExecutionResult(new ArrayList<>(unique.values()), totalExecutionTimeMs);
    }
    
    /**
     * 根据 Agent ID 去重（保留第一个）
     */
    public ParallelExecutionResult distinctByAgentId() {
        Map<String, SubAgentResult> unique = new LinkedHashMap<>();
        for (SubAgentResult result : results) {
            String agentId = result.getAgentId();
            if (agentId != null) {
                unique.putIfAbsent(agentId, result);
            }
        }
        return new ParallelExecutionResult(new ArrayList<>(unique.values()), totalExecutionTimeMs);
    }
    
    /**
     * 根据自定义键去重
     */
    public ParallelExecutionResult distinctBy(Function<SubAgentResult, String> keyExtractor) {
        Map<String, SubAgentResult> unique = new LinkedHashMap<>();
        for (SubAgentResult result : results) {
            String key = keyExtractor.apply(result);
            if (key != null) {
                unique.putIfAbsent(key, result);
            }
        }
        return new ParallelExecutionResult(new ArrayList<>(unique.values()), totalExecutionTimeMs);
    }
    
    // ==================== 结果转换 ====================
    
    /**
     * 映射所有结果
     */
    public <T> List<T> map(Function<SubAgentResult, T> mapper) {
        return results.stream()
            .map(mapper)
            .collect(Collectors.toList());
    }
    
    /**
     * 合并所有输出
     */
    public String combineOutputs() {
        return combineOutputs("\n\n");
    }
    
    /**
     * 合并所有输出（带分隔符）
     */
    public String combineOutputs(String separator) {
        return results.stream()
            .map(SubAgentResult::getOutput)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(separator));
    }
    
    /**
     * 合并所有成功结果的输出
     */
    public String combineSuccessfulOutputs() {
        return combineSuccessfulOutputs("\n\n");
    }
    
    /**
     * 合并所有成功结果的输出（带分隔符）
     */
    public String combineSuccessfulOutputs(String separator) {
        return getSuccessfulResults().stream()
            .map(SubAgentResult::getOutput)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(separator));
    }
    
    /**
     * 合并所有错误信息
     */
    public String combineErrors() {
        return getFailedResults().stream()
            .map(SubAgentResult::getError)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));
    }
    
    // ==================== 结果分组 ====================
    
    /**
     * 按 Agent ID 分组
     */
    public Map<String, List<SubAgentResult>> groupByAgent() {
        return results.stream()
            .filter(r -> r.getAgentId() != null)
            .collect(Collectors.groupingBy(SubAgentResult::getAgentId));
    }
    
    /**
     * 按成功/失败分组
     */
    public Map<Boolean, List<SubAgentResult>> groupBySuccess() {
        return results.stream()
            .collect(Collectors.groupingBy(SubAgentResult::isSuccess));
    }
    
    /**
     * 自定义分组
     */
    public <K> Map<K, List<SubAgentResult>> groupBy(Function<SubAgentResult, K> classifier) {
        return results.stream()
            .collect(Collectors.groupingBy(classifier));
    }
    
    // ==================== 统计分析 ====================
    
    /**
     * 获取平均执行时间
     */
    public double getAverageExecutionTime() {
        return results.isEmpty() ? 0 : 
            results.stream().mapToLong(SubAgentResult::getExecutionTimeMs).average().orElse(0);
    }
    
    /**
     * 获取最短执行时间
     */
    public long getMinExecutionTime() {
        return results.stream()
            .mapToLong(SubAgentResult::getExecutionTimeMs)
            .min()
            .orElse(0);
    }
    
    /**
     * 获取最长执行时间
     */
    public long getMaxExecutionTime() {
        return results.stream()
            .mapToLong(SubAgentResult::getExecutionTimeMs)
            .max()
            .orElse(0);
    }
    
    /**
     * 获取总 Token 使用
     */
    public int getTotalTokens() {
        return results.stream()
            .mapToInt(SubAgentResult::getTotalTokens)
            .sum();
    }
    
    /**
     * 获取总工具调用次数
     */
    public int getTotalToolCalls() {
        return results.stream()
            .mapToInt(SubAgentResult::getToolCallCount)
            .sum();
    }
    
    // ==================== 元数据 ====================
    
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    // ==================== 报告生成 ====================
    
    /**
     * 生成执行报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("╔══════════════════════════════════════════════════════════════╗\n");
        report.append("║              并行执行结果报告                                 ║\n");
        report.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        
        // 概览
        report.append("【执行概览】\n");
        report.append("执行ID: ").append(executionId).append("\n");
        report.append("执行时间: ").append(formatTimestamp(timestamp)).append("\n");
        report.append("总耗时: ").append(totalExecutionTimeMs).append("ms\n");
        report.append("总任务数: ").append(totalCount).append("\n");
        report.append("成功: ").append(successCount).append("\n");
        report.append("失败: ").append(failureCount).append("\n");
        report.append("成功率: ").append(String.format("%.2f%%", getSuccessRate())).append("\n");
        report.append("平均执行时间: ").append(String.format("%.2fms", getAverageExecutionTime())).append("\n");
        report.append("最短/最长: ").append(getMinExecutionTime()).append("ms / ")
              .append(getMaxExecutionTime()).append("ms\n\n");
        
        // 详细信息
        if (!results.isEmpty()) {
            report.append("【详细结果】\n");
            for (SubAgentResult result : results) {
                String statusIcon = result.isSuccess() ? "✓" : "✗";
                report.append(statusIcon).append(" ")
                      .append(result.getTaskId())
                      .append(" (").append(result.getExecutionTimeMs()).append("ms)");
                if (result.getAgentId() != null) {
                    report.append(" [").append(result.getAgentId()).append("]");
                }
                if (!result.isSuccess() && result.getError() != null) {
                    report.append(" - ").append(result.getError());
                }
                report.append("\n");
            }
            report.append("\n");
        }
        
        // 失败详情
        List<SubAgentResult> failures = getFailedResults();
        if (!failures.isEmpty()) {
            report.append("【失败详情】\n");
            for (SubAgentResult failure : failures) {
                report.append("- ").append(failure.getTaskId()).append(": ");
                report.append(failure.getError() != null ? failure.getError() : "Unknown error");
                report.append("\n");
            }
            report.append("\n");
        }
        
        return report.toString();
    }
    
    /**
     * 生成 JSON 格式的报告
     */
    public Map<String, Object> toReportMap() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("executionId", executionId);
        report.put("timestamp", timestamp);
        report.put("totalExecutionTimeMs", totalExecutionTimeMs);
        report.put("summary", Map.of(
            "total", totalCount,
            "success", successCount,
            "failure", failureCount,
            "successRate", getSuccessRate()
        ));
        report.put("statistics", Map.of(
            "avgExecutionTime", getAverageExecutionTime(),
            "minExecutionTime", getMinExecutionTime(),
            "maxExecutionTime", getMaxExecutionTime(),
            "totalTokens", getTotalTokens(),
            "totalToolCalls", getTotalToolCalls()
        ));
        report.put("results", results.stream()
            .map(r -> Map.of(
                "taskId", r.getTaskId(),
                "success", r.isSuccess(),
                "executionTimeMs", r.getExecutionTimeMs(),
                "agentId", r.getAgentId() != null ? r.getAgentId() : "unknown",
                "outputPreview", r.getOutput() != null ? 
                    (r.getOutput().length() > 100 ? r.getOutput().substring(0, 100) + "..." : r.getOutput()) : ""
            ))
            .collect(Collectors.toList()));
        return report;
    }
    
    private String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private List<SubAgentResult> results = new ArrayList<>();
        private long totalExecutionTimeMs;
        private Map<String, Object> metadata = new HashMap<>();
        
        public Builder results(List<SubAgentResult> results) {
            this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
            return this;
        }
        
        public Builder addResult(SubAgentResult result) {
            if (result != null) {
                this.results.add(result);
            }
            return this;
        }
        
        public Builder totalExecutionTimeMs(long totalExecutionTimeMs) {
            this.totalExecutionTimeMs = totalExecutionTimeMs;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }
        
        public ParallelExecutionResult build() {
            ParallelExecutionResult result = new ParallelExecutionResult(results, totalExecutionTimeMs);
            result.setMetadata(metadata);
            return result;
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "ParallelExecutionResult{executionId='%s', total=%d, success=%d, failure=%d, time=%dms}",
            executionId, totalCount, successCount, failureCount, totalExecutionTimeMs
        );
    }
}
