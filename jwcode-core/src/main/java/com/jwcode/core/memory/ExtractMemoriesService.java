package com.jwcode.core.memory;

import com.jwcode.core.index.EmbeddingService;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ExtractMemoriesService — 自动记忆提取服务（对标 Claude Code extractMemories.ts）。
 *
 * <p>在每个 query loop 结束时自动运行（fire-and-forget），从会话转录中
 * 提取持久记忆写入 {@code .jwcode/memory/} 目录。使用 Fork Agent 模式
 * （子 Agent 共享父 Agent 的上下文缓存），只读文件 + 只能写 memory 目录。</p>
 *
 * <h3>核心设计</h3>
 * <ul>
 *   <li><b>Cursor 跟踪</b>：记录 lastProcessedMessageUuid，每次只处理新消息</li>
 *   <li><b>互斥</b>：主 Agent 自己写 memory 时跳过自动提取（避免重复）</li>
 *   <li><b>Trailing Extraction</b>：提取进行中有新消息时，当前提取完成后自动追跑一次</li>
 *   <li><b>节流</b>：默认每 N 个符合条件的 turn 运行一次</li>
 *   <li><b>最佳努力</b>：失败不影响主流程</li>
 * </ul>
 *
 * <h3>四种记忆类型</h3>
 * <ul>
 *   <li><b>user</b> — 用户角色/偏好/知识背景（私人）</li>
 *   <li><b>feedback</b> — 用户反馈/纠正/确认（私人或团队）</li>
 *   <li><b>project</b> — 项目目标/决策/约束（偏团队）</li>
 *   <li><b>reference</b> — 外部系统引用（偏团队）</li>
 * </ul>
 *
 * <h3>与 Claude Code 的对应关系</h3>
 * <pre>
 *   extractMemories.ts → ExtractMemoriesService.java
 *   initExtractMemories() → init()
 *   executeExtractMemories() → extractAsync()
 *   drainPendingExtraction() → drain()
 *   hasMemoryWritesSince() → hasMemoryWritesSince()
 *   createAutoMemCanUseTool() → getMemoryDirPermissions()
 * </pre>
 *
 * @author JWCode Team
 * @since 3.0.0
 */
public class ExtractMemoriesService {

    private static final Logger logger = Logger.getLogger(ExtractMemoriesService.class.getName());

    // ==================== 记忆类型 ====================

    public enum MemoryType {
        USER("user", "用户角色、偏好、知识背景"),
        FEEDBACK("feedback", "用户反馈、纠正、确认"),
        PROJECT("project", "项目目标、决策、约束"),
        REFERENCE("reference", "外部系统引用（Linear、Slack、Grafana 等）");

        public final String value;
        public final String description;

        MemoryType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public static MemoryType fromString(String s) {
            for (MemoryType t : values()) {
                if (t.value.equals(s)) return t;
            }
            return null;
        }
    }

    // ==================== 记忆条目 ====================

    /**
     * 单条记忆 — 对应 memory 目录中的一个 .md 文件。
     */
    public static class MemoryEntry {
        /** 文件名 slug（不含 .md） */
        public String name;
        /** 单行描述（用于 MEMORY.md 索引） */
        public String description;
        /** 记忆类型 */
        public MemoryType type;
        /** 记忆体内容 */
        public String body;
        /** 文件路径 */
        public Path filePath;
        /** 最后修改时间 */
        public Instant lastModified;
        /** Token 估算 */
        public long tokenEstimate;

        /**
         * 解析 frontmatter。
         * 格式：
         * <pre>
         * ---
         * name: {{slug}}
         * description: {{one-line}}
         * type: {{user|feedback|project|reference}}
         * ---
         * {{body}}
         * </pre>
         */
        public static MemoryEntry parse(Path filePath, String content) {
            MemoryEntry entry = new MemoryEntry();
            entry.filePath = filePath;
            entry.name = filePath.getFileName().toString().replace(".md", "");

            if (content.startsWith("---")) {
                int end = content.indexOf("---", 3);
                if (end > 0) {
                    String frontmatter = content.substring(3, end).trim();
                    for (String line : frontmatter.split("\n")) {
                        String[] parts = line.split(":", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();
                            switch (key) {
                                case "name" -> entry.name = value;
                                case "description" -> entry.description = value;
                                case "type" -> entry.type = MemoryType.fromString(value);
                            }
                        }
                    }
                    entry.body = content.substring(end + 3).trim();
                }
            } else {
                entry.body = content;
            }

            // 粗略 Token 估算
            entry.tokenEstimate = content.length() / 4;
            return entry;
        }

        /**
         * 序列化为 frontmatter 格式的 Markdown。
         */
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("---\n");
            sb.append("name: ").append(name != null ? name : "").append("\n");
            sb.append("description: ").append(description != null ? description : "").append("\n");
            if (type != null) {
                sb.append("type: ").append(type.value).append("\n");
            }
            sb.append("---\n");
            if (body != null && !body.isEmpty()) {
                sb.append("\n").append(body).append("\n");
            }
            return sb.toString();
        }
    }

    // ==================== 闭包状态 ====================

    /** UUID of last message processed — cursor for incremental extraction */
    private final AtomicReference<String> lastProcessedUuid = new AtomicReference<>();

    /** In-progress extraction lock */
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

    /** Pending context for trailing extraction */
    private final AtomicReference<PendingContext> pendingContext = new AtomicReference<>();

    /** 提取次数节流计数 */
    private int turnsSinceLastExtraction = 0;
    private static final int DEFAULT_EXTRACT_EVERY_N_TURNS = 1;

    /** 进行中的提取任务 */
    private final Set<CompletableFuture<Void>> inFlightExtractions = ConcurrentHashMap.newKeySet();

    /** 记忆目录路径 */
    private final Path memoryDir;

    /** 是否启用 */
    private volatile boolean enabled = true;

    /** Fork Agent 执行器（由外部注入） */
    private ForkAgentExecutor forkAgentExecutor;

    /** 嵌入服务（用于语义记忆检索，可为 null） */
    private EmbeddingService embeddingService;

    // ==================== 内部类 ====================

    private static class PendingContext {
        final List<MessageStub> messages;
        final String agentId;

        PendingContext(List<MessageStub> messages, String agentId) {
            this.messages = messages;
            this.agentId = agentId;
        }
    }

    /**
     * Fork Agent 执行器接口 — 由主框架注入。
     * 对标 Claude Code 的 runForkedAgent()。
     */
    public interface ForkAgentExecutor {
        /**
         * 执行 Fork Agent。
         *
         * @param systemPrompt 系统 prompt
         * @param userPrompt 用户 prompt
         * @param allowedTools 允许的工具（只读 + memory 目录写入）
         * @param maxTurns 最大 turn 数
         * @return Agent 生成的输出消息列表
         */
        List<String> execute(String systemPrompt, String userPrompt,
                            Set<String> allowedTools, int maxTurns) throws Exception;
    }

    /**
     * 消息存根 — 最小化消息表示，仅用于 cursor 跟踪。
     */
    public static class MessageStub {
        public String uuid;
        public String role; // user, assistant, system
        public String content;
        /** tool_use blocks 中是否包含对 memory 目录的 Write/Edit */
        public boolean hasMemoryWrite;

        public MessageStub(String uuid, String role, String content) {
            this.uuid = uuid;
            this.role = role;
            this.content = content;
        }
    }

    // ==================== 构造函数 ====================

    public ExtractMemoriesService(Path memoryDir) {
        this.memoryDir = memoryDir;
    }

    public ExtractMemoriesService(Path memoryDir, ForkAgentExecutor forkAgentExecutor) {
        this.memoryDir = memoryDir;
        this.forkAgentExecutor = forkAgentExecutor;
    }

    public ExtractMemoriesService(Path memoryDir, ForkAgentExecutor forkAgentExecutor,
                                   EmbeddingService embeddingService) {
        this.memoryDir = memoryDir;
        this.forkAgentExecutor = forkAgentExecutor;
        this.embeddingService = embeddingService;
    }

    // ==================== 公共 API ====================

    /**
     * 异步提取记忆（fire-and-forget）。
     * 对标 Claude Code 的 executeExtractMemories()。
     *
     * @param messages 所有会话消息
     * @param agentId 当前 agent ID（主 agent 为 null/空）
     */
    public CompletableFuture<Void> extractAsync(List<MessageStub> messages, String agentId) {
        if (!enabled || forkAgentExecutor == null) {
            return CompletableFuture.completedFuture(null);
        }

        // 只对主 agent 运行
        if (agentId != null && !agentId.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // 节流
        turnsSinceLastExtraction++;
        int threshold = DEFAULT_EXTRACT_EVERY_N_TURNS;
        if (turnsSinceLastExtraction < threshold) {
            return CompletableFuture.completedFuture(null);
        }
        turnsSinceLastExtraction = 0;

        // 如果提取正在进行中，stash 上下文
        if (inProgress.get()) {
            logger.fine("[extractMemories] 提取进行中 — stash 上下文供 trailing run");
            pendingContext.set(new PendingContext(messages, agentId));
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = runExtraction(messages, false)
            .whenComplete((v, ex) -> {
                if (ex != null) {
                    logger.fine("[extractMemories] 提取失败: " + ex.getMessage());
                }
            });

        inFlightExtractions.add(future);
        future.whenComplete((v, ex) -> inFlightExtractions.remove(future));

        return future;
    }

    /**
     * 等待所有进行中的提取完成（带超时）。
     * 对标 Claude Code 的 drainPendingExtraction()。
     */
    public void drain(long timeoutMs) {
        if (inFlightExtractions.isEmpty()) return;
        try {
            CompletableFuture.allOf(inFlightExtractions.toArray(new CompletableFuture[0]))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.fine("[extractMemories] drain 超时或中断");
        }
    }

    /**
     * 扫描记忆目录，返回所有记忆条目。
     */
    public List<MemoryEntry> scanMemories() {
        List<MemoryEntry> entries = new ArrayList<>();
        if (!Files.exists(memoryDir)) return entries;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(memoryDir, "*.md")) {
            for (Path path : stream) {
                if (path.getFileName().toString().equals("MEMORY.md")) continue;
                try {
                    String content = Files.readString(path);
                    MemoryEntry entry = MemoryEntry.parse(path, content);
                    try {
                        entry.lastModified = Files.getLastModifiedTime(path).toInstant();
                    } catch (IOException ignored) {}
                    entries.add(entry);
                } catch (IOException e) {
                    logger.fine("[extractMemories] 读取失败: " + path + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.warning("[extractMemories] 扫描记忆目录失败: " + e.getMessage());
        }
        return entries;
    }

    /**
     * 语义记忆检索 — 按相关性评分排序记忆条目，只返回最相关的 topK 条。
     * 对标 Claude Code 的 findRelevantMemories()。
     *
     * @param query 查询字符串（当前用户消息或任务描述）
     * @param topK  最多返回条数
     * @return 按 score 降序排列的评分记忆列表
     */
    public List<MemoryRelevanceScorer.ScoredMemory> findRelevantMemories(String query, int topK) {
        List<MemoryEntry> allEntries = scanMemories();
        MemoryRelevanceScorer scorer = new MemoryRelevanceScorer(embeddingService);
        return scorer.score(allEntries, query, topK);
    }

    /**
     * 语义记忆检索 — 使用默认 topK (5)。
     */
    public List<MemoryRelevanceScorer.ScoredMemory> findRelevantMemories(String query) {
        return findRelevantMemories(query, 5);
    }

    /**
     * 格式化记忆清单（用于注入 Fork Agent prompt）。
     * 对标 Claude Code 的 formatMemoryManifest()。
     */
    public String formatMemoryManifest() {
        List<MemoryEntry> entries = scanMemories();
        if (entries.isEmpty()) {
            return "(记忆目录为空)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前记忆文件清单:\n\n");
        for (MemoryEntry entry : entries) {
            sb.append("- [").append(entry.name).append("](")
              .append(entry.name).append(".md)");
            if (entry.description != null && !entry.description.isEmpty()) {
                sb.append(" — ").append(entry.description);
            }
            if (entry.type != null) {
                sb.append(" (类型: ").append(entry.type.value).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取记忆目录允许的写入工具权限。
     * 对标 Claude Code 的 createAutoMemCanUseTool()。
     */
    public Set<String> getMemoryDirPermissions() {
        return Set.of("Read", "Grep", "Glob", "FileEdit", "FileWrite");
    }

    /**
     * 检查路径是否在记忆目录下。
     */
    public boolean isInMemoryDir(Path path) {
        return path != null && path.normalize().toAbsolutePath()
            .startsWith(memoryDir.normalize().toAbsolutePath());
    }

    /**
     * 设置启用状态。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 设置 Fork Agent 执行器。
     */
    public void setForkAgentExecutor(ForkAgentExecutor executor) {
        this.forkAgentExecutor = executor;
    }

    /**
     * 设置嵌入服务（用于语义记忆检索）。
     */
    public void setEmbeddingService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    // ==================== 内部实现 ====================

    private CompletableFuture<Void> runExtraction(List<MessageStub> messages, boolean isTrailingRun) {
        inProgress.set(true);
        long startTime = System.currentTimeMillis();

        return CompletableFuture.runAsync(() -> {
            try {
                String lastUuid = lastProcessedUuid.get();
                List<MessageStub> newMessages = getNewMessages(messages, lastUuid);

                if (newMessages.isEmpty()) {
                    logger.fine("[extractMemories] 无新消息，跳过");
                    return;
                }

                // 互斥：主 Agent 已写 memory → 跳过
                if (hasMemoryWritesSince(messages, lastUuid)) {
                    logger.fine("[extractMemories] 跳过 — 对话已直接写入记忆文件");
                    advanceCursor(messages);
                    return;
                }

                String manifest = formatMemoryManifest();
                String prompt = buildExtractionPrompt(newMessages.size(), manifest);

                // 执行 Fork Agent
                List<String> results = forkAgentExecutor.execute(
                    buildSystemPrompt(),
                    prompt,
                    getMemoryDirPermissions(),
                    5 // maxTurns
                );

                // 提取写入的文件路径
                List<Path> writtenPaths = extractWrittenPaths(results);

                logger.info(String.format(
                    "[extractMemories] 完成 — %d 文件写入, %d 新消息, %dms",
                    writtenPaths.size(), newMessages.size(),
                    System.currentTimeMillis() - startTime
                ));

                // 更新 cursor
                advanceCursor(messages);

            } catch (Exception e) {
                logger.fine("[extractMemories] 错误: " + e.getMessage());
            } finally {
                inProgress.set(false);

                // Trailing extraction — 处理 stash 的上下文
                PendingContext trailing = pendingContext.getAndSet(null);
                if (trailing != null) {
                    logger.fine("[extractMemories] 执行 trailing extraction");
                    runExtraction(trailing.messages, true);
                }
            }
        });
    }

    private List<MessageStub> getNewMessages(List<MessageStub> messages, String sinceUuid) {
        if (sinceUuid == null) {
            return messages.stream()
                .filter(m -> "user".equals(m.role) || "assistant".equals(m.role))
                .collect(Collectors.toList());
        }

        boolean found = false;
        List<MessageStub> result = new ArrayList<>();
        for (MessageStub msg : messages) {
            if (!found) {
                if (msg.uuid.equals(sinceUuid)) found = true;
                continue;
            }
            if ("user".equals(msg.role) || "assistant".equals(msg.role)) {
                result.add(msg);
            }
        }

        // sinceUuid 未找到（可能被压缩移除），回退到全部
        if (!found) {
            return messages.stream()
                .filter(m -> "user".equals(m.role) || "assistant".equals(m.role))
                .collect(Collectors.toList());
        }
        return result;
    }

    private boolean hasMemoryWritesSince(List<MessageStub> messages, String sinceUuid) {
        boolean foundStart = sinceUuid == null;
        for (MessageStub msg : messages) {
            if (!foundStart) {
                if (msg.uuid.equals(sinceUuid)) foundStart = true;
                continue;
            }
            if (msg.hasMemoryWrite) return true;
        }
        return false;
    }

    private void advanceCursor(List<MessageStub> messages) {
        if (!messages.isEmpty()) {
            lastProcessedUuid.set(messages.get(messages.size() - 1).uuid);
        }
    }

    private List<Path> extractWrittenPaths(List<String> agentOutputs) {
        // 从 Agent 输出中解析 Write/Edit 的 file_path
        List<Path> paths = new ArrayList<>();
        for (String output : agentOutputs) {
            // 简化实现 — 生产代码应解析 JSON tool_use blocks
            if (output.contains("file_path")) {
                for (String line : output.split("\n")) {
                    if (line.contains("\"file_path\"") || line.contains("file_path:")) {
                        String path = extractFilePath(line);
                        if (path != null && isInMemoryDir(Path.of(path))) {
                            paths.add(Path.of(path));
                        }
                    }
                }
            }
        }
        return paths;
    }

    private String extractFilePath(String line) {
        // 简化提取 — 生产代码应使用 JSON 解析
        int start = line.indexOf("\"");
        if (start >= 0) {
            int end = line.indexOf("\"", start + 1);
            if (end > start) return line.substring(start + 1, end);
        }
        return null;
    }

    // ==================== Prompt 构建 ====================

    private String buildSystemPrompt() {
        return """
            # 自动记忆提取 Agent

            你是记忆提取专家。你的任务是从对话转录中提取持久记忆，写入记忆目录。

            ## 记忆类型

            仅限以下四种类型：
            - **user** — 用户的角色、偏好、知识背景
            - **feedback** — 用户对工作方式的纠正或确认
            - **project** — 项目目标、决策、约束（非代码可推导）
            - **reference** — 外部系统引用（Linear、Slack、Grafana 等）

            ## 不应保存的记忆

            - 代码模式、架构、文件路径 — 可从项目代码推导
            - Git 历史 — git log 是权威参考
            - 调试方案 — fix 在代码里，commit message 有上下文
            - 已在 CLAUDE.md 中的内容
            - 临时任务细节

            ## 操作规则

            1. 使用 Read 工具浏览现有记忆文件
            2. 使用 FileWrite 创建新记忆，使用 FileEdit 更新现有记忆
            3. 每个记忆文件使用 frontmatter 格式（name, description, type 字段）
            4. 更新 MEMORY.md 索引文件添加新条目
            5. 完成后停止，不继续对话
            """;
    }

    private String buildExtractionPrompt(int newMessageCount, String manifest) {
        return String.format("""
            分析最近的 %d 条新对话消息，提取值得持久化的记忆。

            %s

            ## 提取规则

            1. 只提取对话中明确透露的新信息
            2. 每个记忆 <= 1 个独立事实/偏好/引用
            3. 更新已有记忆而非创建重复条目
            4. 优先保存 user 和 feedback 类型的记忆
            5. 如无新记忆可提取，直接报告并停止

            ## 文件格式

            每个记忆文件使用以下 frontmatter：
            ```
            ---
            name: {{short-slug}}
            description: {{one-line summary}}
            type: {{user|feedback|project|reference}}
            ---

            {{memory body}}
            ```

            开始分析。""",
            newMessageCount, manifest);
    }
}
