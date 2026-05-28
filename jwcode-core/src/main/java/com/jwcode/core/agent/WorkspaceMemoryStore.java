package com.jwcode.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    // 内存缓存（带 TTL）
    private Map<String, Object> identityCache;
    private volatile long identityCacheLoadTime;
    private Map<String, Object> patternsCache;
    private volatile long patternsCacheLoadTime;
    private Map<String, Object> insightsCache;
    private volatile long insightsCacheLoadTime;
    private Map<String, Object> prefsCache;
    private volatile long prefsCacheLoadTime;

    /** 缓存过期时间（毫秒），超过后自动重新加载 */
    private static final long CACHE_TTL_MS = 300_000; // 5 分钟

    // ────── 异步写入缓冲区 ──────
    /** 写入缓冲区：累积任务记录后批量落盘 */
    private final BlockingQueue<String> writeBuffer = new LinkedBlockingQueue<>();
    /** 后台写入线程 */
    private final Thread writerThread;
    /** 批量落盘触发阈值 */
    private static final int BATCH_SIZE = 50;
    /** 最大刷新间隔（毫秒） */
    private static final long FLUSH_INTERVAL_MS = 5000;
    private volatile boolean running = true;

    public WorkspaceMemoryStore(Path workspaceRoot) {
        this.memoryDir = workspaceRoot.resolve(".jwcode").resolve("memory");
        this.identityFile = memoryDir.resolve("project_identity.json");
        this.patternsFile = memoryDir.resolve("patterns.json");
        this.taskHistoryFile = memoryDir.resolve("task_history.jsonl");
        this.insightsFile = memoryDir.resolve("insights.json");
        this.prefsFile = memoryDir.resolve("user_prefs.json");
        this.planContextFile = memoryDir.resolve("plan_context.md");
        initDirectory();
        // 启动后台写入线程
        this.writerThread = new Thread(this::drainWriteBuffer, "memory-store-writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
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
     * 加载项目身份（带 5 分钟 TTL 缓存）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadIdentity() {
        if (identityCache != null && !isCacheStale(identityCacheLoadTime)) return identityCache;
        identityCache = loadJson(identityFile);
        identityCacheLoadTime = System.currentTimeMillis();
        return identityCache;
    }

    /**
     * 加载代码模式（带 TTL）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadPatterns() {
        if (patternsCache != null && !isCacheStale(patternsCacheLoadTime)) return patternsCache;
        patternsCache = loadJson(patternsFile);
        patternsCacheLoadTime = System.currentTimeMillis();
        return patternsCache;
    }

    /**
     * 加载洞察（带 TTL）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadInsights() {
        if (insightsCache != null && !isCacheStale(insightsCacheLoadTime)) return insightsCache;
        insightsCache = loadJson(insightsFile);
        insightsCacheLoadTime = System.currentTimeMillis();
        return insightsCache;
    }

    /**
     * 加载用户偏好（带 TTL）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadPreferences() {
        if (prefsCache != null && !isCacheStale(prefsCacheLoadTime)) return prefsCache;
        prefsCache = loadJson(prefsFile);
        prefsCacheLoadTime = System.currentTimeMillis();
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
     * 追加任务完成记录（异步批量写入，降低磁盘 IO 频率）。
     */
    public void appendTaskRecord(Map<String, Object> record) {
        try {
            record.putIfAbsent("timestamp", TS_FORMAT.format(Instant.now()));
            String line = MAPPER.writeValueAsString(record) + "\n";
            writeBuffer.offer(line); // 非阻塞入队
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to serialize task record", e);
        }
    }

    /**
     * 加载最近 N 条任务历史（流式读取，避免全量加载 OOM）。
     */
    public List<Map<String, Object>> loadRecentTaskHistory(int limit) {
        List<Map<String, Object>> history = new ArrayList<>();
        try {
            if (!Files.exists(taskHistoryFile)) return history;
            // 流式读取，只保留最后 N 条
            List<String> lines = Files.readAllLines(taskHistoryFile);
            int start = Math.max(0, lines.size() - limit);
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) continue;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entry = MAPPER.readValue(line, Map.class);
                    history.add(entry);
                } catch (IOException e) {
                    // 跳过损坏的行
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load recent task history", e);
        }
        return history;
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
        identityCacheLoadTime = 0;
        patternsCache = null;
        patternsCacheLoadTime = 0;
        insightsCache = null;
        insightsCacheLoadTime = 0;
        prefsCache = null;
        prefsCacheLoadTime = 0;
    }

    /**
     * 检查缓存是否过期。
     */
    private boolean isCacheStale(long loadTime) {
        return loadTime == 0 || (System.currentTimeMillis() - loadTime) > CACHE_TTL_MS;
    }

    // ────── 异步写入引擎 ──────

    /**
     * 后台线程：从缓冲区取记录，批量写入磁盘。
     */
    private void drainWriteBuffer() {
        List<String> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlush = System.currentTimeMillis();

        while (running) {
            try {
                // 阻塞等待第一条记录（最多等 FLUSH_INTERVAL_MS）
                String first = writeBuffer.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    // 尽力批量取剩余记录（非阻塞 drainTo）
                    writeBuffer.drainTo(batch, BATCH_SIZE - 1);
                }

                // 触发条件：批次满 或 时间到
                if (!batch.isEmpty() &&
                    (batch.size() >= BATCH_SIZE ||
                     System.currentTimeMillis() - lastFlush >= FLUSH_INTERVAL_MS)) {
                    flushBatch(batch);
                    batch.clear();
                    lastFlush = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 退出前刷新残留数据
        if (!batch.isEmpty()) {
            writeBuffer.drainTo(batch);
            flushBatch(batch);
        }
    }

    /**
     * 批量写入缓冲区记录。
     */
    private void flushBatch(List<String> batch) {
        if (batch.isEmpty()) return;
        try (BufferedWriter writer = Files.newBufferedWriter(taskHistoryFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String line : batch) {
                writer.write(line);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to flush " + batch.size() + " task records", e);
        }
    }

    /**
     * 强制刷新所有待写入数据到磁盘（优雅关闭时调用）。
     */
    public void flush() {
        List<String> remaining = new ArrayList<>();
        writeBuffer.drainTo(remaining);
        if (!remaining.isEmpty()) {
            flushBatch(remaining);
        }
    }

    /**
     * 关闭后台写入线程并刷新数据。
     */
    public void shutdown() {
        running = false;
        writerThread.interrupt();
        try {
            writerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flush();
        logger.fine("[WorkspaceMemoryStore] Shutdown complete");
    }

    // ==================== 语义检索 ====================

    private com.jwcode.core.llm.LLMService embedService;

    public void setEmbedService(com.jwcode.core.llm.LLMService service) {
        this.embedService = service;
    }

    /**
     * 语义检索记忆 — 余弦相似度 + 关键词混合排序。
     */
    public List<String> semanticSearch(String query, int topK) {
        if (embedService == null) return keywordFallback(query, topK);
        try {
            float[] qVec = embedService.embed(query).get(5, java.util.concurrent.TimeUnit.SECONDS);
            if (qVec.length == 0) return keywordFallback(query, topK);

            List<String> texts = collectTexts();
            if (texts.isEmpty()) return List.of();

            // 余弦排序 + 关键词加分
            texts.sort((a, b) -> {
                double sa = cosineScore(qVec, a) + keywordBonus(query, a);
                double sb = cosineScore(qVec, b) + keywordBonus(query, b);
                return Double.compare(sb, sa);
            });
            return texts.stream().limit(topK).toList();
        } catch (Exception e) {
            logger.fine("[semanticSearch] Failed: " + e.getMessage());
            return keywordFallback(query, topK);
        }
    }

    private List<String> collectTexts() {
        List<String> texts = new ArrayList<>();
        for (Map<String, Object> m : loadTaskHistory()) {
            String s = String.valueOf(m.getOrDefault("summary", ""));
            if (!s.isEmpty()) texts.add(s);
        }
        for (Map.Entry<String, Object> e : loadInsights().entrySet()) {
            String s = String.valueOf(e.getValue());
            if (!s.isEmpty()) texts.add(s);
        }
        return texts;
    }

    private double cosineScore(float[] qVec, String text) {
        try {
            float[] tVec = embedService.embed(text).get(5, java.util.concurrent.TimeUnit.SECONDS);
            if (tVec.length == 0) return 0;
            double dot = 0, nq = 0, nt = 0;
            int len = Math.min(qVec.length, tVec.length);
            for (int i = 0; i < len; i++) { dot += qVec[i]*tVec[i]; nq += qVec[i]*qVec[i]; nt += tVec[i]*tVec[i]; }
            return dot / (Math.sqrt(nq) * Math.sqrt(nt) + 1e-9);
        } catch (Exception e) { return 0; }
    }

    private static double keywordBonus(String query, String text) {
        double score = 0;
        String tl = text.toLowerCase();
        for (String word : query.toLowerCase().split("\\s+")) {
            if (tl.contains(word)) score += 0.3;
        }
        return score;
    }

    private List<String> keywordFallback(String query, int topK) {
        return collectTexts().stream()
            .filter(t -> query.isBlank() || t.toLowerCase().contains(query.toLowerCase()))
            .limit(topK).toList();
    }
}