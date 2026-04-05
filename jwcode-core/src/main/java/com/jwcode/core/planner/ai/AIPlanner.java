package com.jwcode.core.planner.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.planner.PlanStep;
import com.jwcode.core.service.ApiClient;
import com.jwcode.core.service.ApiRequest;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AIPlanner - AI 驱动的任务规划器
 * 
 * 核心能力：
 * 1. 深度任务分析 - 使用 LLM 分析任务意图、复杂度、风险
 * 2. 动态任务分解 - 完全 AI 驱动，无预定义模板
 * 3. 智能依赖分析 - AI 分析子任务间的依赖关系
 * 4. 执行策略推荐 - 推荐最佳的执行模式和并行度
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AIPlanner {
    
    private static final Logger log = LoggerFactory.getLogger(AIPlanner.class);
    
    private final ApiClient apiClient;
    private final ObjectMapper objectMapper;
    
    // 分析结果缓存
    private final Map<String, CacheEntry> analysisCache;
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);
    
    // 学习记忆
    private final AILearningMemory learningMemory;
    
    public AIPlanner(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.objectMapper = new ObjectMapper();
        this.analysisCache = new ConcurrentHashMap<>();
        this.learningMemory = new AILearningMemory();
    }
    
    /**
     * 分析任务 - 主入口
     * 
     * @param taskDescription 任务描述
     * @param context 上下文信息
     * @return 任务分析结果
     */
    public CompletableFuture<TaskAnalysis> analyze(String taskDescription, Map<String, Object> context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // 1. 检查缓存
                String cacheKey = generateCacheKey(taskDescription, context);
                CacheEntry cached = analysisCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    log.info("[AIPlanner] 使用缓存的分析结果");
                    return cached.getAnalysis();
                }
                
                log.info("[AIPlanner] 开始 AI 任务分析: " + 
                    taskDescription.substring(0, Math.min(50, taskDescription.length())) + "...");
                
                // 2. 构建 AI 提示词
                String prompt = buildAnalysisPrompt(taskDescription, context);
                
                // 3. 调用 AI 进行分析
                String aiResponse = callAI(prompt).join();
                
                // 4. 解析 AI 响应
                TaskAnalysis analysis = parseAnalysisResponse(aiResponse);
                analysis.setTaskId(UUID.randomUUID().toString());
                analysis.setOriginalRequest(taskDescription);
                analysis.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
                
                // 5. 应用学习优化
                applyLearningOptimizations(analysis);
                
                // 6. 缓存结果
                analysisCache.put(cacheKey, new CacheEntry(analysis, System.currentTimeMillis()));
                
                log.info("[AIPlanner] 分析完成: 复杂度=" + analysis.getComplexity().getOverallScore() +
                    "/10, 预估子任务=" + analysis.getEstimation().getEstimatedSubTasks());
                
                return analysis;
                
            } catch (Exception e) {
                log.error("[AIPlanner] AI 分析失败: " + e.getMessage());
                // 返回 fallback 分析
                return createFallbackAnalysis(taskDescription, startTime);
            }
        });
    }
    
    /**
     * 分解任务为子任务
     * 
     * @param taskDescription 任务描述
     * @param analysis 任务分析结果
     * @return 子任务列表
     */
    public CompletableFuture<List<PlanStep>> decompose(String taskDescription, TaskAnalysis analysis) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[AIPlanner] 开始动态任务分解");
                
                // 1. 构建分解提示词
                String prompt = buildDecompositionPrompt(taskDescription, analysis);
                
                // 2. 调用 AI 进行分解
                String aiResponse = callAI(prompt).join();
                
                // 3. 解析子任务
                List<PlanStep> steps = parseDecompositionResponse(aiResponse);
                
                // 4. 验证和调整
                steps = validateAndAdjustSteps(steps, analysis);
                
                log.info("[AIPlanner] 分解完成: " + steps.size() + " 个子任务");
                
                return steps;
                
            } catch (Exception e) {
                log.error("[AIPlanner] 任务分解失败: " + e.getMessage());
                // 返回 fallback 分解
                return createFallbackDecomposition(taskDescription);
            }
        });
    }
    
    /**
     * 递归分解 - 对复杂子任务进一步分解
     * 
     * @param parentStep 父任务
     * @param threshold 递归阈值（复杂度超过此值才递归）
     * @return 分解后的子任务列表
     */
    public CompletableFuture<List<PlanStep>> decomposeRecursively(PlanStep parentStep, int threshold) {
        return analyze(parentStep.getDescription(), parentStep.getContext())
            .thenCompose(analysis -> {
                if (analysis.getComplexity().getOverallScore() >= threshold &&
                    analysis.getEstimation().getEstimatedSubTasks() > 1) {
                    log.info("[AIPlanner] 递归分解任务: " + parentStep.getDescription());
                    return decompose(parentStep.getDescription(), analysis);
                } else {
                    return CompletableFuture.completedFuture(List.of(parentStep));
                }
            });
    }
    
    /**
     * 智能合并 - 合并过于细粒度的任务
     * 
     * @param steps 子任务列表
     * @param minTaskSize 最小任务粒度（预估耗时毫秒）
     * @return 合并后的任务列表
     */
    public List<PlanStep> smartMerge(List<PlanStep> steps, long minTaskSize) {
        if (steps.size() <= 1) return steps;
        
        List<PlanStep> merged = new ArrayList<>();
        PlanStep current = null;
        
        for (PlanStep step : steps) {
            if (current == null) {
                current = step;
            } else if (canMerge(current, step) && 
                      (current.getEstimatedTimeMs() + step.getEstimatedTimeMs()) < minTaskSize * 2) {
                // 合并任务
                current = mergeSteps(current, step);
            } else {
                merged.add(current);
                current = step;
            }
        }
        
        if (current != null) {
            merged.add(current);
        }
        
        log.info("[AIPlanner] 智能合并: " + steps.size() + " -> " + merged.size() + " 个任务");
        return merged;
    }
    
    /**
     * 构建分析提示词
     */
    private String buildAnalysisPrompt(String taskDescription, Map<String, Object> context) {
        StringBuilder contextStr = new StringBuilder();
        if (context != null && !context.isEmpty()) {
            contextStr.append("\n上下文信息:\n");
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                contextStr.append("- ").append(entry.getKey()).append(": ")
                         .append(entry.getValue()).append("\n");
            }
        }
        
        // 获取相关历史经验
        List<AILearningMemory.TaskPattern> similarPatterns = learningMemory.findSimilarPatterns(taskDescription);
        String historicalInsights = "";
        if (!similarPatterns.isEmpty()) {
            historicalInsights = "\n历史经验参考:\n";
            for (AILearningMemory.TaskPattern pattern : similarPatterns.subList(0, Math.min(3, similarPatterns.size()))) {
                historicalInsights += String.format("- 类似任务 '%s' 实际耗时 %dms, 成功率 %.0f%%\n",
                    pattern.getDescription().substring(0, Math.min(30, pattern.getDescription().length())),
                    pattern.getActualDurationMs(),
                    pattern.getSuccessRate() * 100);
            }
        }
        
        return String.format("""
            你是一位专业的软件开发任务规划专家。请深度分析以下任务并提供详细的评估报告。
            
            任务描述: %s
            %s
            %s
            
            请提供以下分析（严格 JSON 格式，不要包含 markdown 代码块标记）:
            
            {
              "intent": {
                "type": "任务类型 (CREATE/DEBUG/REFACTOR/ANALYZE/TEST/LEARN/INTEGRATE/DEPLOY/GENERAL)",
                "confidence": 置信度0-1,
                "description": "意图详细描述",
                "targetFiles": ["可能涉及的文件"],
                "targetModules": ["可能涉及的模块"],
                "technologies": ["相关技术栈"],
                "implicitRequirements": ["隐含需求"]
              },
              "complexity": {
                "overallScore": 总体复杂度1-10,
                "technicalComplexity": 技术复杂度1-10,
                "codeVolume": 代码量复杂度1-10,
                "dependencyComplexity": 依赖复杂度1-10,
                "integrationComplexity": 集成复杂度1-10,
                "testingComplexity": 测试复杂度1-10,
                "reasoning": "复杂度评估理由",
                "factors": ["影响复杂度的因素"]
              },
              "risk": {
                "overallLevel": "风险等级 (LOW/MEDIUM/HIGH/CRITICAL)",
                "risks": [
                  {
                    "id": "risk-1",
                    "description": "风险描述",
                    "level": "风险等级",
                    "probability": 概率0-1,
                    "impact": 影响0-1,
                    "mitigation": "缓解措施"
                  }
                ],
                "mitigationStrategies": ["整体缓解策略"],
                "watchPoints": ["需要特别关注的点"]
              },
              "estimation": {
                "estimatedTimeMs": 预估时间毫秒数,
                "minTimeMs": 最短时间毫秒数,
                "maxTimeMs": 最长时间毫秒数,
                "estimatedInputTokens": 预估输入token数,
                "estimatedOutputTokens": 预估输出token数,
                "estimatedSubTasks": 预估子任务数,
                "minSubTasks": 最小子任务数,
                "maxSubTasks": 最大子任务数,
                "confidence": 预估置信度0-1,
                "reasoning": "预估依据"
              },
              "strategy": {
                "recommendedMode": "推荐执行模式 (SERIAL/PARALLEL/ADAPTIVE/CONSERVATIVE)",
                "recommendedParallelism": 推荐并行度,
                "requiresReplanning": 是否需要重规划布尔值,
                "replanningTriggers": ["重规划触发条件"],
                "criticalPathDescription": "关键路径说明",
                "optimizationTips": ["优化建议"],
                "requiresHumanConfirmation": 是否需要人工确认布尔值,
                "confirmationPoints": ["需要确认的点"]
              },
              "confidence": 整体分析置信度0-1,
              "reasoning": "整体思考过程"
            }
            
            注意：
            1. 所有数值必须合理，不能为 null
            2. 数组可以为空但不能为 null
            3. 时间预估单位为毫秒
            4. 复杂度评分 1-10，1 最简单，10 最复杂
            """, taskDescription, contextStr, historicalInsights);
    }
    
    /**
     * 构建分解提示词
     */
    private String buildDecompositionPrompt(String taskDescription, TaskAnalysis analysis) {
        return String.format("""
            你是一位专业的任务分解专家。请将以下任务分解为可执行的子任务。
            
            原始任务: %s
            
            任务分析:
            - 类型: %s
            - 复杂度: %d/10
            - 预估子任务数: %d-%d
            - 推荐并行度: %d
            
            请生成子任务列表（严格 JSON 格式，不要包含 markdown 代码块标记）:
            
            {
              "subTasks": [
                {
                  "id": "task-1",
                  "action": "任务名称（简洁动词+名词）",
                  "description": "详细描述",
                  "agentType": "推荐Agent类型 (analyzer/coder/debug/test/reviewer/architect)",
                  "priority": 优先级1-10,
                  "dependencies": ["依赖的任务id，可为空数组"],
                  "expectedOutput": "预期输出",
                  "estimatedTimeMs": 预估耗时毫秒数
                }
              ],
              "reasoning": "分解思考过程"
            }
            
            要求:
            1. 子任务粒度适中，每个任务预估耗时 30秒-5分钟
            2. 明确依赖关系，避免循环依赖
            3. 尽可能支持并行执行
            4. 任务编号格式: task-1, task-2, ...
            5. 依赖使用 id 引用，不要依赖后续任务
            """,
            taskDescription,
            analysis.getIntent().getType(),
            analysis.getComplexity().getOverallScore(),
            analysis.getEstimation().getMinSubTasks(),
            analysis.getEstimation().getMaxSubTasks(),
            analysis.getStrategy().getRecommendedParallelism()
        );
    }
    
    /**
     * 调用 AI
     */
    private CompletableFuture<String> callAI(String prompt) {
        ApiRequest request = ApiRequest.builder()
            .model("sonnet")
            .messages(List.of(Message.createUserMessage(prompt)))
            .build();
        
        return apiClient.sendRequest(request)
            .thenApply(response -> {
                if (response.hasError()) {
                    throw new RuntimeException("AI 调用失败: " + response.getErrorMessage());
                }
                return response.getContent();
            });
    }
    
    /**
     * 解析分析响应
     */
    private TaskAnalysis parseAnalysisResponse(String aiResponse) throws Exception {
        // 清理响应，移除可能的 markdown 代码块标记
        String cleaned = aiResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        
        JsonNode root = objectMapper.readTree(cleaned);
        return objectMapper.treeToValue(root, TaskAnalysis.class);
    }
    
    /**
     * 解析分解响应
     */
    private List<PlanStep> parseDecompositionResponse(String aiResponse) throws Exception {
        // 清理响应
        String cleaned = aiResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        
        JsonNode root = objectMapper.readTree(cleaned);
        JsonNode subTasksNode = root.get("subTasks");
        
        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < subTasksNode.size(); i++) {
            JsonNode taskNode = subTasksNode.get(i);
            
            PlanStep step = PlanStep.builder()
                .stepNumber(i + 1)
                .action(taskNode.get("action").asText())
                .description(taskNode.get("description").asText())
                .agentType(taskNode.get("agentType").asText())
                .priority(taskNode.has("priority") ? taskNode.get("priority").asInt() : 5)
                .dependencies(parseDependencies(taskNode.get("dependencies")))
                .expectedOutput(taskNode.has("expectedOutput") ? taskNode.get("expectedOutput").asText() : "")
                .estimatedTimeMs(taskNode.has("estimatedTimeMs") ? taskNode.get("estimatedTimeMs").asLong() : 60000)
                .build();
            
            steps.add(step);
        }
        
        return steps;
    }
    
    private List<String> parseDependencies(JsonNode depsNode) {
        List<String> deps = new ArrayList<>();
        if (depsNode != null && depsNode.isArray()) {
            for (JsonNode dep : depsNode) {
                deps.add(dep.asText());
            }
        }
        return deps;
    }
    
    /**
     * 验证和调整步骤
     */
    private List<PlanStep> validateAndAdjustSteps(List<PlanStep> steps, TaskAnalysis analysis) {
        // 1. 确保依赖关系正确
        Set<String> validIds = steps.stream()
            .map(s -> "task-" + s.getStepNumber())
            .collect(java.util.stream.Collectors.toSet());
        
        for (PlanStep step : steps) {
            step.getDependencies().removeIf(dep -> !validIds.contains(dep));
        }
        
        // 2. 根据分析调整优先级
        if (analysis.getRisk().getOverallLevel() == TaskAnalysis.RiskAnalysis.RiskLevel.HIGH) {
            // 高风险任务降低并行度，提高优先级
            for (PlanStep step : steps) {
                step.setPriority(Math.min(10, step.getPriority() + 1));
            }
        }
        
        return steps;
    }
    
    /**
     * 应用学习优化
     */
    private void applyLearningOptimizations(TaskAnalysis analysis) {
        // 根据历史数据调整预估
        List<AILearningMemory.TaskPattern> patterns = learningMemory.findSimilarPatterns(
            analysis.getOriginalRequest()
        );
        
        if (!patterns.isEmpty()) {
            // 计算平均实际耗时
            long avgActualTime = (long) patterns.stream()
                .mapToLong(AILearningMemory.TaskPattern::getActualDurationMs)
                .average()
                .orElse(analysis.getEstimation().getEstimatedTimeMs());
            
            // 调整预估（加权平均）
            long adjustedTime = (analysis.getEstimation().getEstimatedTimeMs() + avgActualTime) / 2;
            analysis.getEstimation().setEstimatedTimeMs(adjustedTime);
            
            log.info("[AIPlanner] 应用学习优化: 时间预估调整为 " + adjustedTime + "ms");
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String taskDescription, Map<String, Object> context) {
        return taskDescription.hashCode() + "_" + (context != null ? context.hashCode() : 0);
    }
    
    /**
     * 检查是否可以合并两个任务
     */
    private boolean canMerge(PlanStep step1, PlanStep step2) {
        // 相同 Agent 类型
        if (!Objects.equals(step1.getAgentType(), step2.getAgentType())) {
            return false;
        }
        
        // step2 不依赖 step1 之后的任务
        // 简化判断：只要 step2 只依赖 step1 或更前的任务，就可以合并
        return step2.getDependencies().stream()
            .allMatch(dep -> dep.equals("task-" + step1.getStepNumber()) ||
                   Integer.parseInt(dep.replace("task-", "")) < step1.getStepNumber());
    }
    
    /**
     * 合并两个任务
     */
    private PlanStep mergeSteps(PlanStep step1, PlanStep step2) {
        return PlanStep.builder()
            .stepNumber(step1.getStepNumber())
            .action(step1.getAction() + " + " + step2.getAction())
            .description(step1.getDescription() + "\n同时执行:\n" + step2.getDescription())
            .agentType(step1.getAgentType())
            .priority(Math.max(step1.getPriority(), step2.getPriority()))
            .dependencies(step1.getDependencies())
            .expectedOutput(step1.getExpectedOutput() + "; " + step2.getExpectedOutput())
            .estimatedTimeMs(step1.getEstimatedTimeMs() + step2.getEstimatedTimeMs())
            .build();
    }
    
    /**
     * Fallback 分析
     */
    private TaskAnalysis createFallbackAnalysis(String taskDescription, long startTime) {
        return TaskAnalysis.builder()
            .taskId(UUID.randomUUID().toString())
            .originalRequest(taskDescription)
            .analysisTimeMs(System.currentTimeMillis() - startTime)
            .intent(TaskAnalysis.IntentAnalysis.builder()
                .type(TaskAnalysis.IntentAnalysis.IntentType.GENERAL)
                .confidence(0.5)
                .description("通用任务")
                .build())
            .complexity(TaskAnalysis.ComplexityAnalysis.builder()
                .overallScore(5)
                .technicalComplexity(5)
                .codeVolume(5)
                .dependencyComplexity(5)
                .integrationComplexity(5)
                .testingComplexity(5)
                .reasoning("Fallback 分析")
                .build())
            .risk(TaskAnalysis.RiskAnalysis.builder()
                .overallLevel(TaskAnalysis.RiskAnalysis.RiskLevel.MEDIUM)
                .build())
            .estimation(TaskAnalysis.Estimation.builder()
                .estimatedTimeMs(300000)  // 5分钟
                .minTimeMs(60000)
                .maxTimeMs(600000)
                .estimatedSubTasks(3)
                .minSubTasks(2)
                .maxSubTasks(5)
                .confidence(0.5)
                .reasoning("Fallback 预估")
                .build())
            .strategy(TaskAnalysis.ExecutionStrategy.builder()
                .recommendedMode(TaskAnalysis.ExecutionStrategy.ExecutionMode.ADAPTIVE)
                .recommendedParallelism(2)
                .requiresReplanning(false)
                .requiresHumanConfirmation(true)
                .build())
            .confidence(0.5)
            .reasoning("AI 分析失败，使用 fallback 分析")
            .build();
    }
    
    /**
     * Fallback 分解
     */
    private List<PlanStep> createFallbackDecomposition(String taskDescription) {
        List<PlanStep> steps = new ArrayList<>();
        
        steps.add(PlanStep.builder()
            .stepNumber(1)
            .action("理解需求")
            .description("理解任务需求: " + taskDescription)
            .agentType("analyzer")
            .priority(10)
            .dependencies(new ArrayList<>())
            .expectedOutput("需求理解")
            .estimatedTimeMs(60000)
            .build());
        
        steps.add(PlanStep.builder()
            .stepNumber(2)
            .action("执行任务")
            .description("执行核心任务")
            .agentType("coder")
            .priority(8)
            .dependencies(List.of("task-1"))
            .expectedOutput("任务结果")
            .estimatedTimeMs(180000)
            .build());
        
        steps.add(PlanStep.builder()
            .stepNumber(3)
            .action("验证结果")
            .description("验证执行结果")
            .agentType("test")
            .priority(7)
            .dependencies(List.of("task-2"))
            .expectedOutput("验证报告")
            .estimatedTimeMs(60000)
            .build());
        
        return steps;
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        analysisCache.clear();
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final TaskAnalysis analysis;
        private final long timestamp;
        
        CacheEntry(TaskAnalysis analysis, long timestamp) {
            this.analysis = analysis;
            this.timestamp = timestamp;
        }
        
        TaskAnalysis getAnalysis() {
            return analysis;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION.toMillis();
        }
    }
}
