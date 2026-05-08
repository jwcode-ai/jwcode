package com.jwcode.core.planner;

import java.util.*;
import java.util.regex.Pattern;

/**
 * IntentAnalyzer — 用户意图识别器。
 *
 * <p>分析用户输入，判断任务类型、复杂度、涉及模块等。
 * 作为 Orchestrator 的第一步工作。</p>
 */
public class IntentAnalyzer {

    // ==================== 任务类型枚举 ====================

    public enum TaskType {
        FEATURE("feature", "新功能开发"),
        BUGFIX("bugfix", "Bug修复"),
        REFACTOR("refactor", "代码重构"),
        TEST("test", "测试"),
        DOC("doc", "文档"),
        ANALYZE("analyze", "分析"),
        DEBUG("debug", "调试"),
        REVIEW("review", "代码审查"),
        CHAT("chat", "闲聊"),
        GENERAL("general", "通用任务");

        private final String keyword;
        private final String displayName;

        TaskType(String keyword, String displayName) {
            this.keyword = keyword;
            this.displayName = displayName;
        }

        public String getKeyword() { return keyword; }
        public String getDisplayName() { return displayName; }
    }

    // ==================== 复杂度枚举 ====================

    public enum Complexity {
        SIMPLE("simple", "简单（1-2步）"),
        MEDIUM("medium", "中等（3-5步）"),
        COMPLEX("complex", "复杂（>5步）");

        private final String keyword;
        private final String displayName;

        Complexity(String keyword, String displayName) {
            this.keyword = keyword;
            this.displayName = displayName;
        }

        public String getKeyword() { return keyword; }
        public String getDisplayName() { return displayName; }
    }

    // ==================== 分析结果 ====================

    public static class AnalysisResult {
        private final TaskType taskType;
        private final Complexity complexity;
        private final List<String> modulesInvolved;
        private final String techStack;
        private final String summary;
        private final boolean isInterruption;
        private final String originalTaskId;

        public AnalysisResult(TaskType taskType, Complexity complexity,
                             List<String> modulesInvolved, String techStack,
                             String summary, boolean isInterruption,
                             String originalTaskId) {
            this.taskType = taskType;
            this.complexity = complexity;
            this.modulesInvolved = modulesInvolved;
            this.techStack = techStack;
            this.summary = summary;
            this.isInterruption = isInterruption;
            this.originalTaskId = originalTaskId;
        }

        public TaskType getTaskType() { return taskType; }
        public Complexity getComplexity() { return complexity; }
        public List<String> getModulesInvolved() { return modulesInvolved; }
        public String getTechStack() { return techStack; }
        public String getSummary() { return summary; }
        public boolean isInterruption() { return isInterruption; }
        public String getOriginalTaskId() { return originalTaskId; }

        @Override
        public String toString() {
            return String.format(
                "AnalysisResult{type=%s, complexity=%s, modules=%s, techStack='%s', interruption=%s}",
                taskType, complexity, modulesInvolved, techStack, isInterruption
            );
        }
    }

    // ==================== 关键词模式 ====================

    private static final Map<TaskType, List<Pattern>> TYPE_PATTERNS = new LinkedHashMap<>();

    static {
        TYPE_PATTERNS.put(TaskType.FEATURE, List.of(
            Pattern.compile("(?:添加|新增|增加|实现|开发|创建|写一个|做个)(?:新)?(?:功能|特性|模块|接口|页面|组件)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:feature|new feature|implement|add|create|develop)", Pattern.CASE_INSENSITIVE)
        ));

        TYPE_PATTERNS.put(TaskType.BUGFIX, List.of(
            Pattern.compile("(?:修复|解决|修正|bug|缺陷|问题|错误|异常|崩溃|NPE|空指针|报错)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:fix|bug|issue|error|exception|crash|broken)", Pattern.CASE_INSENSITIVE)
        ));

        TYPE_PATTERNS.put(TaskType.REFACTOR, List.of(
            Pattern.compile("(?:重构|优化|重写|提取|抽取|合并|拆分|整理|清理)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:refactor|rewrite|optimize|extract|clean|restructure)", Pattern.CASE_INSENSITIVE)
        ));

        TYPE_PATTERNS.put(TaskType.TEST, List.of(
            Pattern.compile("(?:测试|单元测试|集成测试|测试用例|覆盖率)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:test|unit test|integration test|coverage)", Pattern.CASE_INSENSITIVE)
        ));

        TYPE_PATTERNS.put(TaskType.DOC, List.of(
            Pattern.compile("(?:文档|README|API文档|注释|说明|手册|指南)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:doc|documentation|readme|api doc|manual|guide)", Pattern.CASE_INSENSITIVE)
        ));

        TYPE_PATTERNS.put(TaskType.ANALYZE, List.of(
            Pattern.compile("(?:分析|调研|研究|评估|审查|检查|查看|了解)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:analyze|analysis|research|explore|investigate|review)", Pattern.CASE_INSENSITIVE)
        ));

        TYPE_PATTERNS.put(TaskType.DEBUG, List.of(
            Pattern.compile("(?:调试|排查|定位|追踪|诊断|debug|trace|diagnose)", Pattern.CASE_INSENSITIVE)
        ));

        TYPE_PATTERNS.put(TaskType.REVIEW, List.of(
            Pattern.compile("(?:审查|评审|review|code review|PR review)", Pattern.CASE_INSENSITIVE)
        ));

        TYPE_PATTERNS.put(TaskType.CHAT, List.of(
            Pattern.compile("^(?:你好|嗨|hello|hi|hey|早上好|下午好|晚上好|谢谢|感谢|再见|bye)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:你(?:是|叫|能|会|可以|知道|觉得|认为))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:今天|天气|心情|聊聊|聊天|随便问问)", Pattern.CASE_INSENSITIVE)
        ));
    }

    // ==================== 复杂度关键词 ====================

    private static final List<Pattern> COMPLEX_PATTERNS = List.of(
        Pattern.compile("(?:跨模块|跨服务|分布式|微服务|大规模|复杂|大型)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:multiple modules|distributed|microservice|complex|large)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> SIMPLE_PATTERNS = List.of(
        Pattern.compile("(?:简单|改一下|改个|加个|删个|修个|小改动)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:simple|minor|small|quick|tiny|just)", Pattern.CASE_INSENSITIVE)
    );

    // ==================== 中断检测 ====================

    private static final List<Pattern> INTERRUPTION_PATTERNS = List.of(
        Pattern.compile("^/(?:design|debug|review|test|doc|chat|help|stop|pause|cancel)"),
        Pattern.compile("(?:等一下|暂停|停止|取消|先做这个|换个任务|先不管)"),
        Pattern.compile("(?:wait|pause|stop|cancel|never mind|forget|switch|change topic)")
    );

    // ==================== 公共方法 ====================

    /**
     * 分析用户输入
     */
    public AnalysisResult analyze(String userInput, String currentTaskId) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return new AnalysisResult(TaskType.CHAT, Complexity.SIMPLE,
                Collections.emptyList(), "", "Empty input", false, null);
        }

        String input = userInput.trim();

        // 检测中断
        boolean isInterruption = detectInterruption(input);

        // 检测任务类型
        TaskType taskType = detectTaskType(input);

        // 检测复杂度
        Complexity complexity = detectComplexity(input);

        // 提取涉及模块
        List<String> modules = extractModules(input);

        // 提取技术栈
        String techStack = extractTechStack(input);

        // 生成摘要
        String summary = generateSummary(input, taskType);

        return new AnalysisResult(
            taskType, complexity, modules, techStack,
            summary, isInterruption,
            isInterruption ? currentTaskId : null
        );
    }

    /**
     * 快速判断是否为闲聊
     */
    public boolean isChat(String userInput) {
        return detectTaskType(userInput) == TaskType.CHAT;
    }

    /**
     * 快速判断是否为中断
     */
    public boolean isInterruption(String userInput) {
        return detectInterruption(userInput);
    }

    // ==================== 私有方法 ====================

    private TaskType detectTaskType(String input) {
        for (Map.Entry<TaskType, List<Pattern>> entry : TYPE_PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(input).find()) {
                    return entry.getKey();
                }
            }
        }
        return TaskType.GENERAL;
    }

    private Complexity detectComplexity(String input) {
        for (Pattern pattern : COMPLEX_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return Complexity.COMPLEX;
            }
        }
        for (Pattern pattern : SIMPLE_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return Complexity.SIMPLE;
            }
        }
        return Complexity.MEDIUM;
    }

    private boolean detectInterruption(String input) {
        for (Pattern pattern : INTERRUPTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractModules(String input) {
        List<String> modules = new ArrayList<>();

        // 常见模块名
        String[] knownModules = {
            "core", "web", "cli", "common", "parser", "repl", "ui",
            "mcp", "distribution", "parent"
        };

        for (String module : knownModules) {
            if (input.toLowerCase().contains(module)) {
                modules.add(module);
            }
        }

        // 提取文件路径模式
        Pattern pathPattern = Pattern.compile("(?:jwcode-\\w+)");
        java.util.regex.Matcher matcher = pathPattern.matcher(input);
        while (matcher.find()) {
            String match = matcher.group();
            if (!modules.contains(match)) {
                modules.add(match);
            }
        }

        return modules;
    }

    private String extractTechStack(String input) {
        StringBuilder sb = new StringBuilder();

        if (input.contains("Java") || input.contains("java")) sb.append("Java ");
        if (input.contains("Spring") || input.contains("spring")) sb.append("Spring ");
        if (input.contains("Maven") || input.contains("maven")) sb.append("Maven ");
        if (input.contains("TypeScript") || input.contains("typescript") || input.contains("ts")) sb.append("TypeScript ");
        if (input.contains("React") || input.contains("react")) sb.append("React ");
        if (input.contains("Vue") || input.contains("vue")) sb.append("Vue ");
        if (input.contains("Python") || input.contains("python")) sb.append("Python ");
        if (input.contains("Docker") || input.contains("docker")) sb.append("Docker ");
        if (input.contains("SQL") || input.contains("sql") || input.contains("MySQL") || input.contains("mysql")) sb.append("SQL ");

        return sb.toString().trim();
    }

    private String generateSummary(String input, TaskType taskType) {
        // 截取前100个字符作为摘要
        String summary = input.length() > 100 ? input.substring(0, 100) + "..." : input;
        return "[" + taskType.getDisplayName() + "] " + summary;
    }
}
