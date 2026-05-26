package com.jwcode.core.agent;

import com.jwcode.core.model.StructuredTask;
import com.jwcode.core.model.StructuredTask.ExecutionMode;
import com.jwcode.core.model.StructuredTask.TaskPhase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TaskAgent — 任务结构化Agent。
 *
 * <p>职责：将 AI 的 plan/act 回复解析为结构化的任务列表。
 * 自动识别任务的执行模式（并发/串行）、阶段分组、依赖关系。</p>
 *
 * <p>工作流程：
 * <ol>
 *   <li>接收 AI 的 plan 文本回复</li>
 *   <li>解析出任务步骤（支持 Markdown 列表、编号列表、Phase 标记）</li>
 *   <li>分析依赖关系（关键词：depends on、requires、after、before、concurrent with）</li>
 *   <li>识别并发组（关键词：parallel、concurrent、simultaneously、in parallel）</li>
 *   <li>识别阶段（Phase 1/2/3、阶段一/二/三、Exploration/Design/Implementation 等）</li>
 *   <li>生成 StructuredTask 树</li>
 * </ol>
 * </p>
 */
public class TaskAgent {

    // 阶段关键词映射
    private static final Map<String, TaskPhase> PHASE_KEYWORDS = new LinkedHashMap<>();
    static {
        PHASE_KEYWORDS.put("explor|调研|探索|分析代码|codebase", TaskPhase.EXPLORATION);
        PHASE_KEYWORDS.put("design|设计|架构|architect", TaskPhase.DESIGN);
        PHASE_KEYWORDS.put("implement|实现|编写代码|开发|coding|code", TaskPhase.IMPLEMENTATION);
        PHASE_KEYWORDS.put("test|测试|验证|verify", TaskPhase.TESTING);
        PHASE_KEYWORDS.put("review|审查|检查|check", TaskPhase.REVIEW);
        PHASE_KEYWORDS.put("doc|文档|document|readme", TaskPhase.DOCUMENTATION);
    }

    // 并发关键词
    private static final Pattern CONCURRENT_PATTERN = Pattern.compile(
        "(?:parallel|concurrent|simultaneously|in\\s*parallel|同时|并行|并发)",
        Pattern.CASE_INSENSITIVE
    );

    // 串行关键词
    private static final Pattern SEQUENTIAL_PATTERN = Pattern.compile(
        "(?:sequential|sequentially|one\\s*by\\s*one|step\\s*by\\s*step|in\\s*order|依次|串行|顺序)",
        Pattern.CASE_INSENSITIVE
    );

    // 依赖关键词
    private static final Pattern DEPENDS_ON_PATTERN = Pattern.compile(
        "(?:depends\\s*on|requires|依赖|需要|after\\s+step|before\\s+step|prerequisite|前提)",
        Pattern.CASE_INSENSITIVE
    );

    // Phase 标题匹配
    private static final Pattern PHASE_HEADER_PATTERN = Pattern.compile(
        "(?:Phase\\s*(\\d+)|阶段\\s*(\\w+))[：:、\\s]*(.*)",
        Pattern.CASE_INSENSITIVE
    );

    // 步骤匹配（Markdown 列表项）
    private static final Pattern STEP_PATTERN = Pattern.compile(
        "^\\s*(?:[-*+]|\\d+[.)])\\s*(?:\\*\\*)?(.*?)(?:\\*\\*)?\\s*$",
        Pattern.MULTILINE
    );

    // Agent 分配匹配
    private static final Pattern AGENT_PATTERN = Pattern.compile(
        "(?:assigned\\s*to|agent|使用|by)\\s*[:：]?\\s*(\\w+)\\s*(?:agent|Agent)?",
        Pattern.CASE_INSENSITIVE
    );

    // 文件路径匹配（用于上下文提取）
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "(?:[\\w./-]+/)?[\\w.-]+\\.(java|tsx?|jsx?|py|xml|json|yml|yaml|md|css|html)",
        Pattern.CASE_INSENSITIVE
    );

    // 模块/包引用匹配
    private static final Pattern MODULE_REF_PATTERN = Pattern.compile(
        "(?:module|模块|package|包)\\s*[:：]?\\s*([\\w.-]+)",
        Pattern.CASE_INSENSITIVE
    );

    // 约束条件匹配
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
        "(?:约束|限制|constraint|must|必须|should|应该|注意|note|⚠️)\\s*[:：]?\\s*(.+?)(?:[.。\\n]|$)",
        Pattern.CASE_INSENSITIVE
    );

    // 上下文注入标记匹配（AI plan 中用 @context 标记的额外上下文）
    private static final Pattern CONTEXT_BLOCK_PATTERN = Pattern.compile(
        "@context\\s*[:：]\\s*(\\w+)\\s*=\\s*\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    );

    /** JSON 解析器 */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** JSON 代码块匹配 — 匹配 ```json ... ``` */
    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile(
        "```json\\s*\\n?([\\s\\S]*?)\\n?```",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 解析 AI 的 plan 回复文本，生成结构化任务列表。
     *
     * <p>解析优先级：</p>
     * <ol>
     *   <li><b>JSON 代码块</b>（```json ... ```）— 最高优先级，精确解析</li>
     *   <li><b>Phase 拆分 + 正则</b> — 无 JSON 时回退到传统正则解析</li>
     * </ol>
     *
     * @param aiPlanResponse AI 回复的 plan 文本
     * @param taskIdPrefix   任务ID前缀
     * @return 结构化的任务列表（顶级为阶段组，子级为具体任务）
     */
    public List<StructuredTask> parsePlan(String aiPlanResponse, String taskIdPrefix) {
        // Step 0: 优先尝试 JSON 代码块解析
        List<StructuredTask> jsonTasks = parseJsonTaskList(aiPlanResponse, taskIdPrefix);
        if (jsonTasks != null && !jsonTasks.isEmpty()) {
            return jsonTasks;
        }

        List<StructuredTask> phaseTasks = new ArrayList<>();

        // Step 1: 按 Phase 拆分
        List<PhaseBlock> phases = splitIntoPhases(aiPlanResponse);

        if (phases.isEmpty()) {
            // 没有明确的 Phase 标记，尝试按行解析
            phases.add(new PhaseBlock(TaskPhase.GENERAL, "General", aiPlanResponse, ExecutionMode.SEQUENTIAL));
        }

        int globalStep = 0;
        int taskCounter = 1;

        for (PhaseBlock phase : phases) {
            List<StructuredTask> stepTasks = new ArrayList<>();

            // Step 2: 从 Phase 文本中提取任务步骤
            List<String> steps = extractSteps(phase.content);

            // Step 3: 检测执行模式
            ExecutionMode mode = detectExecutionMode(phase.content);
            if (mode == null) {
                mode = phase.defaultMode;
            }

            // Step 4: 为每个步骤创建 StructuredTask
            String parallelGroup = mode == ExecutionMode.CONCURRENT
                ? "parallel-group-" + taskIdPrefix + "-" + phases.indexOf(phase)
                : null;

            for (int i = 0; i < steps.size(); i++) {
                globalStep++;
                String stepText = steps.get(i);
                String taskId = taskIdPrefix + "-" + taskCounter++;

                StructuredTask task = StructuredTask.builder()
                    .id(taskId)
                    .title(extractTitle(stepText))
                    .description(stepText)
                    .status("pending")
                    .agentType(detectAgentType(stepText, phase.phase))
                    .stepNumber(globalStep)
                    .phase(phase.phase)
                    .executionMode(mode)
                    .parallelGroup(parallelGroup)
                    .context(extractTaskContext(stepText, aiPlanResponse, phase.phase))
                    .build();

                // 分析依赖关系
                analyzeDependencies(task, stepText, stepTasks, taskIdPrefix);

                stepTasks.add(task);
            }

            if (!stepTasks.isEmpty()) {
                // 创建阶段包装任务
                String phaseId = taskIdPrefix + "-phase-" + taskCounter++;
                StructuredTask phaseWrapper = StructuredTask.builder()
                    .id(phaseId)
                    .title(phase.title)
                    .description(phase.content.length() > 200
                        ? phase.content.substring(0, 200) + "..."
                        : phase.content)
                    .status("pending")
                    .agentType("orchestrator")
                    .phase(phase.phase)
                    .executionMode(mode)
                    .children(stepTasks)
                    .build();

                phaseTasks.add(phaseWrapper);
            }
        }

        return phaseTasks;
    }

    /**
     * 快速解析模式 — 直接从简单的任务列表文本生成 StructuredTask。
     * 用于 AI 返回的简洁任务清单（如 "1. 分析代码\n2. 设计接口\n3. 实现功能"）。
     */
    public List<StructuredTask> parseQuickPlan(String taskListText, String taskIdPrefix) {
        List<StructuredTask> tasks = new ArrayList<>();
        List<String> lines = Arrays.asList(taskListText.split("\n"));
        int taskCounter = 1;

        // 检测整体执行模式
        ExecutionMode overallMode = detectExecutionMode(taskListText);
        if (overallMode == null) overallMode = ExecutionMode.SEQUENTIAL;

        String parallelGroup = overallMode == ExecutionMode.CONCURRENT
            ? "quick-parallel-" + taskIdPrefix
            : null;

        List<String> stepLines = lines.stream()
            .filter(l -> l.matches("^\\s*(?:[-*+]|\\d+[.)])\\s*.*"))
            .collect(Collectors.toList());

        for (String line : stepLines) {
            String taskId = taskIdPrefix + "-quick-" + taskCounter++;
            String cleanText = line.replaceFirst("^\\s*(?:[-*+]|\\d+[.)])\\s*", "").trim();

            if (cleanText.isEmpty()) continue;

            StructuredTask task = StructuredTask.builder()
                .id(taskId)
                .title(extractTitle(cleanText))
                .description(cleanText)
                .status("pending")
                .agentType(detectAgentType(cleanText, TaskPhase.GENERAL))
                .stepNumber(tasks.size() + 1)
                .executionMode(overallMode)
                .parallelGroup(parallelGroup)
                .context(extractTaskContext(cleanText, taskListText, TaskPhase.GENERAL))
                .build();

            // 分析依赖
            analyzeDependencies(task, cleanText, tasks, taskIdPrefix);
            tasks.add(task);
        }

        return tasks;
    }

    // ==================== 内部方法 ====================

    /**
     * Phase 文本块
     */
    private static class PhaseBlock {
        final TaskPhase phase;
        final String title;
        final String content;
        final ExecutionMode defaultMode;

        PhaseBlock(TaskPhase phase, String title, String content, ExecutionMode defaultMode) {
            this.phase = phase;
            this.title = title;
            this.content = content;
            this.defaultMode = defaultMode;
        }
    }

    /**
     * 将文本按 Phase 拆分为多个块
     */
    private List<PhaseBlock> splitIntoPhases(String text) {
        List<PhaseBlock> blocks = new ArrayList<>();

        // 尝试按 "Phase N" 或 "阶段 N" 拆分
        String[] lines = text.split("\n");
        StringBuilder currentTitle = new StringBuilder();
        StringBuilder currentContent = new StringBuilder();
        TaskPhase currentPhase = null;

        for (String line : lines) {
            Matcher phaseMatcher = PHASE_HEADER_PATTERN.matcher(line.trim());

            if (phaseMatcher.matches()) {
                // 保存前一个 Phase
                if (currentPhase != null && currentContent.length() > 0) {
                    blocks.add(new PhaseBlock(currentPhase,
                        currentTitle.toString(),
                        currentContent.toString(),
                        ExecutionMode.SEQUENTIAL));
                }

                String phaseLabel = phaseMatcher.group(3) != null
                    ? phaseMatcher.group(3)
                    : "Phase " + (phaseMatcher.group(1) != null
                        ? phaseMatcher.group(1)
                        : phaseMatcher.group(2));

                currentTitle = new StringBuilder(phaseLabel);
                currentContent = new StringBuilder();
                currentPhase = detectPhaseFromText(line + " " + phaseLabel);
            } else if (currentPhase != null) {
                // 检查是否是新的阶段标记（如 "### Exploration" 等）
                TaskPhase detectedPhase = detectPhaseFromText(line);
                if (detectedPhase != null && detectedPhase != currentPhase) {
                    // 保存前一个 Phase
                    if (currentContent.length() > 0) {
                        blocks.add(new PhaseBlock(currentPhase,
                            currentTitle.toString(),
                            currentContent.toString(),
                            ExecutionMode.SEQUENTIAL));
                    }
                    currentPhase = detectedPhase;
                    currentTitle = new StringBuilder(line.trim());
                    currentContent = new StringBuilder();
                } else {
                    currentContent.append(line).append("\n");
                }
            } else {
                // 检查是否是阶段起始行
                TaskPhase detectedPhase = detectPhaseFromText(line);
                if (detectedPhase != null) {
                    currentPhase = detectedPhase;
                    currentTitle = new StringBuilder(line.trim());
                    currentContent = new StringBuilder();
                } else {
                    // 累积到前一个 Phase 或作为未分类内容
                    if (!blocks.isEmpty() && currentPhase == null) {
                        PhaseBlock last = blocks.get(blocks.size() - 1);
                        blocks.set(blocks.size() - 1, new PhaseBlock(
                            last.phase, last.title, last.content + line + "\n", last.defaultMode));
                    }
                }
            }
        }

        // 保存最后一个 Phase
        if (currentPhase != null && currentContent.length() > 0) {
            blocks.add(new PhaseBlock(currentPhase,
                currentTitle.toString(),
                currentContent.toString(),
                ExecutionMode.SEQUENTIAL));
        }

        return blocks;
    }

    /**
     * 从文本中提取任务步骤
     */
    private List<String> extractSteps(String text) {
        List<String> steps = new ArrayList<>();
        String[] lines = text.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 匹配 Markdown 列表项
            if (trimmed.matches("^\\s*(?:[-*+]|\\d+[.)])\\s*.*")) {
                String stepText = trimmed.replaceFirst("^\\s*(?:[-*+]|\\d+[.)])\\s*", "").trim();
                if (!stepText.isEmpty()) {
                    steps.add(stepText);
                }
            }
        }

        return steps;
    }

    /**
     * 检测执行模式
     */
    private ExecutionMode detectExecutionMode(String text) {
        // 检查并发关键词
        if (CONCURRENT_PATTERN.matcher(text).find()) {
            return ExecutionMode.CONCURRENT;
        }
        // 检查串行关键词
        if (SEQUENTIAL_PATTERN.matcher(text).find()) {
            return ExecutionMode.SEQUENTIAL;
        }
        return null; // 未明确指定
    }

    /**
     * 从文本检测阶段类型
     */
    private TaskPhase detectPhaseFromText(String text) {
        String lower = text.toLowerCase();
        for (Map.Entry<String, TaskPhase> entry : PHASE_KEYWORDS.entrySet()) {
            Pattern p = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
            if (p.matcher(lower).find()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 提取任务标题（截取第一句或前80个字符）
     */
    private String extractTitle(String text) {
        if (text == null || text.isEmpty()) return "Unnamed Task";

        // 去掉 Markdown 格式
        String clean = text.replaceAll("\\*\\*|__|`", "").trim();

        // 取第一句
        int periodIdx = clean.indexOf('.');
        int colonIdx = clean.indexOf('：');
        int cnColonIdx = clean.indexOf('：');
        int semiIdx = clean.indexOf('；');
        int firstSep = -1;

        if (periodIdx > 0) firstSep = periodIdx;
        if (colonIdx > 0 && (firstSep < 0 || colonIdx < firstSep)) firstSep = colonIdx;
        if (cnColonIdx > 0 && (firstSep < 0 || cnColonIdx < firstSep)) firstSep = cnColonIdx;
        if (semiIdx > 0 && (firstSep < 0 || semiIdx < firstSep)) firstSep = semiIdx;

        if (firstSep > 0) {
            return clean.substring(0, firstSep).trim();
        }

        // 如果太长，截断
        if (clean.length() > 80) {
            return clean.substring(0, 77) + "...";
        }

        return clean;
    }

    /**
     * 检测应分配的 Agent 类型
     */
    private String detectAgentType(String text, TaskPhase phase) {
        String lower = text.toLowerCase();

        // 先检查显式 Agent 分配
        Matcher agentMatcher = AGENT_PATTERN.matcher(text);
        if (agentMatcher.find()) {
            String agentName = agentMatcher.group(1).toLowerCase();
            if (isValidAgentType(agentName)) {
                return agentName;
            }
        }

        // 根据阶段推断
        switch (phase) {
            case EXPLORATION: return "explore";
            case DESIGN: return "architect";
            case IMPLEMENTATION: return "coder";
            case TESTING: return "test";
            case REVIEW: return "reviewer";
            case DOCUMENTATION: return "doc";
            default: break;
        }

        // 根据关键词推断
        if (lower.contains("test") || lower.contains("测试") || lower.contains("验证")) return "test";
        if (lower.contains("debug") || lower.contains("调试") || lower.contains("修复") || lower.contains("fix")) return "debug";
        if (lower.contains("review") || lower.contains("审查") || lower.contains("检查")) return "reviewer";
        if (lower.contains("doc") || lower.contains("文档") || lower.contains("readme")) return "doc";
        if (lower.contains("design") || lower.contains("设计") || lower.contains("架构")) return "architect";
        if (lower.contains("explor") || lower.contains("调研") || lower.contains("分析")) return "explore";
        if (lower.contains("code") || lower.contains("实现") || lower.contains("编写") || lower.contains("开发")) return "coder";

        return "default";
    }

    /**
     * 检查是否是有效的 Agent 类型
     */
    private boolean isValidAgentType(String name) {
        return Set.of("coder", "debug", "reviewer", "test", "doc", "explore",
            "architect", "default", "orchestrator").contains(name);
    }

    /**
     * 分析任务依赖关系
     */
    private void analyzeDependencies(StructuredTask task, String text,
                                      List<StructuredTask> previousTasks,
                                      String taskIdPrefix) {
        String lower = text.toLowerCase();

        // 检查依赖关键词
        if (DEPENDS_ON_PATTERN.matcher(lower).find()) {
            // 查找引用的步骤编号
            Pattern stepRef = Pattern.compile("(?:step|步骤|task|任务)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher stepMatcher = stepRef.matcher(lower);

            while (stepMatcher.find()) {
                int refStep = Integer.parseInt(stepMatcher.group(1));
                // 在 previousTasks 中查找匹配的任务
                for (StructuredTask prev : previousTasks) {
                    if (prev.getStepNumber() == refStep) {
                        if (!task.getDependencies().contains(prev.getId())) {
                            task.getDependencies().add(prev.getId());
                        }
                    }
                }
            }
        }

        // 如果显式标记了 "after step N" 或 "需要先完成步骤N"
        Pattern afterPattern = Pattern.compile(
            "(?:after|需要先完成|完成后|following)\\s*(?:step|步骤|task|任务)?\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);
        Matcher afterMatcher = afterPattern.matcher(lower);
        while (afterMatcher.find()) {
            int refStep = Integer.parseInt(afterMatcher.group(1));
            for (StructuredTask prev : previousTasks) {
                if (prev.getStepNumber() == refStep) {
                    if (!task.getDependencies().contains(prev.getId())) {
                        task.getDependencies().add(prev.getId());
                    }
                }
            }
        }
    }

    /**
     * 从任务步骤文本和完整 plan 中提取任务上下文。
     *
     * <p>上下文包含：相关文件路径、模块信息、约束条件、上游依赖输出等。
     * 这些上下文将在 TaskExecutionAgent 执行时注入到子Agent的 A2A 任务中。</p>
     *
     * @param stepText     当前步骤文本
     * @param fullPlanText 完整 plan 文本（用于提取跨步骤上下文）
     * @param phase        任务所属阶段
     * @return 任务上下文 Map
     */
    private Map<String, String> extractTaskContext(String stepText, String fullPlanText, StructuredTask.TaskPhase phase) {
        Map<String, String> context = new HashMap<>();

        // 1. 提取文件路径
        List<String> files = new ArrayList<>();
        Matcher fileMatcher = FILE_PATH_PATTERN.matcher(stepText);
        while (fileMatcher.find()) {
            files.add(fileMatcher.group());
        }
        if (!files.isEmpty()) {
            context.put("relatedFiles", String.join(", ", files));
        }

        // 2. 提取模块/包引用
        Matcher moduleMatcher = MODULE_REF_PATTERN.matcher(stepText);
        if (moduleMatcher.find()) {
            context.put("targetModule", moduleMatcher.group(1));
        }

        // 3. 提取约束条件
        List<String> constraints = new ArrayList<>();
        Matcher constraintMatcher = CONSTRAINT_PATTERN.matcher(fullPlanText);
        while (constraintMatcher.find()) {
            String constraint = constraintMatcher.group(1).trim();
            if (!constraint.isEmpty() && constraint.length() < 200) {
                constraints.add(constraint);
            }
        }
        if (!constraints.isEmpty()) {
            context.put("constraints", String.join("; ", constraints));
        }

        // 4. 提取显式上下文注入标记 @context key="value"
        Matcher contextBlockMatcher = CONTEXT_BLOCK_PATTERN.matcher(stepText);
        while (contextBlockMatcher.find()) {
            context.put(contextBlockMatcher.group(1), contextBlockMatcher.group(2));
        }

        // 5. 阶段信息作为上下文
        context.put("phase", phase.name());
        context.put("phaseLabel", getPhaseLabel(phase));

        // 6. 从步骤文本中提取关键名词作为搜索关键词
        String cleanText = stepText.replaceAll("[*_`~#\\[\\]()]", " ").trim();
        context.put("searchKeywords", cleanText.length() > 100 ? cleanText.substring(0, 100) : cleanText);

        return context;
    }

    /**
     * 获取阶段的中文标签
     */
    private String getPhaseLabel(StructuredTask.TaskPhase phase) {
        switch (phase) {
            case EXPLORATION: return "调研探索";
            case DESIGN: return "架构设计";
            case IMPLEMENTATION: return "代码实现";
            case TESTING: return "测试验证";
            case REVIEW: return "代码审查";
            case DOCUMENTATION: return "文档编写";
            default: return "通用任务";
        }
    }

    // ==================== JSON 代码块解析（Phase 2 优化） ====================

    /**
     * 从 AI plan 文本中提取并解析 JSON 代码块。
     *
     * <p>AI 在 Plan 模式下可以输出如下格式的 JSON 代码块：</p>
     * <pre>
     * ```json
     * {
     *   "tasks": [
     *     {"id": "t1", "title": "...", "agentType": "explorer", "phase": "exploration", ...}
     *   ]
     * }
     * ```
     * </pre>
     *
     * @param aiPlanResponse AI 回复的 plan 文本
     * @param taskIdPrefix   任务ID前缀
     * @return 解析成功的结构化任务列表，无 JSON 块或解析失败时返回 null
     */
    public List<StructuredTask> parseJsonTaskList(String aiPlanResponse, String taskIdPrefix) {
        if (aiPlanResponse == null || aiPlanResponse.isEmpty()) {
            return null;
        }

        // 查找 JSON 代码块
        Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(aiPlanResponse);
        if (!matcher.find()) {
            return null; // 没有 JSON 代码块
        }

        String jsonContent = matcher.group(1).trim();
        if (jsonContent.isEmpty()) {
            return null;
        }

        try {
            // 解析 JSON
            JsonNode root = JSON_MAPPER.readTree(jsonContent);

            // 支持两种顶层结构：
            // 1. {"tasks": [...]}
            // 2. [...] 直接是数组
            ArrayNode tasksArray;
            if (root.has("tasks") && root.get("tasks").isArray()) {
                tasksArray = (ArrayNode) root.get("tasks");
            } else if (root.isArray()) {
                tasksArray = (ArrayNode) root;
            } else {
                return null;
            }

            if (tasksArray.size() == 0) {
                return null;
            }

            // 解析每个任务
            List<StructuredTask> tasks = new ArrayList<>();
            int stepNumber = 1;

            for (JsonNode taskNode : tasksArray) {
                StructuredTask task = parseJsonTask(taskNode, taskIdPrefix, stepNumber);
                if (task != null) {
                    tasks.add(task);
                    stepNumber++;
                }
            }

            if (tasks.isEmpty()) {
                return null;
            }

            // 处理依赖关系（将依赖 ID 从原始 ID 映射为实际 ID）
            resolveJsonDependencies(tasks);

            // 按阶段分组，创建阶段包装任务
            return groupTasksByPhase(tasks, taskIdPrefix);

        } catch (Exception e) {
            // JSON 解析失败，返回 null 让调用方回退到正则解析
            return null;
        }
    }

    /**
     * 从 JSON 节点解析单个 StructuredTask
     */
    private StructuredTask parseJsonTask(JsonNode node, String taskIdPrefix, int stepNumber) {
        try {
            String title = node.has("title") ? node.get("title").asText() : "Unnamed Task";
            if (title.isEmpty()) title = "Unnamed Task";

            String id = node.has("id") ? node.get("id").asText() : (taskIdPrefix + "-json-" + stepNumber);
            // 确保 ID 唯一
            if (!id.startsWith(taskIdPrefix)) {
                id = taskIdPrefix + "-" + id;
            }

            String agentType = node.has("agentType") ? node.get("agentType").asText("default") : "default";
            // 标准化 agentType
            agentType = normalizeAgentType(agentType);

            String description = node.has("description") ? node.get("description").asText("") : "";
            if (description.isEmpty()) description = title;

            // 解析阶段
            TaskPhase phase = TaskPhase.GENERAL;
            if (node.has("phase")) {
                phase = parseJsonPhase(node.get("phase").asText());
            }

            // 解析执行模式
            ExecutionMode mode = ExecutionMode.SEQUENTIAL;
            if (node.has("executionMode")) {
                String modeStr = node.get("executionMode").asText();
                if ("concurrent".equalsIgnoreCase(modeStr) || "parallel".equalsIgnoreCase(modeStr)) {
                    mode = ExecutionMode.CONCURRENT;
                }
            }

            // 构建任务
            StructuredTask.Builder builder = StructuredTask.builder()
                .id(id)
                .title(title)
                .description(description)
                .status("pending")
                .agentType(agentType)
                .stepNumber(stepNumber)
                .phase(phase)
                .executionMode(mode);

            // 解析依赖
            if (node.has("dependencies") && node.get("dependencies").isArray()) {
                ArrayNode deps = (ArrayNode) node.get("dependencies");
                List<String> dependencyIds = new ArrayList<>();
                for (JsonNode dep : deps) {
                    String depId = dep.asText();
                    if (!depId.startsWith(taskIdPrefix)) {
                        depId = taskIdPrefix + "-" + depId;
                    }
                    dependencyIds.add(depId);
                }
                builder.dependencies(dependencyIds);
            }

            // 解析上下文
            if (node.has("context") && node.get("context").isObject()) {
                Map<String, String> context = new HashMap<>();
                node.get("context").fields().forEachRemaining(entry -> {
                    context.put(entry.getKey(), entry.getValue().asText());
                });
                builder.context(context);
            }

            return builder.build();

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 标准化 Agent 类型名称
     */
    private String normalizeAgentType(String agentType) {
        switch (agentType.toLowerCase()) {
            case "explorer":
            case "explore":
            case "exploration":
                return "explore";
            case "architect":
            case "architecture":
            case "design":
                return "architect";
            case "coder":
            case "developer":
            case "code":
            case "implementation":
                return "coder";
            case "tester":
            case "test":
            case "testing":
                return "test";
            case "reviewer":
            case "review":
                return "reviewer";
            case "documenter":
            case "doc":
            case "documentation":
            case "docs":
                return "doc";
            case "debug":
            case "debugger":
                return "debug";
            default:
                return "default";
        }
    }

    /**
     * 解析 JSON 中的阶段名称
     */
    private TaskPhase parseJsonPhase(String phaseStr) {
        if (phaseStr == null) return TaskPhase.GENERAL;
        switch (phaseStr.toLowerCase()) {
            case "exploration":
            case "explore":
            case "调研":
            case "探索":
                return TaskPhase.EXPLORATION;
            case "design":
            case "架构":
            case "设计":
                return TaskPhase.DESIGN;
            case "implementation":
            case "implement":
            case "coding":
            case "实现":
            case "开发":
                return TaskPhase.IMPLEMENTATION;
            case "testing":
            case "test":
            case "测试":
            case "验证":
                return TaskPhase.TESTING;
            case "review":
            case "审查":
            case "检查":
                return TaskPhase.REVIEW;
            case "documentation":
            case "doc":
            case "文档":
                return TaskPhase.DOCUMENTATION;
            default:
                return TaskPhase.GENERAL;
        }
    }

    /**
     * 解析 JSON 依赖关系 — 将原始 ID 映射为实际 ID（处理 taskIdPrefix）
     */
    private void resolveJsonDependencies(List<StructuredTask> tasks) {
        // 构建 ID 映射：原始ID → 实际ID
        Map<String, String> idMap = new HashMap<>();
        for (StructuredTask task : tasks) {
            // 从实际 ID 中提取原始 ID（去掉 taskIdPrefix- 前缀）
            String rawId = task.getId();
            int prefixIdx = rawId.lastIndexOf('-');
            if (prefixIdx > 0 && prefixIdx < rawId.length() - 1) {
                String possibleRawId = rawId.substring(prefixIdx + 1);
                idMap.put(possibleRawId, rawId);
            }
            idMap.put(rawId, rawId);
        }

        // 重新映射依赖
        for (StructuredTask task : tasks) {
            List<String> resolvedDeps = new ArrayList<>();
            for (String dep : task.getDependencies()) {
                String resolved = idMap.getOrDefault(dep, dep);
                if (!resolvedDeps.contains(resolved)) {
                    resolvedDeps.add(resolved);
                }
            }
            task.getDependencies().clear();
            task.getDependencies().addAll(resolvedDeps);
        }
    }

    /**
     * 将任务按阶段分组，创建阶段包装任务。
     * 如果所有任务都是同一个阶段，则不包装。
     */
    private List<StructuredTask> groupTasksByPhase(List<StructuredTask> tasks, String taskIdPrefix) {
        // 按阶段分组
        Map<TaskPhase, List<StructuredTask>> phaseGroups = tasks.stream()
            .collect(Collectors.groupingBy(StructuredTask::getPhase, LinkedHashMap::new, Collectors.toList()));

        // 如果只有一个阶段且任务数 <= 1，直接返回
        if (phaseGroups.size() <= 1 && tasks.size() <= 3) {
            return tasks;
        }

        // 为每个阶段创建包装任务
        List<StructuredTask> result = new ArrayList<>();
        int phaseCounter = 1;

        for (Map.Entry<TaskPhase, List<StructuredTask>> entry : phaseGroups.entrySet()) {
            TaskPhase phase = entry.getKey();
            List<StructuredTask> phaseTasks = entry.getValue();

            if (phaseTasks.size() == 1) {
                // 只有一个任务，不需要包装
                result.add(phaseTasks.get(0));
            } else {
                // 多个任务，创建阶段包装
                String phaseId = taskIdPrefix + "-phase-" + phaseCounter++;
                String phaseTitle = getPhaseLabel(phase);

                StructuredTask phaseWrapper = StructuredTask.builder()
                    .id(phaseId)
                    .title(phaseTitle)
                    .description(phaseTitle + "阶段 — 共 " + phaseTasks.size() + " 个任务")
                    .status("pending")
                    .agentType("orchestrator")
                    .phase(phase)
                    .executionMode(ExecutionMode.SEQUENTIAL)
                    .children(new ArrayList<>(phaseTasks))
                    .build();

                result.add(phaseWrapper);
            }
        }

        return result;
    }
}
