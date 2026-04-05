package com.jwcode.core.planner.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

/**
 * TaskAnalysis - AI 任务分析结果
 * 
 * 包含任务的完整分析信息：
 * - 意图分析：任务类型、目标、关键实体
 * - 复杂度评估：多维度复杂度评分
 * - 风险评估：风险点和缓解策略
 * - 执行策略：推荐的执行方式
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskAnalysis {
    
    private String taskId;
    private String originalRequest;
    private long analysisTimeMs;
    
    // 意图分析
    private IntentAnalysis intent;
    
    // 复杂度评估
    private ComplexityAnalysis complexity;
    
    // 风险评估
    private RiskAnalysis risk;
    
    // 资源预估
    private Estimation estimation;
    
    // 执行策略
    private ExecutionStrategy strategy;
    
    // 分析置信度
    private double confidence;
    
    // 分析原因/思考过程
    private String reasoning;
    
    // Getters and Setters
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getOriginalRequest() { return originalRequest; }
    public void setOriginalRequest(String originalRequest) { this.originalRequest = originalRequest; }
    public long getAnalysisTimeMs() { return analysisTimeMs; }
    public void setAnalysisTimeMs(long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }
    public IntentAnalysis getIntent() { return intent; }
    public void setIntent(IntentAnalysis intent) { this.intent = intent; }
    public ComplexityAnalysis getComplexity() { return complexity; }
    public void setComplexity(ComplexityAnalysis complexity) { this.complexity = complexity; }
    public RiskAnalysis getRisk() { return risk; }
    public void setRisk(RiskAnalysis risk) { this.risk = risk; }
    public Estimation getEstimation() { return estimation; }
    public void setEstimation(Estimation estimation) { this.estimation = estimation; }
    public ExecutionStrategy getStrategy() { return strategy; }
    public void setStrategy(ExecutionStrategy strategy) { this.strategy = strategy; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private String taskId;
        private String originalRequest;
        private long analysisTimeMs;
        private IntentAnalysis intent;
        private ComplexityAnalysis complexity;
        private RiskAnalysis risk;
        private Estimation estimation;
        private ExecutionStrategy strategy;
        private double confidence;
        private String reasoning;
        
        public Builder taskId(String v) { this.taskId = v; return this; }
        public Builder originalRequest(String v) { this.originalRequest = v; return this; }
        public Builder analysisTimeMs(long v) { this.analysisTimeMs = v; return this; }
        public Builder intent(IntentAnalysis v) { this.intent = v; return this; }
        public Builder complexity(ComplexityAnalysis v) { this.complexity = v; return this; }
        public Builder risk(RiskAnalysis v) { this.risk = v; return this; }
        public Builder estimation(Estimation v) { this.estimation = v; return this; }
        public Builder strategy(ExecutionStrategy v) { this.strategy = v; return this; }
        public Builder confidence(double v) { this.confidence = v; return this; }
        public Builder reasoning(String v) { this.reasoning = v; return this; }
        
        public TaskAnalysis build() {
            TaskAnalysis a = new TaskAnalysis();
            a.taskId = taskId;
            a.originalRequest = originalRequest;
            a.analysisTimeMs = analysisTimeMs;
            a.intent = intent;
            a.complexity = complexity;
            a.risk = risk;
            a.estimation = estimation;
            a.strategy = strategy;
            a.confidence = confidence;
            a.reasoning = reasoning;
            return a;
        }
    }
    
    /**
     * 意图分析
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IntentAnalysis {
        
        public enum IntentType {
            CREATE, DEBUG, REFACTOR, ANALYZE, TEST, LEARN, INTEGRATE, DEPLOY, GENERAL
        }
        
        private IntentType type;
        private double confidence;
        private String description;
        
        private List<String> targetFiles = new ArrayList<>();
        private List<String> targetModules = new ArrayList<>();
        private List<String> technologies = new ArrayList<>();
        private List<String> implicitRequirements = new ArrayList<>();
        
        // Getters and Setters
        public IntentType getType() { return type; }
        public void setType(IntentType type) { this.type = type; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getTargetFiles() { return targetFiles; }
        public void setTargetFiles(List<String> targetFiles) { this.targetFiles = targetFiles; }
        public List<String> getTargetModules() { return targetModules; }
        public void setTargetModules(List<String> targetModules) { this.targetModules = targetModules; }
        public List<String> getTechnologies() { return technologies; }
        public void setTechnologies(List<String> technologies) { this.technologies = technologies; }
        public List<String> getImplicitRequirements() { return implicitRequirements; }
        public void setImplicitRequirements(List<String> implicitRequirements) { this.implicitRequirements = implicitRequirements; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private IntentType type;
            private double confidence;
            private String description;
            private List<String> targetFiles = new ArrayList<>();
            private List<String> targetModules = new ArrayList<>();
            private List<String> technologies = new ArrayList<>();
            private List<String> implicitRequirements = new ArrayList<>();
            
            public Builder type(IntentType v) { this.type = v; return this; }
            public Builder confidence(double v) { this.confidence = v; return this; }
            public Builder description(String v) { this.description = v; return this; }
            public Builder targetFiles(List<String> v) { this.targetFiles = v; return this; }
            public Builder targetModules(List<String> v) { this.targetModules = v; return this; }
            public Builder technologies(List<String> v) { this.technologies = v; return this; }
            public Builder implicitRequirements(List<String> v) { this.implicitRequirements = v; return this; }
            
            public IntentAnalysis build() {
                IntentAnalysis a = new IntentAnalysis();
                a.type = type;
                a.confidence = confidence;
                a.description = description;
                a.targetFiles = targetFiles;
                a.targetModules = targetModules;
                a.technologies = technologies;
                a.implicitRequirements = implicitRequirements;
                return a;
            }
        }
    }
    
    /**
     * 复杂度分析
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComplexityAnalysis {
        
        private int overallScore;
        private int technicalComplexity;
        private int codeVolume;
        private int dependencyComplexity;
        private int integrationComplexity;
        private int testingComplexity;
        private String reasoning;
        
        private List<String> factors = new ArrayList<>();
        
        private String historicalComparison;
        
        // Getters and Setters
        public int getOverallScore() { return overallScore; }
        public void setOverallScore(int overallScore) { this.overallScore = overallScore; }
        public int getTechnicalComplexity() { return technicalComplexity; }
        public void setTechnicalComplexity(int technicalComplexity) { this.technicalComplexity = technicalComplexity; }
        public int getCodeVolume() { return codeVolume; }
        public void setCodeVolume(int codeVolume) { this.codeVolume = codeVolume; }
        public int getDependencyComplexity() { return dependencyComplexity; }
        public void setDependencyComplexity(int dependencyComplexity) { this.dependencyComplexity = dependencyComplexity; }
        public int getIntegrationComplexity() { return integrationComplexity; }
        public void setIntegrationComplexity(int integrationComplexity) { this.integrationComplexity = integrationComplexity; }
        public int getTestingComplexity() { return testingComplexity; }
        public void setTestingComplexity(int testingComplexity) { this.testingComplexity = testingComplexity; }
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
        public List<String> getFactors() { return factors; }
        public void setFactors(List<String> factors) { this.factors = factors; }
        public String getHistoricalComparison() { return historicalComparison; }
        public void setHistoricalComparison(String historicalComparison) { this.historicalComparison = historicalComparison; }
        
        /**
         * 获取复杂度等级
         */
        public ComplexityLevel getLevel() {
            if (overallScore <= 3) return ComplexityLevel.LOW;
            if (overallScore <= 6) return ComplexityLevel.MEDIUM;
            if (overallScore <= 8) return ComplexityLevel.HIGH;
            return ComplexityLevel.VERY_HIGH;
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private int overallScore;
            private int technicalComplexity;
            private int codeVolume;
            private int dependencyComplexity;
            private int integrationComplexity;
            private int testingComplexity;
            private String reasoning;
            private List<String> factors = new ArrayList<>();
            private String historicalComparison;
            
            public Builder overallScore(int v) { this.overallScore = v; return this; }
            public Builder technicalComplexity(int v) { this.technicalComplexity = v; return this; }
            public Builder codeVolume(int v) { this.codeVolume = v; return this; }
            public Builder dependencyComplexity(int v) { this.dependencyComplexity = v; return this; }
            public Builder integrationComplexity(int v) { this.integrationComplexity = v; return this; }
            public Builder testingComplexity(int v) { this.testingComplexity = v; return this; }
            public Builder reasoning(String v) { this.reasoning = v; return this; }
            public Builder factors(List<String> v) { this.factors = v; return this; }
            public Builder historicalComparison(String v) { this.historicalComparison = v; return this; }
            
            public ComplexityAnalysis build() {
                ComplexityAnalysis c = new ComplexityAnalysis();
                c.overallScore = overallScore;
                c.technicalComplexity = technicalComplexity;
                c.codeVolume = codeVolume;
                c.dependencyComplexity = dependencyComplexity;
                c.integrationComplexity = integrationComplexity;
                c.testingComplexity = testingComplexity;
                c.reasoning = reasoning;
                c.factors = factors;
                c.historicalComparison = historicalComparison;
                return c;
            }
        }
        
        public enum ComplexityLevel {
            LOW, MEDIUM, HIGH, VERY_HIGH
        }
    }
    
    /**
     * 风险分析
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskAnalysis {
        
        private RiskLevel overallLevel;
        
        private List<RiskItem> risks = new ArrayList<>();
        private List<String> mitigationStrategies = new ArrayList<>();
        private List<String> watchPoints = new ArrayList<>();
        
        // Getters and Setters
        public RiskLevel getOverallLevel() { return overallLevel; }
        public void setOverallLevel(RiskLevel overallLevel) { this.overallLevel = overallLevel; }
        public List<RiskItem> getRisks() { return risks; }
        public void setRisks(List<RiskItem> risks) { this.risks = risks; }
        public List<String> getMitigationStrategies() { return mitigationStrategies; }
        public void setMitigationStrategies(List<String> mitigationStrategies) { this.mitigationStrategies = mitigationStrategies; }
        public List<String> getWatchPoints() { return watchPoints; }
        public void setWatchPoints(List<String> watchPoints) { this.watchPoints = watchPoints; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private RiskLevel overallLevel;
            private List<RiskItem> risks = new ArrayList<>();
            private List<String> mitigationStrategies = new ArrayList<>();
            private List<String> watchPoints = new ArrayList<>();
            
            public Builder overallLevel(RiskLevel v) { this.overallLevel = v; return this; }
            public Builder risks(List<RiskItem> v) { this.risks = v; return this; }
            public Builder mitigationStrategies(List<String> v) { this.mitigationStrategies = v; return this; }
            public Builder watchPoints(List<String> v) { this.watchPoints = v; return this; }
            
            public RiskAnalysis build() {
                RiskAnalysis r = new RiskAnalysis();
                r.overallLevel = overallLevel;
                r.risks = risks;
                r.mitigationStrategies = mitigationStrategies;
                r.watchPoints = watchPoints;
                return r;
            }
        }
        
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RiskItem {
            private String id;
            private String description;
            private RiskLevel level;
            private double probability;
            private double impact;
            private String mitigation;
            
            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            public String getDescription() { return description; }
            public void setDescription(String description) { this.description = description; }
            public RiskLevel getLevel() { return level; }
            public void setLevel(RiskLevel level) { this.level = level; }
            public double getProbability() { return probability; }
            public void setProbability(double probability) { this.probability = probability; }
            public double getImpact() { return impact; }
            public void setImpact(double impact) { this.impact = impact; }
            public String getMitigation() { return mitigation; }
            public void setMitigation(String mitigation) { this.mitigation = mitigation; }
            
            public static Builder builder() { return new Builder(); }
            
            public static class Builder {
                private String id;
                private String description;
                private RiskLevel level;
                private double probability;
                private double impact;
                private String mitigation;
                
                public Builder id(String v) { this.id = v; return this; }
                public Builder description(String v) { this.description = v; return this; }
                public Builder level(RiskLevel v) { this.level = v; return this; }
                public Builder probability(double v) { this.probability = v; return this; }
                public Builder impact(double v) { this.impact = v; return this; }
                public Builder mitigation(String v) { this.mitigation = v; return this; }
                
                public RiskItem build() {
                    RiskItem r = new RiskItem();
                    r.id = id;
                    r.description = description;
                    r.level = level;
                    r.probability = probability;
                    r.impact = impact;
                    r.mitigation = mitigation;
                    return r;
                }
            }
        }
        
        public enum RiskLevel {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }
    
    /**
     * 资源预估
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Estimation {
        
        private long estimatedTimeMs;
        private long minTimeMs;
        private long maxTimeMs;
        private int estimatedInputTokens;
        private int estimatedOutputTokens;
        private int estimatedSubTasks;
        private int minSubTasks;
        private int maxSubTasks;
        private double confidence;
        private String reasoning;
        
        // Getters and Setters
        public long getEstimatedTimeMs() { return estimatedTimeMs; }
        public void setEstimatedTimeMs(long estimatedTimeMs) { this.estimatedTimeMs = estimatedTimeMs; }
        public long getMinTimeMs() { return minTimeMs; }
        public void setMinTimeMs(long minTimeMs) { this.minTimeMs = minTimeMs; }
        public long getMaxTimeMs() { return maxTimeMs; }
        public void setMaxTimeMs(long maxTimeMs) { this.maxTimeMs = maxTimeMs; }
        public int getEstimatedInputTokens() { return estimatedInputTokens; }
        public void setEstimatedInputTokens(int estimatedInputTokens) { this.estimatedInputTokens = estimatedInputTokens; }
        public int getEstimatedOutputTokens() { return estimatedOutputTokens; }
        public void setEstimatedOutputTokens(int estimatedOutputTokens) { this.estimatedOutputTokens = estimatedOutputTokens; }
        public int getEstimatedSubTasks() { return estimatedSubTasks; }
        public void setEstimatedSubTasks(int estimatedSubTasks) { this.estimatedSubTasks = estimatedSubTasks; }
        public int getMinSubTasks() { return minSubTasks; }
        public void setMinSubTasks(int minSubTasks) { this.minSubTasks = minSubTasks; }
        public int getMaxSubTasks() { return maxSubTasks; }
        public void setMaxSubTasks(int maxSubTasks) { this.maxSubTasks = maxSubTasks; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
        
        /**
         * 获取格式化的预估时间
         */
        public String getFormattedTime() {
            long seconds = estimatedTimeMs / 1000;
            if (seconds < 60) return seconds + "秒";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "分钟";
            long hours = minutes / 60;
            return hours + "小时" + (minutes % 60) + "分钟";
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private long estimatedTimeMs;
            private long minTimeMs;
            private long maxTimeMs;
            private int estimatedInputTokens;
            private int estimatedOutputTokens;
            private int estimatedSubTasks;
            private int minSubTasks;
            private int maxSubTasks;
            private double confidence;
            private String reasoning;
            
            public Builder estimatedTimeMs(long v) { this.estimatedTimeMs = v; return this; }
            public Builder minTimeMs(long v) { this.minTimeMs = v; return this; }
            public Builder maxTimeMs(long v) { this.maxTimeMs = v; return this; }
            public Builder estimatedInputTokens(int v) { this.estimatedInputTokens = v; return this; }
            public Builder estimatedOutputTokens(int v) { this.estimatedOutputTokens = v; return this; }
            public Builder estimatedSubTasks(int v) { this.estimatedSubTasks = v; return this; }
            public Builder minSubTasks(int v) { this.minSubTasks = v; return this; }
            public Builder maxSubTasks(int v) { this.maxSubTasks = v; return this; }
            public Builder confidence(double v) { this.confidence = v; return this; }
            public Builder reasoning(String v) { this.reasoning = v; return this; }
            
            public Estimation build() {
                Estimation e = new Estimation();
                e.estimatedTimeMs = estimatedTimeMs;
                e.minTimeMs = minTimeMs;
                e.maxTimeMs = maxTimeMs;
                e.estimatedInputTokens = estimatedInputTokens;
                e.estimatedOutputTokens = estimatedOutputTokens;
                e.estimatedSubTasks = estimatedSubTasks;
                e.minSubTasks = minSubTasks;
                e.maxSubTasks = maxSubTasks;
                e.confidence = confidence;
                e.reasoning = reasoning;
                return e;
            }
        }
    }
    
    /**
     * 执行策略
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExecutionStrategy {
        
        private ExecutionMode recommendedMode;
        private int recommendedParallelism;
        private boolean requiresReplanning;
        
        private List<String> replanningTriggers = new ArrayList<>();
        private String criticalPathDescription;
        private List<String> optimizationTips = new ArrayList<>();
        private boolean requiresHumanConfirmation;
        private List<String> confirmationPoints = new ArrayList<>();
        
        // Getters and Setters
        public ExecutionMode getRecommendedMode() { return recommendedMode; }
        public void setRecommendedMode(ExecutionMode recommendedMode) { this.recommendedMode = recommendedMode; }
        public int getRecommendedParallelism() { return recommendedParallelism; }
        public void setRecommendedParallelism(int recommendedParallelism) { this.recommendedParallelism = recommendedParallelism; }
        public boolean isRequiresReplanning() { return requiresReplanning; }
        public void setRequiresReplanning(boolean requiresReplanning) { this.requiresReplanning = requiresReplanning; }
        public List<String> getReplanningTriggers() { return replanningTriggers; }
        public void setReplanningTriggers(List<String> replanningTriggers) { this.replanningTriggers = replanningTriggers; }
        public String getCriticalPathDescription() { return criticalPathDescription; }
        public void setCriticalPathDescription(String criticalPathDescription) { this.criticalPathDescription = criticalPathDescription; }
        public List<String> getOptimizationTips() { return optimizationTips; }
        public void setOptimizationTips(List<String> optimizationTips) { this.optimizationTips = optimizationTips; }
        public boolean isRequiresHumanConfirmation() { return requiresHumanConfirmation; }
        public void setRequiresHumanConfirmation(boolean requiresHumanConfirmation) { this.requiresHumanConfirmation = requiresHumanConfirmation; }
        public List<String> getConfirmationPoints() { return confirmationPoints; }
        public void setConfirmationPoints(List<String> confirmationPoints) { this.confirmationPoints = confirmationPoints; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private ExecutionMode recommendedMode;
            private int recommendedParallelism;
            private boolean requiresReplanning;
            private List<String> replanningTriggers = new ArrayList<>();
            private String criticalPathDescription;
            private List<String> optimizationTips = new ArrayList<>();
            private boolean requiresHumanConfirmation;
            private List<String> confirmationPoints = new ArrayList<>();
            
            public Builder recommendedMode(ExecutionMode v) { this.recommendedMode = v; return this; }
            public Builder recommendedParallelism(int v) { this.recommendedParallelism = v; return this; }
            public Builder requiresReplanning(boolean v) { this.requiresReplanning = v; return this; }
            public Builder replanningTriggers(List<String> v) { this.replanningTriggers = v; return this; }
            public Builder criticalPathDescription(String v) { this.criticalPathDescription = v; return this; }
            public Builder optimizationTips(List<String> v) { this.optimizationTips = v; return this; }
            public Builder requiresHumanConfirmation(boolean v) { this.requiresHumanConfirmation = v; return this; }
            public Builder confirmationPoints(List<String> v) { this.confirmationPoints = v; return this; }
            
            public ExecutionStrategy build() {
                ExecutionStrategy s = new ExecutionStrategy();
                s.recommendedMode = recommendedMode;
                s.recommendedParallelism = recommendedParallelism;
                s.requiresReplanning = requiresReplanning;
                s.replanningTriggers = replanningTriggers;
                s.criticalPathDescription = criticalPathDescription;
                s.optimizationTips = optimizationTips;
                s.requiresHumanConfirmation = requiresHumanConfirmation;
                s.confirmationPoints = confirmationPoints;
                return s;
            }
        }
        
        public enum ExecutionMode {
            SERIAL, PARALLEL, ADAPTIVE, CONSERVATIVE
        }
    }
    
    /**
     * 格式化分析报告
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("AI 任务分析报告\n");
        sb.append("================\n\n");
        
        // 意图分析
        sb.append("意图分析:\n");
        sb.append("  类型: ").append(intent != null ? intent.getType() : "N/A").append("\n");
        sb.append("  描述: ").append(intent != null ? intent.getDescription() : "N/A").append("\n");
        sb.append("\n");
        
        // 复杂度评估
        sb.append("复杂度评估: ").append(complexity != null ? complexity.getOverallScore() : 0).append("/10\n");
        sb.append("\n");
        
        // 资源预估
        sb.append("预估时间: ").append(estimation != null ? estimation.getFormattedTime() : "N/A").append("\n");
        sb.append("\n");
        
        sb.append("分析耗时: ").append(analysisTimeMs).append("ms\n");
        
        return sb.toString();
    }
    
    /**
     * 检查是否需要使用 Agent Swarm
     */
    public boolean requiresSwarm() {
        return (complexity != null && complexity.getOverallScore() >= 5) || 
               (estimation != null && estimation.getEstimatedSubTasks() >= 3) ||
               (risk != null && risk.getOverallLevel() == RiskAnalysis.RiskLevel.HIGH);
    }
    
    /**
     * 检查是否需要人工确认
     */
    public boolean requiresHumanConfirmation() {
        return (strategy != null && strategy.isRequiresHumanConfirmation()) ||
               (risk != null && risk.getOverallLevel() == RiskAnalysis.RiskLevel.CRITICAL) ||
               (complexity != null && complexity.getOverallScore() >= 8);
    }
}
