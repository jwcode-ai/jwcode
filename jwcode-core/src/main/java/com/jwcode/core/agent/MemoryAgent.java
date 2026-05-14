package com.jwcode.core.agent;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * MemoryAgent — 工作目录级主动记忆Agent。
 *
 * <p>职责：为每个工作目录维护项目记忆，在每个任务完成后主动记录，并在 plan 模式下
 * 为 AI 提供记忆上下文。</p>
 *
 * <p>工作流程：
 * <ol>
 *   <li>初始化时自动探测项目身份（类型、语言、框架）</li>
 *   <li>每次任务完成后调用 {@link #recordTaskCompletion} 记录任务摘要</li>
 *   <li>Plan 模式调用 {@link #getPlanContextPrompt} 获取记忆上下文注入 AI prompt</li>
 *   <li>定期（或按需）调用 {@link #regeneratePlanContext} 重新生成 plan 上下文</li>
 * </ol>
 * </p>
 *
 * <p>存储位置：{@code <workspace>/.jwcode/memory/}</p>
 */
public class MemoryAgent {

    private static final Logger logger = Logger.getLogger(MemoryAgent.class.getName());

    /** 工作目录根路径 */
    private final Path workspaceRoot;

    /** 持久化存储 */
    private final WorkspaceMemoryStore store;

    /** 是否启用 */
    private boolean enabled = true;

    /** 任务计数（用于判断何时重新生成 plan 上下文） */
    private int tasksSinceLastRegen = 0;

    /** 重新生成 plan 上下文的频率（每 N 个任务） */
    private static final int REGEN_THRESHOLD = 5;

    public MemoryAgent(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.normalize().toAbsolutePath();
        this.store = new WorkspaceMemoryStore(this.workspaceRoot);
        initProjectIdentity();
    }

    // ==================== 初始化 ====================

    /**
     * 自动探测项目身份信息
     */
    private void initProjectIdentity() {
        try {
            Map<String, Object> identity = store.loadIdentity();

            // 如果已有身份信息，跳过
            if (!identity.isEmpty() && identity.containsKey("projectName")) {
                logger.fine("[MemoryAgent] 项目身份已存在: " + identity.get("projectName"));
                return;
            }

            // 从工作目录名推断项目名
            String projectName = workspaceRoot.getFileName().toString();
            identity.put("projectName", projectName);
            identity.put("workspacePath", workspaceRoot.toString());
            identity.put("initializedAt", Instant.now().toString());

            // 探测项目类型
            detectProjectType(identity);

            // 探测语言和框架
            detectLanguages(identity);

            store.saveIdentity(identity);
            logger.info("[MemoryAgent] 初始化项目身份: " + projectName
                    + " | 类型: " + identity.getOrDefault("projectType", "未知")
                    + " | 语言: " + identity.getOrDefault("languages", "未知"));
        } catch (Exception e) {
            logger.warning("[MemoryAgent] 初始化项目身份失败: " + e.getMessage());
        }
    }

    /**
     * 探测项目类型
     */
    private void detectProjectType(Map<String, Object> identity) {
        try {
            // Maven 项目
            if (java.nio.file.Files.exists(workspaceRoot.resolve("pom.xml"))) {
                identity.put("projectType", "maven");
                identity.put("buildTool", "maven");
            }
            // Gradle 项目
            else if (java.nio.file.Files.exists(workspaceRoot.resolve("build.gradle"))
                    || java.nio.file.Files.exists(workspaceRoot.resolve("build.gradle.kts"))) {
                identity.put("projectType", "gradle");
                identity.put("buildTool", "gradle");
            }
            // Node.js 项目
            else if (java.nio.file.Files.exists(workspaceRoot.resolve("package.json"))) {
                identity.put("projectType", "nodejs");
                identity.put("buildTool", "npm");
            }
            // Python 项目
            else if (java.nio.file.Files.exists(workspaceRoot.resolve("setup.py"))
                    || java.nio.file.Files.exists(workspaceRoot.resolve("pyproject.toml"))) {
                identity.put("projectType", "python");
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 探测项目语言
     */
    private void detectLanguages(Map<String, Object> identity) {
        try {
            List<String> languages = new ArrayList<>();
            // 检查 Java 文件
            if (hasFilesWithExtension(workspaceRoot, ".java")) {
                languages.add("Java");
            }
            // 检查 TypeScript/JavaScript
            if (hasFilesWithExtension(workspaceRoot, ".ts") || hasFilesWithExtension(workspaceRoot, ".tsx")) {
                languages.add("TypeScript");
            }
            if (hasFilesWithExtension(workspaceRoot, ".js") || hasFilesWithExtension(workspaceRoot, ".jsx")) {
                languages.add("JavaScript");
            }
            // 检查 Python
            if (hasFilesWithExtension(workspaceRoot, ".py")) {
                languages.add("Python");
            }
            // 检查前端
            if (hasFilesWithExtension(workspaceRoot, ".tsx") || hasFilesWithExtension(workspaceRoot, ".jsx")) {
                identity.put("frontend", true);
                identity.put("frontendFramework", detectFrontendFramework());
            }

            if (!languages.isEmpty()) {
                identity.put("languages", String.join(", ", languages));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 探测前端框架
     */
    private String detectFrontendFramework() {
        try {
            Path packageJson = workspaceRoot.resolve("package.json");
            if (java.nio.file.Files.exists(packageJson)) {
                String content = java.nio.file.Files.readString(packageJson);
                if (content.contains("\"react\"")) return "React";
                if (content.contains("\"vue\"")) return "Vue";
                if (content.contains("\"angular\"")) return "Angular";
                if (content.contains("\"svelte\"")) return "Svelte";
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 检查目录中是否存在指定扩展名的文件（浅搜索）
     */
    private boolean hasFilesWithExtension(Path dir, String extension) {
        try {
            return java.nio.file.Files.walk(dir, 3)
                    .anyMatch(p -> p.toString().endsWith(extension));
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 记忆操作 ====================

    /**
     * 记录代码模式（项目约定、命名规范等）
     */
    public void rememberPattern(String name, String description) {
        if (!enabled) return;
        try {
            Map<String, Object> pattern = new LinkedHashMap<>();
            pattern.put("description", description);
            pattern.put("learnedAt", Instant.now().toString());
            store.addPattern(name, pattern);
            logger.fine("[MemoryAgent] 记录模式: " + name);
        } catch (Exception e) {
            logger.warning("[MemoryAgent] 记录模式失败: " + e.getMessage());
        }
    }

    /**
     * 记录项目洞察
     */
    public void rememberInsight(String topic, String insight) {
        if (!enabled) return;
        try {
            store.addInsight(topic, insight);
            logger.fine("[MemoryAgent] 记录洞察: " + topic);
        } catch (Exception e) {
            logger.warning("[MemoryAgent] 记录洞察失败: " + e.getMessage());
        }
    }

    /**
     * 记录用户偏好
     */
    public void rememberPreference(String key, Object value) {
        if (!enabled) return;
        try {
            store.updatePreference(key, value);
            logger.fine("[MemoryAgent] 记录偏好: " + key + " = " + value);
        } catch (Exception e) {
            logger.warning("[MemoryAgent] 记录偏好失败: " + e.getMessage());
        }
    }

    // ==================== 任务完成自动记忆 ====================

    /**
     * 记录任务完成 — 自动提取关键信息并持久化。
     *
     * @param taskDescription 任务描述
     * @param agentType       执行的 Agent 类型
     * @param status          完成状态 (completed/failed)
     * @param summary         任务结果摘要
     * @param filesChanged    变更的文件列表
     */
    public void recordTaskCompletion(String taskDescription, String agentType,
                                      String status, String summary,
                                      List<String> filesChanged) {
        if (!enabled) return;
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("task", taskDescription);
            record.put("agent", agentType);
            record.put("status", status);
            record.put("summary", summary.length() > 500 ? summary.substring(0, 500) + "..." : summary);
            if (filesChanged != null && !filesChanged.isEmpty()) {
                record.put("filesChanged", new ArrayList<>(filesChanged));
            }

            store.appendTaskRecord(record);

            tasksSinceLastRegen++;
            if (tasksSinceLastRegen >= REGEN_THRESHOLD) {
                regeneratePlanContext();
                tasksSinceLastRegen = 0;
            }

            logger.fine("[MemoryAgent] 记录任务完成: " + taskDescription + " [" + status + "]");
        } catch (Exception e) {
            logger.warning("[MemoryAgent] 记录任务完成失败: " + e.getMessage());
        }
    }

    // ==================== Plan 模式上下文 ====================

    /**
     * 重新生成 plan 上下文 — 汇总所有记忆为 Markdown 格式。
     * 在 plan 模式下会注入到 AI prompt 中。
     */
    public void regeneratePlanContext() {
        if (!enabled) return;
        try {
            StringBuilder ctx = new StringBuilder();
            ctx.append("# 项目记忆上下文 (MemoryAgent 自动生成)\n\n");

            // 1. 项目身份
            Map<String, Object> identity = store.loadIdentity();
            if (!identity.isEmpty()) {
                ctx.append("## 项目概况\n\n");
                ctx.append("| 属性 | 值 |\n");
                ctx.append("|------|------|\n");
                for (Map.Entry<String, Object> e : identity.entrySet()) {
                    String key = e.getKey();
                    String value = String.valueOf(e.getValue());
                    if (value.length() > 100) value = value.substring(0, 97) + "...";
                    if (!key.equals("workspacePath")) {
                        ctx.append("| ").append(key).append(" | ").append(value).append(" |\n");
                    }
                }
                ctx.append("\n");
            }

            // 2. 代码规范/模式
            Map<String, Object> patterns = store.loadPatterns();
            if (!patterns.isEmpty()) {
                ctx.append("## 已知代码规范/模式\n\n");
                for (Map.Entry<String, Object> e : patterns.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pattern = (Map<String, Object>) e.getValue();
                    ctx.append("- **").append(e.getKey()).append("**: ")
                       .append(pattern.getOrDefault("description", "")).append("\n");
                }
                ctx.append("\n");
            }

            // 3. 关键洞察
            Map<String, Object> insights = store.loadInsights();
            if (!insights.isEmpty()) {
                ctx.append("## 项目洞察\n\n");
                for (Map.Entry<String, Object> e : insights.entrySet()) {
                    ctx.append("- **").append(e.getKey()).append("**: ")
                       .append(e.getValue()).append("\n");
                }
                ctx.append("\n");
            }

            // 4. 最近任务历史
            List<Map<String, Object>> taskHistory = store.loadTaskHistory();
            if (!taskHistory.isEmpty()) {
                int recent = Math.min(10, taskHistory.size());
                List<Map<String, Object>> recentTasks = taskHistory.subList(
                        taskHistory.size() - recent, taskHistory.size());
                ctx.append("## 最近完成的任务（最近 ").append(recent).append(" 个）\n\n");
                for (int i = recentTasks.size() - 1; i >= 0; i--) {
                    Map<String, Object> task = recentTasks.get(i);
                    String statusIcon = "completed".equals(task.get("status")) ? "✅" : "❌";
                    ctx.append("- ").append(statusIcon).append(" ")
                       .append(task.getOrDefault("task", "未知任务"))
                       .append(" `[").append(task.getOrDefault("agent", "?"))
                       .append("]`\n");
                }
                ctx.append("\n");
            }

            // 5. 用户偏好
            Map<String, Object> prefs = store.loadPreferences();
            if (!prefs.isEmpty()) {
                ctx.append("## 用户偏好\n\n");
                for (Map.Entry<String, Object> e : prefs.entrySet()) {
                    ctx.append("- **").append(e.getKey()).append("**: ").append(e.getValue()).append("\n");
                }
                ctx.append("\n");
            }

            ctx.append("---\n");
            ctx.append("> 此上下文由 MemoryAgent 自动维护。在 plan 模式下会自动注入到 AI prompt 中。\n");

            store.savePlanContext(ctx.toString());
            logger.info("[MemoryAgent] 重新生成 plan 上下文 ("
                    + ctx.length() + " chars, " + tasksSinceLastRegen + " tasks since last regen)");
        } catch (Exception e) {
            logger.warning("[MemoryAgent] 重新生成 plan 上下文失败: " + e.getMessage());
        }
    }

    /**
     * 获取用于 plan 模式的 prompt 注入文本。
     * 此文本将被注入到 AI 的 plan 请求中，帮助 AI 做出更好的任务规划。
     *
     * @return plan 上下文 prompt 文本
     */
    public String getPlanContextPrompt() {
        if (!enabled) return "";
        try {
            // 优先读取缓存的 plan_context.md
            String cached = store.loadPlanContext();
            if (cached != null && cached.length() > 50) {
                return "\n\n" + cached;
            }

            // 如果缓存为空，即时生成
            regeneratePlanContext();
            cached = store.loadPlanContext();
            return (cached != null && cached.length() > 50) ? "\n\n" + cached : "";
        } catch (Exception e) {
            logger.warning("[MemoryAgent] 获取 plan 上下文失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 获取简洁版记忆摘要（用于在聊天中快速了解项目上下文）
     *
     * @return 简洁的项目记忆摘要
     */
    public String getQuickSummary() {
        if (!enabled) return "";
        Map<String, Object> identity = store.loadIdentity();
        if (identity.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[项目记忆] ");
        sb.append(identity.getOrDefault("projectName", "未知项目"));

        Object type = identity.get("projectType");
        if (type != null) sb.append(" | ").append(type);

        Object langs = identity.get("languages");
        if (langs != null) sb.append(" | ").append(langs);

        Object fw = identity.get("frontendFramework");
        if (fw != null) sb.append(" | 前端: ").append(fw);

        List<Map<String, Object>> history = store.loadTaskHistory();
        if (!history.isEmpty()) {
            sb.append(" | ").append(history.size()).append(" 个已完成任务");
        }

        return sb.toString();
    }

    // ==================== 查询方法 ====================

    /**
     * 获取最近的任务历史
     */
    public List<Map<String, Object>> getRecentTaskHistory(int count) {
        List<Map<String, Object>> history = store.loadTaskHistory();
        int from = Math.max(0, history.size() - count);
        return history.subList(from, history.size());
    }

    /**
     * 获取项目身份信息
     */
    public Map<String, Object> getProjectIdentity() {
        return store.loadIdentity();
    }

    /**
     * 获取存储的代码模式
     */
    public Map<String, Object> getPatterns() {
        return store.loadPatterns();
    }

    /**
     * 获取存储的洞察
     */
    public Map<String, Object> getInsights() {
        return store.loadInsights();
    }

    /**
     * 获取任务历史中涉及的文件
     */
    public Set<String> getKnownFiles() {
        Set<String> files = new LinkedHashSet<>();
        for (Map<String, Object> task : store.loadTaskHistory()) {
            @SuppressWarnings("unchecked")
            List<String> changed = (List<String>) task.get("filesChanged");
            if (changed != null) {
                files.addAll(changed);
            }
        }
        return files;
    }

    // ==================== 控制方法 ====================

    /**
     * 启用/禁用 MemoryAgent
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            regeneratePlanContext();
        }
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取工作目录根路径
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * 获取底层存储
     */
    public WorkspaceMemoryStore getStore() {
        return store;
    }
}
