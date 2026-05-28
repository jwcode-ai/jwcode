package com.jwcode.core.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CodebaseIndexer — 代码库索引器。
 *
 * <p>负责工作区文件的扫描、分块、嵌入和增量更新。提供语义搜索入口。
 * 索引数据存储在 {@code .jwcode/index/} 目录下。</p>
 *
 * <p>核心流程：
 * <ol>
 *   <li>初始扫描：遍历工作区，过滤文件 → 分块 → 生成嵌入 → 存储向量</li>
 *   <li>增量更新：FileWatcher 监听文件变更 → 重新索引变更文件</li>
 *   <li>语义搜索：query → embed → VectorStore.search() → 格式化结果</li>
 * </ol>
 * </p>
 */
public class CodebaseIndexer {

    private static final Logger logger = Logger.getLogger(CodebaseIndexer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private Path workspaceRoot;
    private IndexConfig config;
    private final EmbeddingService embeddingService;
    private VectorStore vectorStore;

    /** 索引元信息文件 */
    private Path statusFile;

    /** 文件级索引条目存储目录 */
    private Path filesDir;

    /** 索引元信息 */
    private volatile IndexStatus status;

    /** 索引是否正在进行中 */
    private volatile boolean indexing;

    /** 文件监控服务 */
    private WatchService watchService;
    private volatile boolean watching;

    /** 索引线程池 */
    private final ExecutorService indexExecutor;

    /** 防抖 — 待处理的文件变更 */
    private final Map<String, ScheduledFuture<?>> pendingChanges;

    /** 防抖调度器 */
    private final ScheduledExecutorService debounceScheduler;

    /** 符号提取正则（匹配类/接口/方法/函数定义） */
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(
        "(?:class|interface|enum|@interface|record|def|fn|fun|func|function|" +
        "public\\s+(?:static\\s+)?(?:void|\\w+(?:<[^>]+>)?)\\s+|" +
        "private\\s+(?:static\\s+)?(?:void|\\w+(?:<[^>]+>)?)\\s+|" +
        "protected\\s+(?:static\\s+)?(?:void|\\w+(?:<[^>]+>)?)\\s+)" +
        "\\s*(\\w+)",
        Pattern.MULTILINE
    );

    public CodebaseIndexer(Path workspaceRoot, IndexConfig config,
                           EmbeddingService embeddingService) {
        this.workspaceRoot = workspaceRoot.normalize().toAbsolutePath();
        this.config = config != null ? config : IndexConfig.forWorkspace(workspaceRoot);
        this.embeddingService = embeddingService;
        this.vectorStore = new VectorStore(this.config.getIndexDir());
        this.statusFile = this.config.getIndexDir().resolve("status.json");
        this.filesDir = this.config.getIndexDir().resolve("files");
        this.indexing = false;
        this.watching = false;
        this.indexExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "codebase-indexer");
            t.setDaemon(true);
            return t;
        });
        this.pendingChanges = new ConcurrentHashMap<>();
        this.debounceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "indexer-debounce");
            t.setDaemon(true);
            return t;
        });

        initDirectories();
    }

    private void initDirectories() {
        try {
            Files.createDirectories(config.getIndexDir());
            Files.createDirectories(filesDir);
            vectorStore.load();
            loadStatus();
        } catch (IOException e) {
            logger.warning("Failed to init index directories: " + e.getMessage());
        }
    }

    // ==================== 索引状态 ====================

    static class IndexStatus {
        public long lastFullIndexTime;
        public int totalFiles;
        public int totalChunks;
        public String workspaceRoot;

        IndexStatus() {}

        IndexStatus(String workspaceRoot) {
            this.workspaceRoot = workspaceRoot;
            this.lastFullIndexTime = 0;
            this.totalFiles = 0;
            this.totalChunks = 0;
        }
    }

    private void loadStatus() {
        try {
            if (Files.exists(statusFile) && Files.size(statusFile) > 0) {
                status = MAPPER.readValue(statusFile.toFile(), IndexStatus.class);
            } else {
                status = new IndexStatus(workspaceRoot.toString());
            }
        } catch (IOException e) {
            status = new IndexStatus(workspaceRoot.toString());
        }
    }

    private void saveStatus() {
        try {
            MAPPER.writeValue(statusFile.toFile(), status);
        } catch (IOException e) {
            logger.warning("Failed to save index status: " + e.getMessage());
        }
    }

    public IndexStatus getStatus() {
        return status;
    }

    // ==================== 全量索引 ====================

    /**
     * 执行全量索引（同步，阻塞直到完成）
     *
     * @return 索引的文件数
     */
    public int reindex() {
        if (indexing) {
            logger.warning("Index already in progress, skipping reindex");
            return 0;
        }

        indexing = true;
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Starting full reindex of workspace: " + workspaceRoot);
            vectorStore.clear();
            clearFileEntries();

            List<Path> files = scanWorkspace();
            logger.info("Found " + files.size() + " indexable files");

            int indexedCount = 0;
            int totalChunks = 0;

            // 逐文件索引
            List<String> allChunkTexts = new ArrayList<>();
            List<String> allChunkIds = new ArrayList<>();
            List<FileIndexEntry> allEntries = new ArrayList<>();

            for (Path file : files) {
                try {
                    FileIndexEntry entry = buildFileEntry(file);
                    if (entry == null || entry.getChunks() == null || entry.getChunks().isEmpty()) {
                        continue;
                    }

                    // 收集所有需要 embedding 的 chunk
                    for (FileIndexEntry.Chunk chunk : entry.getChunks()) {
                        allChunkTexts.add(chunk.getText());
                        allChunkIds.add(chunk.getChunkId());
                    }

                    allEntries.add(entry);
                    indexedCount++;
                } catch (Exception e) {
                    logger.fine("Failed to index file " + file + ": " + e.getMessage());
                }
            }

            // 批量生成嵌入向量
            if (!allChunkTexts.isEmpty()) {
                logger.info("Generating embeddings for " + allChunkTexts.size() + " chunks...");
                List<float[]> embeddings = embeddingService.embedBatch(allChunkTexts);

                // 存储向量 + 元数据
                for (int i = 0; i < allChunkIds.size(); i++) {
                    String chunkId = allChunkIds.get(i);
                    float[] vec = embeddings.get(i);
                    String chunkText = allChunkTexts.get(i);

                    // 构建元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("chunkText", chunkText);

                    // 找到所属的文件条目
                    for (FileIndexEntry entry : allEntries) {
                        if (entry.getChunks() != null) {
                            for (FileIndexEntry.Chunk chunk : entry.getChunks()) {
                                if (chunk.getChunkId().equals(chunkId)) {
                                    metadata.put("filePath", entry.getRelativePath());
                                    metadata.put("startLine", chunk.getStartLine());
                                    metadata.put("endLine", chunk.getEndLine());
                                    metadata.put("language", entry.getLanguage());
                                    chunk.setEmbedding(vec);
                                    break;
                                }
                            }
                        }
                    }

                    vectorStore.store(chunkId, vec, metadata);
                    totalChunks++;
                }

                vectorStore.save();
            }

            // 保存文件条目
            for (FileIndexEntry entry : allEntries) {
                saveFileEntry(entry);
            }

            // 更新状态
            status.lastFullIndexTime = System.currentTimeMillis();
            status.totalFiles = indexedCount;
            status.totalChunks = totalChunks;
            status.workspaceRoot = workspaceRoot.toString();
            saveStatus();

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Reindex complete: " + indexedCount + " files, "
                + totalChunks + " chunks, " + elapsed + "ms");

            return indexedCount;

        } catch (Exception e) {
            logger.severe("Reindex failed: " + e.getMessage());
            return 0;
        } finally {
            indexing = false;
        }
    }

    /**
     * 异步执行全量索引
     */
    public CompletableFuture<Integer> reindexAsync() {
        return CompletableFuture.supplyAsync(this::reindex, indexExecutor)
            .exceptionally(e -> {
                logger.warning("Async reindex failed: " + e.getMessage());
                return 0;
            });
    }

    // ==================== 增量索引 ====================

    /**
     * 索引单个文件（新增或更新）
     */
    public void indexFile(Path filePath) {
        if (!config.shouldIndex(filePath)) {
            return;
        }

        try {
            Path relative = workspaceRoot.relativize(filePath);
            String relPath = relative.toString().replace('\\', '/');

            // 删除旧索引
            vectorStore.deleteByFile(relPath);
            deleteFileEntry(relPath);

            // 重新索引
            FileIndexEntry entry = buildFileEntry(filePath);
            if (entry == null) return;

            // 生成嵌入
            if (entry.getChunks() != null) {
                for (FileIndexEntry.Chunk chunk : entry.getChunks()) {
                    try {
                        float[] vec = embeddingService.embed(chunk.getText());
                        chunk.setEmbedding(vec);

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("filePath", relPath);
                        metadata.put("startLine", chunk.getStartLine());
                        metadata.put("endLine", chunk.getEndLine());
                        metadata.put("language", entry.getLanguage());
                        metadata.put("chunkText", chunk.getText());

                        vectorStore.store(chunk.getChunkId(), vec, metadata);
                    } catch (Exception e) {
                        logger.fine("Failed to embed chunk " + chunk.getChunkId()
                            + ": " + e.getMessage());
                    }
                }
                vectorStore.save();
            }

            saveFileEntry(entry);
            logger.fine("Indexed: " + relPath);

        } catch (Exception e) {
            logger.fine("Failed to index file " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * 从索引中移除文件
     */
    public void removeFile(Path filePath) {
        try {
            Path relative = workspaceRoot.relativize(filePath);
            String relPath = relative.toString().replace('\\', '/');
            vectorStore.deleteByFile(relPath);
            deleteFileEntry(relPath);
            vectorStore.save();
            logger.fine("Removed from index: " + relPath);
        } catch (Exception e) {
            logger.fine("Failed to remove file from index: " + filePath);
        }
    }

    // ==================== 语义搜索 ====================

    /**
     * 语义搜索
     *
     * @param query     自然语言查询
     * @param topK      返回数量
     * @param fileTypes 文件扩展名过滤（可选）
     * @return 搜索结果
     */
    public List<VectorStore.SearchResult> search(String query, int topK,
                                                  Set<String> fileTypes) {
        if (vectorStore.isEmpty()) {
            logger.fine("Vector store is empty, returning empty results");
            return Collections.emptyList();
        }

        float[] queryVector = embeddingService.embed(query);

        if (fileTypes != null && !fileTypes.isEmpty()) {
            return vectorStore.search(queryVector, topK, fileTypes);
        }

        return vectorStore.search(queryVector, topK);
    }

    /**
     * 语义搜索（无文件类型过滤）
     */
    public List<VectorStore.SearchResult> search(String query, int topK) {
        return search(query, topK, null);
    }

    // ==================== 文件监控 ====================

    /**
     * 启动文件监控（增量更新）
     */
    public void startWatching() {
        if (watching) return;

        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerWatchRecursive(workspaceRoot);

            Thread watchThread = new Thread(this::watchLoop, "indexer-watcher");
            watchThread.setDaemon(true);
            watchThread.start();

            watching = true;
            logger.info("File watcher started for: " + workspaceRoot);
        } catch (IOException e) {
            logger.warning("Failed to start file watcher: " + e.getMessage());
        }
    }

    /**
     * 停止文件监控
     */
    public void stopWatching() {
        watching = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void registerWatchRecursive(Path dir) throws IOException {
        // 跳过排除目录
        if (dir.getFileName() != null
            && config.getExcludeDirs().contains(dir.getFileName().toString())) {
            return;
        }

        dir.register(watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);

        try {
            File[] children = dir.toFile().listFiles(File::isDirectory);
            if (children != null) {
                for (File child : children) {
                    registerWatchRecursive(child.toPath());
                }
            }
        } catch (IOException e) {
            // skip inaccessible directories
        }
    }

    private void watchLoop() {
        while (watching) {
            try {
                WatchKey key = watchService.poll(5, TimeUnit.SECONDS);
                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Path dir = (Path) key.watchable();
                    Path fileName = (Path) event.context();
                    Path fullPath = dir.resolve(fileName);

                    // 防抖处理
                    String pathKey = fullPath.toString();
                    ScheduledFuture<?> existing = pendingChanges.remove(pathKey);
                    if (existing != null) {
                        existing.cancel(false);
                    }

                    pendingChanges.put(pathKey,
                        debounceScheduler.schedule(() -> {
                            pendingChanges.remove(pathKey);
                            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                removeFile(fullPath);
                            } else if (Files.exists(fullPath) && !Files.isDirectory(fullPath)) {
                                indexFile(fullPath);
                            }
                        }, config.getFileWatchDebounceMs(), TimeUnit.MILLISECONDS));
                }

                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.fine("File watcher error: " + e.getMessage());
            }
        }
    }

    // ==================== 文件扫描 ====================

    /**
     * 扫描工作区，返回应索引的文件列表
     */
    List<Path> scanWorkspace() throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                if (config.getExcludeDirs().contains(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (config.shouldIndex(file) && attrs.size() <= config.getMaxFileSizeBytes()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    // ==================== 文件条目构建 ====================

    /**
     * 为单个文件构建索引条目
     */
    private FileIndexEntry buildFileEntry(Path filePath) throws IOException {
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            return null;
        }

        Path relative = workspaceRoot.relativize(filePath);
        String relPath = relative.toString().replace('\\', '/');
        long fileSize = Files.size(filePath);

        if (fileSize > config.getMaxFileSizeBytes()) {
            return null;
        }

        String content = Files.readString(filePath);
        if (content.isBlank()) return null;

        String language = FileIndexEntry.inferLanguage(filePath);
        String contentHash = EmbeddingService.hashContent(content);

        FileIndexEntry entry = new FileIndexEntry(
            relPath, language, fileSize, contentHash,
            Files.getLastModifiedTime(filePath).toInstant());

        // 提取符号
        entry.setSymbols(extractSymbols(content, language));

        // 分块
        entry.setChunks(chunkContent(content, relPath, config.getMaxChunkChars()));

        return entry;
    }

    /**
     * 提取代码符号（类名、方法名等）
     */
    List<String> extractSymbols(String content, String language) {
        List<String> symbols = new ArrayList<>();
        Matcher matcher = SYMBOL_PATTERN.matcher(content);
        while (matcher.find()) {
            String symbol = matcher.group(1);
            if (symbol != null && !symbol.isEmpty()
                && !isKeyword(symbol, language)) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    private boolean isKeyword(String word, String language) {
        Set<String> keywords = switch (language.toLowerCase()) {
            case "java" -> Set.of("new", "return", "if", "else", "for", "while",
                "try", "catch", "throw", "throws", "import", "package", "null",
                "true", "false", "this", "super", "synchronized", "volatile",
                "transient", "native", "strictfp", "const", "goto");
            case "typescript", "javascript" -> Set.of("new", "return", "if", "else",
                "for", "while", "try", "catch", "throw", "import", "export",
                "null", "true", "false", "this", "super", "typeof", "instanceof");
            case "python" -> Set.of("def", "return", "if", "elif", "else", "for",
                "while", "try", "except", "import", "from", "None", "True", "False");
            default -> Set.of();
        };
        return keywords.contains(word);
    }

    /**
     * 将文件内容切分为块
     */
    List<FileIndexEntry.Chunk> chunkContent(String content, String filePath,
                                             int maxChars) {
        List<FileIndexEntry.Chunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        StringBuilder currentChunk = new StringBuilder();
        int chunkStartLine = 1;
        int currentLine = 1;
        int chunkIndex = 0;

        for (String line : lines) {
            // 按自然边界切分（空行优先）
            boolean shouldSplit = currentChunk.length() + line.length() > maxChars
                && currentChunk.length() > maxChars / 2;

            if (shouldSplit && !currentChunk.isEmpty()) {
                String chunkId = filePath + "#chunk" + chunkIndex;
                chunks.add(new FileIndexEntry.Chunk(
                    chunkId, currentChunk.toString().trim(),
                    chunkStartLine, currentLine - 1));
                currentChunk = new StringBuilder();
                chunkStartLine = currentLine;
                chunkIndex++;
            }

            if (!currentChunk.isEmpty()) {
                currentChunk.append('\n');
            }
            currentChunk.append(line);
            currentLine++;
        }

        // 最后一个块
        if (!currentChunk.isEmpty()) {
            String chunkId = filePath + "#chunk" + chunkIndex;
            chunks.add(new FileIndexEntry.Chunk(
                chunkId, currentChunk.toString().trim(),
                chunkStartLine, currentLine - 1));
        }

        return chunks;
    }

    // ==================== 文件条目持久化 ====================

    private void saveFileEntry(FileIndexEntry entry) {
        try {
            String fileName = entry.getRelativePath().replace('/', '_').replace('\\', '_') + ".json";
            Path filePath = filesDir.resolve(fileName);
            Files.createDirectories(filePath.getParent());
            MAPPER.writeValue(filePath.toFile(), entry);
        } catch (IOException e) {
            logger.fine("Failed to save file entry: " + entry.getRelativePath());
        }
    }

    private void deleteFileEntry(String relativePath) {
        try {
            String fileName = relativePath.replace('/', '_').replace('\\', '_') + ".json";
            Path filePath = filesDir.resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // ignore
        }
    }

    private void clearFileEntries() {
        try {
            if (Files.exists(filesDir)) {
                try (var stream = Files.list(filesDir)) {
                    stream.forEach(f -> {
                        try { Files.deleteIfExists(f); }
                        catch (IOException ignored) {}
                    });
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to clear file entries: " + e.getMessage());
        }
    }

    // ==================== 工作区切换 ====================

    /**
     * 切换到新的工作区，停止旧的文件监控，清除旧索引，重新索引新工作区。
     *
     * <p>此方法是线程安全的，会等待当前索引任务完成后再切换。</p>
     *
     * @param newWorkspaceRoot 新的工作区根路径
     */
    public synchronized void switchWorkspace(Path newWorkspaceRoot) {
        Path newRoot = newWorkspaceRoot.normalize().toAbsolutePath();
        if (newRoot.equals(this.workspaceRoot)) {
            logger.info("switchWorkspace: 新工作区与当前相同，跳过");
            return;
        }
        
        // 防重入：如果索引正在进行中，等待完成
        if (indexing) {
            logger.info("switchWorkspace: 索引正在进行中，等待完成后再切换...");
            while (indexing) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.info("switchWorkspace: " + this.workspaceRoot + " → " + newRoot);

        // 1. 停止文件监控
        stopWatching();

        // 2. 等待当前索引完成
        while (indexing) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 3. 保存旧状态
        saveStatus();

        // 4. 切换到新工作区
        this.workspaceRoot = newRoot;
        this.config = IndexConfig.forWorkspace(newRoot);
        this.vectorStore = new VectorStore(this.config.getIndexDir());
        this.statusFile = this.config.getIndexDir().resolve("status.json");
        this.filesDir = this.config.getIndexDir().resolve("files");

        // 5. 初始化新目录和状态
        initDirectories();

        // 6. 异步全量索引
        reindexAsync()
            .thenAccept(fileCount -> {
                if (fileCount > 0) {
                    logger.info("工作区切换后索引完成: " + fileCount + " 个文件, "
                        + getVectorCount() + " 个向量块");
                } else {
                    logger.info("工作区切换后索引完成（无新增文件）");
                }
            })
            .exceptionally(e -> {
                logger.warning("工作区切换后索引失败: " + e.getMessage());
                return null;
            });

        // 7. 重新启动文件监控
        startWatching();

        logger.info("switchWorkspace: 切换完成，后台索引中...");
    }

    // ==================== 关闭 ====================

    public void shutdown() {
        stopWatching();
        indexExecutor.shutdown();
        debounceScheduler.shutdown();
        try {
            if (!indexExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                indexExecutor.shutdownNow();
            }
            if (!debounceScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                debounceScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            indexExecutor.shutdownNow();
            debounceScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("CodebaseIndexer shutdown complete");
    }

    // ==================== Getters ====================

    public boolean isIndexing() { return indexing; }
    public boolean isWatching() { return watching; }
    public int getVectorCount() { return vectorStore.size(); }
    public Path getWorkspaceRoot() { return workspaceRoot; }
    public IndexConfig getConfig() { return config; }
    public EmbeddingService getEmbeddingService() { return embeddingService; }
}
