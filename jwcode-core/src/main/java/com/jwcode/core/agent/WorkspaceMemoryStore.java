package com.jwcode.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WorkspaceMemoryStore — 工作目录级内存持久化存储。
 *
 * <p>每个工作目录的 `.jwcode/memory/` 下存储项目记忆，包括：
 * <ul>
 *   <li>{@code project_identity.json} — 项目身份（名称、类型、语言、框架）</li>
 *   <li>{@code patterns.json} — 学到的代码模式、约定</li>
 *   <li>{@code task_history.jsonl} — 追加式任务完成记录</li>
 *   <li>{@code insights.json} — 代码库关键洞察</li>
 *   <li>{@code user_prefs.json} — 观察到的用户偏好</li>
 *   <li>{@code plan_context.md} — 自动生成的 plan 模式上下文摘要</li>
 * </ul>
 * </p>
 */
public class WorkspaceMemoryStore {

    private static final Logger logger = Logger.getLogger(WorkspaceMemoryStore.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Path memoryDir;

    // 文件路径缓存
    private final Path identityFile;
    private final Path patternsFile;
    private final Path taskHistoryFile;
    private final Path insightsFile;
    private final Path prefsFile;
    private final Path planContextFile;

    // 内存缓存
    private Map<String, Object> identityCache;
    private Map<String, Object> patternsCache;
    private Map<String, Object> insightsCache;
    private Map<String, Object> prefsCache;

    public WorkspaceMemoryStore(Path workspaceRoot) {
        this.memoryDir = workspaceRoot.resolve(".jwcode").resolve("memory");
        this.identityFile = memoryDir.resolve("project_identity.json");
        this.patternsFile = memoryDir.resolve("patterns.json");
        this.taskHistoryFile = memoryDir.resolve("task_history.jsonl");
        this.insightsFile = memoryDir.resolve("insights.json");
        this.prefsFile = memoryDir.resolve("user_prefs.json");
        this.planContextFile = memoryDir.resolve("plan_context.md");
        initDirectory();
    }

    /**
     * 初始化存储目录和文件
     */
    private void initDirectory() {
        try {
            Files.createDirectories(memoryDir);
            // 确保所有文件存在
            for (Path file : Arrays.asList(identityFile, patternsFile,
                    insightsFile, prefsFile, planContextFile)) {
                if (!Files.exists(file)) {
                    Files.createFile(file);
                    Files.writeString(file, file == planContextFile
                            ? "# Plan Context\n\n此文件由 MemoryAgent 自动生成。\n"
                            : "{}");
                }
            }
            if (!Files.exists(taskHistoryFile)) {
                Files.createFile(taskHistoryFile);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to init memory dir: " + memoryDir, e);
        }
    }

    // ==================== 读操作 ====================

    /**
     * 加载项目身份
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadIdentity() {
        if (identityCache != null) return identityCache;
        identityCache = loadJson(identityFile);
        return identityCache;
    }

    /**
     * 加载代码模式
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadPatterns() {
        if (patternsCache != null) return patternsCache;
        patternsCache = loadJson(patternsFile);
        return patternsCache;
    }

    /**
     * 加载洞察
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadInsights() {
        if (insightsCache != null) return insightsCache;
        insightsCache = loadJson(insightsFile);
        return insightsCache;
    }

    /**
     * 加载用户偏好
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadPreferences() {
        if (prefsCache != null) return prefsCache;
        prefsCache = loadJson(prefsFile);
        return prefsCache;
    }

    /**
     * 加载任务历史（JSONL）
     */
    public List<Map<String, Object>> loadTaskHistory() {
        List<Map<String, Object>> history = new ArrayList<>();
        try {
            if (Files.exists(taskHistoryFile)) {
                List<String> lines = Files.readAllLines(taskHistoryFile);
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> entry = MAPPER.readValue(line, Map.class);
                        history.add(entry);
                    } catch (IOException e) {
                        // 跳过损坏的行
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load task history", e);
        }
        return history;
    }

    /**
     * 加载 plan 上下文（Markdown）
     */
    public String loadPlanContext() {
        try {
            if (Files.exists(planContextFile)) {
                return Files.readString(planContextFile);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load plan context", e);
        }
        return "";
    }

    // ==================== 写操作 ====================

    /**
     * 保存项目身份
     */
    public void saveIdentity(Map<String, Object> identity) {
        this.identityCache = identity;
        saveJson(identityFile, identity);
    }

    /**
     * 更新项目身份中的字段
     */
    public void updateIdentity(String key, Object value) {
        Map<String, Object> identity = loadIdentity();
        identity.put(key, value);
        saveIdentity(identity);
    }

    /**
     * 添加代码模式
     */
    public void addPattern(String name, Object pattern) {
        Map<String, Object> patterns = loadPatterns();
        patterns.put(name, pattern);
        savePatterns(patterns);
    }

    /**
     * 保存代码模式
     */
    public void savePatterns(Map<String, Object> patterns) {
        this.patternsCache = patterns;
        saveJson(patternsFile, patterns);
    }

    /**
     * 添加洞察
     */
    public void addInsight(String key, String insight) {
        Map<String, Object> insights = loadInsights();
        insights.put(key, insight);
        saveInsights(insights);
    }

    /**
     * 保存洞察
     */
    public void saveInsights(Map<String, Object> insights) {
        this.insightsCache = insights;
        saveJson(insightsFile, insights);
    }

    /**
     * 更新用户偏好
     */
    public void updatePreference(String key, Object value) {
        Map<String, Object> prefs = loadPreferences();
        prefs.put(key, value);
        savePreferences(prefs);
    }

    /**
     * 保存用户偏好
     */
    public void savePreferences(Map<String, Object> prefs) {
        this.prefsCache = prefs;
        saveJson(prefsFile, prefs);
    }

    /**
     * 追加任务完成记录（JSONL 格式）
     */
    public void appendTaskRecord(Map<String, Object> record) {
        try {
            record.putIfAbsent("timestamp", TS_FORMAT.format(Instant.now()));
            String line = MAPPER.writeValueAsString(record) + "\n";
            Files.writeString(taskHistoryFile, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write task record", e);
        }
    }

    /**
     * 保存 plan 上下文
     */
    public void savePlanContext(String context) {
        try {
            Files.writeString(planContextFile, context);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save plan context", e);
        }
    }

    // ==================== 工具方法 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadJson(Path file) {
        try {
            if (Files.exists(file) && Files.size(file) > 2) {
                return MAPPER.readValue(file.toFile(), Map.class);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load: " + file, e);
        }
        return new LinkedHashMap<>();
    }

    private void saveJson(Path file, Map<String, Object> data) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save: " + file, e);
        }
    }

    /**
     * 获取内存目录路径
     */
    public Path getMemoryDir() {
        return memoryDir;
    }

    /**
     * 清除所有内存缓存
     */
    public void clearCache() {
        identityCache = null;
        patternsCache = null;
        insightsCache = null;
        prefsCache = null;
    }
}
