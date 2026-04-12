package com.jwcode.core.planner;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.agent.parallel.SubAgentTask;
import com.jwcode.core.planner.ai.AITaskPlanner;
import com.jwcode.core.planner.ai.DynamicExecutionEngine;
import com.jwcode.core.planner.ai.TaskAnalysis;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能任务规划器 - AI 驱动版本
 * 
 * 这是 AI 驱动任务规划的统一入口，支持两种模式：
 * 1. AI 驱动模式（推荐）- 完全 AI 分析和分解
 * 2. 规则模式（兼容）- 基于规则的快速分解
 * 
 * 升级特性：
 * - 完全 AI 驱动的任务分析
 * - 动态任务分解
 * - 递归分解支持
 * - 智能依赖分析
 * - 执行追踪
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskPlanner {
    
    private static final Logger log = LoggerFactory.getLogger(TaskPlanner.class);
    
    private final AgentRegistry agentRegistry;
    private final PlanValidator planValidator;
    private final AITaskPlanner aiTaskPlanner;
    
    // 模式选择
    private boolean aiMode = true;  // 默认使用 AI 模式
    
    // 任务拆解模式（用于规则模式）
    private static final Pattern STEP_PATTERN = Pattern.compile(
        "^(?:步骤?|Step|Task)\\s*(\\d+)[.:：\\s]*(.*)$", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    public TaskPlanner(AgentRegistry agentRegistry) {
        this(agentRegistry, null, null);
    }
    
    public TaskPlanner(AgentRegistry agentRegistry, LLMService llmService, ToolRegistry toolRegistry) {
        this.agentRegistry = agentRegistry;
        this.planValidator = new PlanValidator();
        
        // 初始化 AI 规划器
        if (llmService != null && toolRegistry != null) {
            this.aiTaskPlanner = new AITaskPlanner(llmService, toolRegistry);
        } else {
            this.aiTaskPlanner = null;
        }
    }
    
    /**
     * 设置规划模式
     * @param aiMode true=AI 驱动模式, false=规则模式
     */
    public void setAiMode(boolean aiMode) {
        this.aiMode = aiMode && aiTaskPlanner != null;
    }
    
    /**
     * 规划任务 - 主入口（AI 驱动）
     * 
     * @param userRequest 用户的自然语言请求
     * @param context 上下文信息
     * @return 执行计划
     */
    public ExecutionPlan plan(String userRequest, PlanningContext context) {
        if (aiMode && aiTaskPlanner != null) {
            return planWithAI(userRequest, context);
        } else {
            return planWithRules(userRequest, context);
        }
    }
    
    /**
     * 使用 AI 规划
     */
    private ExecutionPlan planWithAI(String userRequest, PlanningContext context) {
        log.info("[TaskPlanner] 使用 AI 模式规划任务");
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 使用 AI 规划器
            AITaskPlanner.PlanningResult result = aiTaskPlanner.plan(userRequest, 
                context != null ? context.toMap() : new HashMap<>()).join();
            
            // 转换结果为 ExecutionPlan
            ExecutionPlan plan = result.getPlan();
            plan.setPlanningTimeMs(System.currentTimeMillis() - startTime);
            
            log.info("[TaskPlanner] AI 规划完成: " + plan.getSteps().size() + " 个步骤, 耗时=" + 
                plan.getPlanningTimeMs() + "ms");
            
            return plan;
            
        } catch (Exception e) {
            log.warn("[TaskPlanner] AI 规划失败，回退到规则模式: " + e.getMessage());
            return planWithRules(userRequest, context);
        }
    }
    
    /**
     * 使用规则规划（兼容旧模式）
     */
    private ExecutionPlan planWithRules(String userRequest, PlanningContext context) {
        log.info("[TaskPlanner] 使用规则模式规划任务");
        
        long startTime = System.currentTimeMillis();
        
        // 1. 分析请求意图
        IntentAnalysis intent = analyzeIntent(userRequest);
        
        // 2. 拆解任务步骤
        List<PlanStep> steps = decomposeTask(userRequest, intent);
        
        // 3. 为每个步骤分配最佳 Agent
        assignAgents(steps, context);
        
        // 4. 分析依赖关系
        analyzeDependencies(steps);
        
        // 5. 优化执行顺序
        optimizeExecutionOrder(steps);
        
        // 6. 验证计划
        List<String> validationIssues = planValidator.validate(steps);
        
        ExecutionPlan plan = ExecutionPlan.builder()
            .planId("plan_" + System.currentTimeMillis())
            .originalRequest(userRequest)
            .intent(intent)
            .steps(steps)
            .estimatedSteps(steps.size())
            .planningTimeMs(System.currentTimeMillis() - startTime)
            .validationIssues(validationIssues)
            .status(validationIssues.isEmpty() ? ExecutionPlan.PlanStatus.VALID : ExecutionPlan.PlanStatus.NEEDS_REVIEW)
            .build();
        
        log.info("[TaskPlanner] 规则规划完成: " + steps.size() + " 个步骤");
        
        return plan;
    }
    
    /**
     * 快速规划（使用模板）
     */
    public ExecutionPlan quickPlan(String userRequest, String templateType, PlanningContext context) {
        PlanTemplate template = PlanTemplateRegistry.get(templateType);
        if (template != null) {
            return template.apply(userRequest, context);
        }
        return plan(userRequest, context);
    }
    
    /**
     * 递归规划 - 对复杂任务递归分解
     * 
     * @param userRequest 任务描述
     * @param complexityThreshold 复杂度阈值（超过此值递归分解）
     * @return 执行计划
     */
    public ExecutionPlan planRecursively(String userRequest, int complexityThreshold) {
        if (aiTaskPlanner == null) {
            log.warn("[TaskPlanner] AI 规划器未初始化，无法进行递归规划");
            return plan(userRequest, null);
        }
        
        try {
            AITaskPlanner.PlanningResult result = aiTaskPlanner.planRecursively(userRequest, complexityThreshold).join();
            return result.getPlan();
        } catch (Exception e) {
            log.warn("[TaskPlanner] 递归规划失败: " + e.getMessage());
            return plan(userRequest, null);
        }
    }
    
    /**
     * 执行计划（使用 AI 动态执行引擎）
     * 
     * @param plan 执行计划
     * @param parentAgent 父 Agent
     * @param parentSession 父 Session
     * @return 执行结果
     */
    public java.util.concurrent.CompletableFuture<DynamicExecutionEngine.ExecutionResult> execute(
            ExecutionPlan plan, Agent parentAgent, Session parentSession) {
        if (aiTaskPlanner == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("AI 执行引擎未初始化")
            );
        }
        
        return aiTaskPlanner.execute(plan, parentAgent, parentSession);
    }
    
    /**
     * 完整流程：规划并执行
     * 
     * @param userRequest 任务描述
     * @param context 上下文
     * @param parentAgent 父 Agent
     * @param parentSession 父 Session
     * @return 完整结果
     */
    public java.util.concurrent.CompletableFuture<AITaskPlanner.Result> planAndExecute(
            String userRequest, 
            PlanningContext context,
            Agent parentAgent, 
            Session parentSession) {
        if (aiTaskPlanner == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("AI 规划器未初始化")
            );
        }
        
        return aiTaskPlanner.planAndExecute(
            userRequest, 
            context != null ? context.toMap() : new HashMap<>(),
            parentAgent, 
            parentSession
        );
    }
    
    /**
     * 获取 AI 任务分析
     * 
     * @param userRequest 任务描述
     * @return 任务分析结果
     */
    public java.util.concurrent.CompletableFuture<TaskAnalysis> analyze(String userRequest) {
        if (aiTaskPlanner == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("AI 规划器未初始化")
            );
        }
        return aiTaskPlanner.analyze(userRequest);
    }
    
    // ==================== 规则模式方法（保持兼容）====================
    
    private IntentAnalysis analyzeIntent(String request) {
        IntentAnalysis.IntentType type = IntentAnalysis.IntentType.GENERAL;
        double confidence = 0.5;
        Map<String, Object> entities = new HashMap<>();
        
        String lowerRequest = request.toLowerCase();
        
        if (containsAny(lowerRequest, "修复", "fix", "bug", "错误", "调试", "debug")) {
            type = IntentAnalysis.IntentType.DEBUG;
            confidence = 0.8;
            entities.put("target", extractTarget(lowerRequest));
        } else if (containsAny(lowerRequest, "创建", "create", "新建", "new", "生成", "generate", "编写", "write")) {
            type = IntentAnalysis.IntentType.CREATE;
            confidence = 0.75;
            entities.put("itemType", extractItemType(lowerRequest));
        } else if (containsAny(lowerRequest, "重构", "refactor", "优化", "optimize", "改进", "improve")) {
            type = IntentAnalysis.IntentType.REFACTOR;
            confidence = 0.7;
        } else if (containsAny(lowerRequest, "分析", "analyze", "理解", "understand", "解释", "explain")) {
            type = IntentAnalysis.IntentType.ANALYZE;
            confidence = 0.75;
        } else if (containsAny(lowerRequest, "测试", "test", "验证", "verify")) {
            type = IntentAnalysis.IntentType.TEST;
            confidence = 0.8;
        }
        
        entities.putAll(extractFileEntities(request));
        
        return IntentAnalysis.builder()
            .type(type)
            .confidence(confidence)
            .entities(entities)
            .rawRequest(request)
            .build();
    }
    
    private List<PlanStep> decomposeTask(String request, IntentAnalysis intent) {
        List<PlanStep> steps = new ArrayList<>();
        
        switch (intent.getType()) {
            case CREATE:
                steps = decomposeCreateTask(request, intent);
                break;
            case DEBUG:
                steps = decomposeDebugTask(request, intent);
                break;
            case REFACTOR:
                steps = decomposeRefactorTask(request, intent);
                break;
            case ANALYZE:
                steps = decomposeAnalyzeTask(request, intent);
                break;
            case TEST:
                steps = decomposeTestTask(request, intent);
                break;
            default:
                steps = decomposeGeneralTask(request);
        }
        
        if (steps.isEmpty()) {
            steps = decomposeGeneralTask(request);
        }
        
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setStepNumber(i + 1);
        }
        
        return steps;
    }
    
    private List<PlanStep> decomposeCreateTask(String request, IntentAnalysis intent) {
        List<PlanStep> steps = new ArrayList<>();
        
        steps.add(PlanStep.builder()
            .action("分析需求")
            .description("理解创建目标和约束条件")
            .agentType("analyzer")
            .expectedOutput("需求规格说明")
            .build());
        
        steps.add(PlanStep.builder()
            .action("设计方案")
            .description("设计实现方案和架构")
            .agentType("architect")
            .expectedOutput("设计方案文档")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("生成代码")
            .description("根据设计生成代码实现")
            .agentType("coder")
            .expectedOutput("代码文件")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("代码审查")
            .description("检查代码质量和规范性")
            .agentType("reviewer")
            .expectedOutput("审查报告")
            .dependsOnPrevious(true)
            .build());
        
        return steps;
    }
    
    private List<PlanStep> decomposeDebugTask(String request, IntentAnalysis intent) {
        List<PlanStep> steps = new ArrayList<>();
        
        steps.add(PlanStep.builder()
            .action("复现问题")
            .description("重现和确认问题现象")
            .agentType("debug")
            .expectedOutput("问题复现步骤")
            .build());
        
        steps.add(PlanStep.builder()
            .action("定位根因")
            .description("分析代码找出问题根源")
            .agentType("debug")
            .expectedOutput("根因分析报告")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("制定修复方案")
            .description("设计修复策略")
            .agentType("debug")
            .expectedOutput("修复方案")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("实施修复")
            .description("应用修复并验证")
            .agentType("coder")
            .expectedOutput("修复后的代码")
            .dependsOnPrevious(true)
            .build());
        
        return steps;
    }
    
    private List<PlanStep> decomposeRefactorTask(String request, IntentAnalysis intent) {
        List<PlanStep> steps = new ArrayList<>();
        
        steps.add(PlanStep.builder()
            .action("分析现状")
            .description("分析当前代码结构和问题")
            .agentType("analyzer")
            .expectedOutput("现状分析报告")
            .build());
        
        steps.add(PlanStep.builder()
            .action("制定重构计划")
            .description("设计重构策略和步骤")
            .agentType("architect")
            .expectedOutput("重构计划")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("执行重构")
            .description("按步骤执行代码重构")
            .agentType("coder")
            .expectedOutput("重构后的代码")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("验证重构")
            .description("确保重构后功能正确")
            .agentType("test")
            .expectedOutput("验证结果")
            .dependsOnPrevious(true)
            .build());
        
        return steps;
    }
    
    private List<PlanStep> decomposeAnalyzeTask(String request, IntentAnalysis intent) {
        List<PlanStep> steps = new ArrayList<>();
        
        steps.add(PlanStep.builder()
            .action("收集信息")
            .description("获取相关代码和文档")
            .agentType("analyzer")
            .expectedOutput("收集的信息")
            .build());
        
        steps.add(PlanStep.builder()
            .action("深入分析")
            .description("分析代码结构和逻辑")
            .agentType("analyzer")
            .expectedOutput("分析报告")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("生成总结")
            .description("整理分析结果")
            .agentType("analyzer")
            .expectedOutput("分析总结")
            .dependsOnPrevious(true)
            .build());
        
        return steps;
    }
    
    private List<PlanStep> decomposeTestTask(String request, IntentAnalysis intent) {
        List<PlanStep> steps = new ArrayList<>();
        
        steps.add(PlanStep.builder()
            .action("分析测试需求")
            .description("理解测试范围和目标")
            .agentType("test")
            .expectedOutput("测试需求分析")
            .build());
        
        steps.add(PlanStep.builder()
            .action("设计测试用例")
            .description("设计覆盖各种场景的测试用例")
            .agentType("test")
            .expectedOutput("测试用例")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("生成测试代码")
            .description("编写测试代码")
            .agentType("coder")
            .expectedOutput("测试代码文件")
            .dependsOnPrevious(true)
            .build());
        
        steps.add(PlanStep.builder()
            .action("执行测试")
            .description("运行测试并收集结果")
            .agentType("test")
            .expectedOutput("测试报告")
            .dependsOnPrevious(true)
            .build());
        
        return steps;
    }
    
    private List<PlanStep> decomposeGeneralTask(String request) {
        List<PlanStep> steps = new ArrayList<>();
        
        Matcher matcher = STEP_PATTERN.matcher(request);
        while (matcher.find()) {
            steps.add(PlanStep.builder()
                .stepNumber(Integer.parseInt(matcher.group(1)))
                .action(matcher.group(2).trim())
                .description(matcher.group(2).trim())
                .agentType("default")
                .build());
        }
        
        if (steps.isEmpty()) {
            steps.add(PlanStep.builder()
                .stepNumber(1)
                .action("执行任务")
                .description(request)
                .agentType("default")
                .build());
        }
        
        return steps;
    }
    
    private void assignAgents(List<PlanStep> steps, PlanningContext context) {
        for (PlanStep step : steps) {
            String agentType = recommendAgent(step, context);
            step.setAgentType(agentType);
            
            Agent agent = agentRegistry.get(agentType);
            if (agent == null) {
                agent = agentRegistry.getCurrent();
            }
            step.setAssignedAgent(agent);
        }
    }
    
    private String recommendAgent(PlanStep step, PlanningContext context) {
        String action = step.getAction().toLowerCase();
        
        if (containsAny(action, "分析", "analyze", "理解", "understand")) {
            return "analyzer";
        } else if (containsAny(action, "编写", "write", "生成", "generate", "创建", "create")) {
            return "coder";
        } else if (containsAny(action, "调试", "debug", "修复", "fix")) {
            return "debug";
        } else if (containsAny(action, "测试", "test", "验证", "verify")) {
            return "test";
        } else if (containsAny(action, "审查", "review", "检查", "check")) {
            return "reviewer";
        } else if (containsAny(action, "设计", "design", "架构", "architect")) {
            return "architect";
        }
        
        return context != null && context.getPreferredAgent() != null ? 
            context.getPreferredAgent() : "default";
    }
    
    private void analyzeDependencies(List<PlanStep> steps) {
        Map<String, PlanStep> stepMap = steps.stream()
            .collect(Collectors.toMap(s -> String.valueOf(s.getStepNumber()), s -> s));
        
        for (PlanStep step : steps) {
            if (step.isDependsOnPrevious() && step.getStepNumber() > 1) {
                String prevId = String.valueOf(step.getStepNumber() - 1);
                step.getDependencies().add(prevId);
            }
            
            String description = step.getDescription().toLowerCase();
            for (PlanStep other : steps) {
                if (other.getStepNumber() >= step.getStepNumber()) continue;
                
                String otherOutput = other.getExpectedOutput() != null ? 
                    other.getExpectedOutput().toLowerCase() : "";
                if (!otherOutput.isEmpty() && description.contains(otherOutput)) {
                    step.getDependencies().add(String.valueOf(other.getStepNumber()));
                }
            }
        }
    }
    
    private void optimizeExecutionOrder(List<PlanStep> steps) {
        steps.sort(Comparator.comparingInt(PlanStep::getStepNumber));
    }
    
    // ==================== 辅助方法 ====================
    
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private String extractTarget(String text) {
        return "unknown";
    }
    
    private String extractItemType(String text) {
        if (text.contains("类") || text.contains("class")) return "class";
        if (text.contains("方法") || text.contains("method") || text.contains("函数")) return "method";
        if (text.contains("接口") || text.contains("interface")) return "interface";
        if (text.contains("测试") || text.contains("test")) return "test";
        return "code";
    }
    
    private Map<String, Object> extractFileEntities(String request) {
        Map<String, Object> entities = new HashMap<>();
        
        Pattern filePattern = Pattern.compile("([\\w/\\\\]+\\.(java|py|js|ts|go|rs|cpp|c|h|xml|json|yaml|yml))");
        Matcher matcher = filePattern.matcher(request);
        List<String> files = new ArrayList<>();
        while (matcher.find()) {
            files.add(matcher.group(1));
        }
        
        if (!files.isEmpty()) {
            entities.put("files", files);
        }
        
        return entities;
    }
    
    /**
     * 获取 AI 规划器
     */
    public AITaskPlanner getAiTaskPlanner() {
        return aiTaskPlanner;
    }
}
