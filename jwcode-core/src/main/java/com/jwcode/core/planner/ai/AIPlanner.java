package com.jwcode.core.planner.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.planner.PlanStep;
import com.jwcode.core.planner.LayeredTaskRepresentation;
import com.jwcode.core.llm.*;
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
    
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    
    // 分析结果缓存
    private final Map<String, CacheEntry> analysisCache;
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);
    
    // 学习记忆
    private final AILearningMemory learningMemory;
    
    public AIPlanner(LLMService llmService) {
        this.llmService = llmService;
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
                List<PlanStep> steps = parseDecompositionResponse(aiResponse, taskDescription);
                
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
     * 【Phase 4 接线】分解任务并返回完整的分层任务表征。
     * 
     * <p>内部调用 decompose() 获取子任务列表，然后将结果包装为
     * {@link LayeredTaskRepresentation}，确保三层（Goal/Planning/Execution）完整。</p>
     * 
     * @param taskDescription 任务描述
     * @param analysis 任务分析结果
     * @return 包含三层结构的 LayeredTaskRepresentation
     */
    public CompletableFuture<LayeredTaskRepresentation> decomposeLayered(
            String taskDescription, TaskAnalysis analysis) {
        return decompose(taskDescription, analysis)
            .thenApply(steps -> {
                LayeredTaskRepresentation layered = new LayeredTaskRepresentation(taskDescription);
                layered.getPlanningLayer().setSteps(steps);
                log.info("[AIPlanner] 分层任务表征已创建 | goal='{}' | steps={}",
                    taskDescription.substring(0, Math.min(50, taskDescription.length())),
                    steps.size());
                return layered;
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
            ⚠️ CRITICAL FORMAT RULE — READ THIS FIRST ⚠️
            Your ENTIRE response MUST be a single valid JSON object, starting with { and ending with }.
            NO markdown fences. NO text before {. NO text after }.
            If your response is not valid JSON, it will be rejected and you will have to retry.
            ALL fields are required — use [] for empty arrays, 0 for unknown numbers, "" for unknown strings.
            
            你是一位专业的软件开发任务规划专家。请深度分析以下任务并提供详细的评估报告。
            
            任务描述: %s
            %s
            %s
            
            输出以下 JSON（严格遵守字段名和类型）:
            
            {
              "intent": {
                "type": "GENERAL",
                "confidence": 0.5,
                "description": "",
                "targetFiles": [],
                "targetModules": [],
                "technologies": [],
                "implicitRequirements": []
              },
              "complexity": {
                "overallScore": 5,
                "technicalComplexity": 5,
                "codeVolume": 5,
                "dependencyComplexity": 5,
                "integrationComplexity": 5,
                "testingComplexity": 5,
                "reasoning": "",
                "factors": []
              },
              "risk": {
                "overallLevel": "MEDIUM",
                "risks": [],
                "mitigationStrategies": [],
                "watchPoints": []
              },
              "estimation": {
                "estimatedTimeMs": 60000,
                "minTimeMs": 10000,
                "maxTimeMs": 300000,
                "estimatedInputTokens": 1000,
                "estimatedOutputTokens": 2000,
                "estimatedSubTasks": 3,
                "minSubTasks": 1,
                "maxSubTasks": 5,
                "confidence": 0.5,
                "reasoning": ""
              },
              "strategy": {
                "recommendedMode": "SERIAL",
                "recommendedParallelism": 1,
                "requiresReplanning": false,
                "replanningTriggers": [],
                "criticalPathDescription": "",
                "optimizationTips": [],
                "requiresHumanConfirmation": false,
                "confirmationPoints": []
              },
              "confidence": 0.5,
              "reasoning": ""
            }
            
            ## 字段说明
            - intent.type: CREATE/DEBUG/REFACTOR/ANALYZE/TEST/LEARN/INTEGRATE/DEPLOY/GENERAL
            - risk.overallLevel: LOW/MEDIUM/HIGH/CRITICAL
            - risk.risks[].level: LOW/MEDIUM/HIGH/CRITICAL
            - strategy.recommendedMode: SERIAL/PARALLEL/ADAPTIVE/CONSERVATIVE
            - 所有分数 1-10, 置信度 0-1, 时间单位毫秒
            - 布尔值使用 true/false (小写)
            
            ## 禁止事项 (VIOLATION = 回复无效)
            1. ❌ 不得在 JSON 外添加任何文字（不含解释、不含问候、不含标记）
            2. ❌ 不得用 ```json ``` 包裹
            3. ❌ 不得虚构不存在的项目信息 — 不确定就用默认值
            4. ❌ 不得省略任何字段 — 每个字段都必须出现
            5. ❌ 不得返回 null — 数组为空写 []，对象为空写 {}
            """, taskDescription, contextStr, historicalInsights);
    }
    
    /**
     * 构建分解提示词
     */
    private String buildDecompositionPrompt(String taskDescription, TaskAnalysis analysis) {
        return String.format("""
            ⚠️ CRITICAL FORMAT RULE — READ THIS FIRST ⚠️
            Your ENTIRE response MUST be a single valid JSON object, starting with { and ending with }.
            NO markdown fences. NO text before {. NO text after }.
            If your response is not valid JSON, it will be rejected.
            
            你是一位专业的任务分解专家。请将以下任务分解为可执行的子任务。
            
            原始任务: %s
            
            任务分析:
            - 类型: %s
            - 复杂度: %d/10
            - 预估子任务数: %d-%d
            - 推荐并行度: %d
            
            输出以下 JSON:

            {
              "subTasks": [
                {
                  "id": "task-1",
                  "action": "读取配置文件",
                  "description": "使用 Read 工具读取 config.json 的内容",
                  "agentType": "explorer",
                  "priority": 8,
                  "dependencies": [],
                  "expectedOutput": "配置文件内容及结构说明",
                  "estimatedTimeMs": 30000,
                  "stepPrompt": "你需要使用文件读取工具打开 config.json 文件，分析其结构并记录所有配置项的键值对"
                }
              ],
              "reasoning": "任务分解思路"
            }

            ## 字段说明
            - id: 格式 "task-N" (N 从 1 开始递增)
            - action: 简洁的动词+名词，如 "读取配置文件"、"编写单元测试"
            - description: 具体要做什么，包含工具名或文件路径
            - agentType: coder/test/reviewer/debug/doc/explorer/architect
            - priority: 1-10, 数字越大优先级越高
            - dependencies: 依赖的 task id 列表，无依赖写 []
            - expectedOutput: 可验证的验收标准（具体、可检查）
            - estimatedTimeMs: 预估毫秒数 (30000=30秒, 300000=5分钟)
            - stepPrompt: 进入此步骤时向AI注入的上下文提示，说明这个步骤的背景、目标、需要关注的要点和预期产出。用中文写，50-200字。
            - reasoning: 简要说明分解思路

            ## 硬性要求 (VIOLATION = 回复无效)
            1. ❌ 不得在 JSON 外添加任何文字
            2. ❌ 不得用 ```json ``` 包裹
            3. ❌ subTasks 数组不能为空 — 至少包含1个任务
            4. ❌ 不得生成无法验证的模糊任务（如"优化代码"、"改进性能"）
            5. ❌ 依赖关系不能有循环 — 必须是 DAG
            6. ❌ 每个任务必须有明确的 expectedOutput (验收标准)
            7. ❌ 不得依赖后续任务 (task-2 不能依赖 task-3)
            8. ❌ 每个任务必须有 stepPrompt (AI上下文提示)
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
        List<LLMMessage> messages = List.of(LLMMessage.user(prompt));
        
        return llmService.chat(messages)
            .thenApply(response -> {
                if (response.hasError()) {
                    throw new RuntimeException("AI 调用失败: " + response.getErrorMessage());
                }
                return response.getContent();
            });
    }
    
    /**
     * 解析分析响应（带增强容错）
     */
    private TaskAnalysis parseAnalysisResponse(String aiResponse) throws Exception {
        String cleaned = extractJson(aiResponse);
        
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            return objectMapper.treeToValue(root, TaskAnalysis.class);
        } catch (Exception e) {
            log.warn("[AIPlanner] JSON 解析失败，尝试修复: " + e.getMessage());
            // 尝试修复常见 JSON 问题
            String repaired = repairJson(cleaned);
            JsonNode root = objectMapper.readTree(repaired);
            return objectMapper.treeToValue(root, TaskAnalysis.class);
        }
    }
    
    /**
     * 解析分解响应（带增强容错）
     */
    private List<PlanStep> parseDecompositionResponse(String aiResponse, String taskDescription) throws Exception {
        String cleaned = extractJson(aiResponse);
        
        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("[AIPlanner] 子任务 JSON 解析失败，尝试修复: " + e.getMessage());
            String repaired = repairJson(cleaned);
            root = objectMapper.readTree(repaired);
        }
        
        JsonNode subTasksNode = root.get("subTasks");
        if (subTasksNode == null || !subTasksNode.isArray() || subTasksNode.size() == 0) {
            log.warn("[AIPlanner] subTasks 为空或不存在，使用默认单任务");
            return List.of(createFallbackStep(taskDescription));
        }
        
        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < subTasksNode.size(); i++) {
            JsonNode taskNode = subTasksNode.get(i);
            
            PlanStep step = PlanStep.builder()
                .stepNumber(i + 1)
                .action(taskNode.has("action") ? taskNode.get("action").asText() : ("任务步骤 " + (i + 1)))
                .description(taskNode.has("description") ? taskNode.get("description").asText() : taskNode.toString())
                .agentType(taskNode.has("agentType") ? taskNode.get("agentType").asText() : "coder")
                .priority(taskNode.has("priority") ? taskNode.get("priority").asInt() : 5)
                .dependencies(parseDependencies(taskNode.get("dependencies")))
                .expectedOutput(taskNode.has("expectedOutput") ? taskNode.get("expectedOutput").asText() : "")
                .estimatedTimeMs(taskNode.has("estimatedTimeMs") ? taskNode.get("estimatedTimeMs").asLong() : 60000)
                .stepPrompt(taskNode.has("stepPrompt") ? taskNode.get("stepPrompt").asText() : 
                    ("执行步骤" + (i + 1) + "：" + (taskNode.has("action") ? taskNode.get("action").asText() : "任务")))
                .build();
            
            steps.add(step);
        }
        
        return steps;
    }
    
    /**
     * 从 AI 响应中提取 JSON（去除 markdown 代码块、前后文字）
     */
    private String extractJson(String aiResponse) {
        String cleaned = aiResponse.trim();
        
        // 移除 markdown 代码块
        cleaned = cleaned.replaceAll("(?i)```json\\s*", "").replaceAll("(?i)```\\s*", "");
        
        // 找到第一个 { 和最后一个 }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }
        
        return cleaned.trim();
    }
    
    /**
     * 修复常见 JSON 问题
     */
    private String repairJson(String json) {
        // 移除尾部逗号 (在 ] 或 } 之前)
        json = json.replaceAll(",\\s*([}\\]])", "$1");
        // 修复单引号为双引号 (简单情况)
        // 移除注释 (// ...)
        json = json.replaceAll("//[^\n]*", "");
        // 移除注释 (/* ... */)
        json = json.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");
        // 移除尾部逗号后的换行
        json = json.replaceAll(",\\s*\n\\s*([}\\]])", "\n$1");
        
        return json.trim();
    }
    
    /**
     * 创建 fallback 步骤
     */
    private PlanStep createFallbackStep(String taskDescription) {
        return PlanStep.builder()
            .stepNumber(1)
            .action("执行任务")
            .description(taskDescription)
            .agentType("coder")
            .priority(5)
            .dependencies(List.of())
            .expectedOutput("任务完成")
            .estimatedTimeMs(120000)
            .stepPrompt("请执行以下任务：" + taskDescription + "。按照用户的要求完成所有必要的工作，并在完成后汇报结果。")
            .build();
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
