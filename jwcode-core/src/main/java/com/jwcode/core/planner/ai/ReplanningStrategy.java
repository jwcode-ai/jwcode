package com.jwcode.core.planner.ai;

import com.jwcode.core.planner.AdaptiveExecutionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ReplanningStrategy - 重规划策略
 * 
 * 根据执行情况和失败原因选择最佳的重规划策略：
 * 1. SUBDIVIDE - 细化分解失败任务
 * 2. RETRY - 重试失败任务
 * 3. ADJUST_ORDER - 调整执行顺序
 * 4. CHANGE_AGENT - 更换 Agent 类型
 * 5. ABORT - 中止执行
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ReplanningStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(ReplanningStrategy.class);
    
    // 策略选择器列表
    private final List<StrategySelector> selectors;
    
    public ReplanningStrategy() {
        this.selectors = Arrays.asList(
            new AbortStrategySelector(),
            new SubdivideStrategySelector(),
            new ChangeAgentStrategySelector(),
            new AdjustOrderStrategySelector(),
            new RetryStrategySelector()
        );
    }
    
    /**
     * 选择重规划策略
     * 
     * @param context 任务上下文
     * @param failures 失败信息
     * @param monitorReport 监控报告
     * @return 选择的策略类型
     */
    public StrategyType selectStrategy(Object context, List<String> failures, 
                                       AdaptiveExecutionMonitor.ExecutionReport monitorReport) {
        // 收集所有策略的评分
        List<StrategyScore> scores = selectors.stream()
            .map(selector -> new StrategyScore(
                selector.getType(),
                selector.evaluate(context, failures, monitorReport)
            ))
            .sorted(Comparator.comparingDouble(StrategyScore::getScore).reversed())
            .collect(Collectors.toList());
        
        // 选择得分最高的策略（分数必须大于阈值）
        StrategyScore best = scores.get(0);
        if (best.getScore() > 0.3) {
            log.info("[ReplanningStrategy] 选择策略: " + best.getType() + 
                " (得分: " + String.format("%.2f", best.getScore()) + ")");
            return best.getType();
        }
        
        // 默认重试
        log.info("[ReplanningStrategy] 选择默认策略: RETRY");
        return StrategyType.RETRY;
    }
    
    /**
     * 获取策略建议
     */
    public ReplanningSuggestion getSuggestion(StrategyType type, Object context) {
        for (StrategySelector selector : selectors) {
            if (selector.getType() == type) {
                return selector.generateSuggestion(context);
            }
        }
        return ReplanningSuggestion.builder()
            .strategy(StrategyType.RETRY)
            .description("重试失败的任务")
            .confidence(0.5)
            .build();
    }
    
    // ==================== 策略选择器接口 ====================
    
    private interface StrategySelector {
        StrategyType getType();
        double evaluate(Object context, List<String> failures, AdaptiveExecutionMonitor.ExecutionReport report);
        ReplanningSuggestion generateSuggestion(Object context);
    }
    
    // ==================== 具体策略选择器 ====================
    
    /**
     * 中止策略选择器
     */
    private static class AbortStrategySelector implements StrategySelector {
        @Override
        public StrategyType getType() { return StrategyType.ABORT; }
        
        @Override
        public double evaluate(Object context, List<String> failures, AdaptiveExecutionMonitor.ExecutionReport report) {
            double score = 0;
            
            // 失败率过高
            if (report.getFailedSteps() >= 3) {
                double failureRate = (double) report.getFailedSteps() / report.getTotalSteps();
                if (failureRate > 0.7) score += 0.5;
            }
            
            // 检测到无法恢复的错误
            for (String failure : failures) {
                String lower = failure.toLowerCase();
                if (lower.contains("fatal") || lower.contains("无法") || lower.contains("cannot") ||
                    lower.contains("permission denied") || lower.contains("unauthorized")) {
                    score += 0.5;
                }
            }
            
            return Math.min(1.0, score);
        }
        
        @Override
        public ReplanningSuggestion generateSuggestion(Object context) {
            return ReplanningSuggestion.builder()
                .strategy(StrategyType.ABORT)
                .description("检测到无法恢复的错误，建议中止执行并人工介入")
                .confidence(0.9)
                .actions(List.of("中止当前执行", "记录错误日志", "通知用户"))
                .build();
        }
    }
    
    /**
     * 细化分解策略选择器
     */
    private static class SubdivideStrategySelector implements StrategySelector {
        @Override
        public StrategyType getType() { return StrategyType.SUBDIVIDE; }
        
        @Override
        public double evaluate(Object context, List<String> failures, AdaptiveExecutionMonitor.ExecutionReport report) {
            double score = 0;
            
            // 任务过于复杂
            for (String failure : failures) {
                String lower = failure.toLowerCase();
                if (lower.contains("complex") || lower.contains("too large") || 
                    lower.contains("超时") || lower.contains("timeout") ||
                    lower.contains("memory") || lower.contains("oom")) {
                    score += 0.3;
                }
            }
            
            // 执行时间过长
            long avgTime = report.getTotalExecutionTime() / Math.max(1, report.getCompletedSteps());
            if (avgTime > 300000) { // 5分钟
                score += 0.3;
            }
            
            // 失败率中等
            double failureRate = (double) report.getFailedSteps() / Math.max(1, report.getTotalSteps());
            if (failureRate > 0.3 && failureRate <= 0.6) {
                score += 0.2;
            }
            
            return Math.min(1.0, score);
        }
        
        @Override
        public ReplanningSuggestion generateSuggestion(Object context) {
            return ReplanningSuggestion.builder()
                .strategy(StrategyType.SUBDIVIDE)
                .description("将失败的任务细分为更小的子任务")
                .confidence(0.75)
                .actions(List.of(
                    "分析失败任务的具体步骤",
                    "将复杂步骤拆分为 2-3 个简单步骤",
                    "重新分配依赖关系",
                    "使用新计划继续执行"
                ))
                .build();
        }
    }
    
    /**
     * 更换 Agent 策略选择器
     */
    private static class ChangeAgentStrategySelector implements StrategySelector {
        @Override
        public StrategyType getType() { return StrategyType.CHANGE_AGENT; }
        
        @Override
        public double evaluate(Object context, List<String> failures, AdaptiveExecutionMonitor.ExecutionReport report) {
            double score = 0;
            
            // 特定类型的错误
            for (String failure : failures) {
                String lower = failure.toLowerCase();
                if (lower.contains("syntax error") || lower.contains("compile") ||
                    lower.contains("找不到符号") || lower.contains("cannot find symbol")) {
                    score += 0.4; // 代码问题，需要 coder/debug
                }
                if (lower.contains("test failed") || lower.contains("assertion") ||
                    lower.contains("验证失败")) {
                    score += 0.3; // 测试问题，需要 test/debug
                }
                if (lower.contains("design") || lower.contains("architecture") ||
                    lower.contains("结构")) {
                    score += 0.3; // 设计问题，需要 architect
                }
            }
            
            return Math.min(1.0, score);
        }
        
        @Override
        public ReplanningSuggestion generateSuggestion(Object context) {
            return ReplanningSuggestion.builder()
                .strategy(StrategyType.CHANGE_AGENT)
                .description("为失败的任务更换更适合的 Agent 类型")
                .confidence(0.7)
                .actions(List.of(
                    "分析失败原因和任务类型",
                    "选择更专业的 Agent",
                    "重新执行失败的任务"
                ))
                .build();
        }
    }
    
    /**
     * 调整顺序策略选择器
     */
    private static class AdjustOrderStrategySelector implements StrategySelector {
        @Override
        public StrategyType getType() { return StrategyType.ADJUST_ORDER; }
        
        @Override
        public double evaluate(Object context, List<String> failures, AdaptiveExecutionMonitor.ExecutionReport report) {
            double score = 0;
            
            // 依赖相关错误
            for (String failure : failures) {
                String lower = failure.toLowerCase();
                if (lower.contains("dependency") || lower.contains("依赖") ||
                    lower.contains("not found") || lower.contains("找不到") ||
                    lower.contains("missing")) {
                    score += 0.4;
                }
            }
            
            // 资源竞争
            if (failures.stream().anyMatch(f -> f.toLowerCase().contains("resource") || 
                                               f.toLowerCase().contains("lock") ||
                                               f.toLowerCase().contains("conflict"))) {
                score += 0.3;
            }
            
            return Math.min(1.0, score);
        }
        
        @Override
        public ReplanningSuggestion generateSuggestion(Object context) {
            return ReplanningSuggestion.builder()
                .strategy(StrategyType.ADJUST_ORDER)
                .description("调整任务的执行顺序，解决依赖问题")
                .confidence(0.65)
                .actions(List.of(
                    "重新分析任务依赖关系",
                    "确保前置任务先执行",
                    "减少资源竞争"
                ))
                .build();
        }
    }
    
    /**
     * 重试策略选择器
     */
    private static class RetryStrategySelector implements StrategySelector {
        @Override
        public StrategyType getType() { return StrategyType.RETRY; }
        
        @Override
        public double evaluate(Object context, List<String> failures, AdaptiveExecutionMonitor.ExecutionReport report) {
            // 重试是默认策略，分数较低
            double score = 0.2;
            
            // 临时性错误
            for (String failure : failures) {
                String lower = failure.toLowerCase();
                if (lower.contains("timeout") || lower.contains("network") ||
                    lower.contains("connection") || lower.contains("temporarily") ||
                    lower.contains("rate limit") || lower.contains("busy")) {
                    score += 0.3; // 临时性问题，适合重试
                }
            }
            
            // 低失败率
            double failureRate = (double) report.getFailedSteps() / Math.max(1, report.getTotalSteps());
            if (failureRate < 0.3) {
                score += 0.2;
            }
            
            return Math.min(1.0, score);
        }
        
        @Override
        public ReplanningSuggestion generateSuggestion(Object context) {
            return ReplanningSuggestion.builder()
                .strategy(StrategyType.RETRY)
                .description("重试失败的任务（可能是临时性问题）")
                .confidence(0.6)
                .actions(List.of(
                    "重置失败任务的状态",
                    "清除临时状态",
                    "重新执行失败的任务"
                ))
                .build();
        }
    }
    
    // ==================== 数据类 ====================
    
    public enum StrategyType {
        SUBDIVIDE,      // 细化分解
        RETRY,          // 重试
        ADJUST_ORDER,   // 调整顺序
        CHANGE_AGENT,   // 更换 Agent
        ABORT           // 中止
    }
    
    public static class ReplanningSuggestion {
        private StrategyType strategy;
        private String description;
        private double confidence;
        private List<String> actions;
        private Map<String, Object> metadata;
        
        public StrategyType getStrategy() { return strategy; }
        public String getDescription() { return description; }
        public double getConfidence() { return confidence; }
        public List<String> getActions() { return actions; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        public void setStrategy(StrategyType strategy) { this.strategy = strategy; }
        public void setDescription(String description) { this.description = description; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public void setActions(List<String> actions) { this.actions = actions; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private StrategyType strategy;
            private String description;
            private double confidence;
            private List<String> actions;
            private Map<String, Object> metadata;
            
            public Builder strategy(StrategyType v) { this.strategy = v; return this; }
            public Builder description(String v) { this.description = v; return this; }
            public Builder confidence(double v) { this.confidence = v; return this; }
            public Builder actions(List<String> v) { this.actions = v; return this; }
            public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }
            
            public ReplanningSuggestion build() {
                ReplanningSuggestion s = new ReplanningSuggestion();
                s.strategy = this.strategy;
                s.description = this.description;
                s.confidence = this.confidence;
                s.actions = this.actions;
                s.metadata = this.metadata;
                return s;
            }
        }
    }
    
    private static class StrategyScore {
        private final StrategyType type;
        private final double score;
        
        public StrategyScore(StrategyType type, double score) {
            this.type = type;
            this.score = score;
        }
        
        public StrategyType getType() { return type; }
        public double getScore() { return score; }
    }
}
