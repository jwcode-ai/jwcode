package com.jwcode.core.agent;

import com.jwcode.core.model.StructuredTask;
import com.jwcode.core.model.StructuredTask.ExecutionMode;
import com.jwcode.core.model.StructuredTask.TaskPhase;

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

    /**
     * 解析 AI 的 plan 回复文本，生成结构化任务列表。
     *
     * @param aiPlanResponse AI 回复的 plan 文本
     * @param taskIdPrefix   任务ID前缀
     * @return 结构化的任务列表（顶级为阶段组，子级为具体任务）
     */
    public List<StructuredTask> parsePlan(String aiPlanResponse, String taskIdPrefix) {
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
}
