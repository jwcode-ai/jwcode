package com.jwcode.core.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * VectorStore — 本地向量存储。
 *
 * <p>基于 JSON 文件的轻量级向量存储，支持余弦相似度 Top-K 搜索。
 * 存储结构：{@code .jwcode/index/vectors.json}</p>
 *
 * <p>核心操作：
 * <ul>
 *   <li>store — 存储向量 + 元数据</li>
 *   <li>search — 余弦相似度 Top-K 搜索</li>
 *   <li>delete — 按 chunkId 删除</li>
 *   <li>clear — 清空全部</li>
 * </ul>
 * </p>
 *
 * <p>后续可升级为 SQLite + faiss 方案。</p>
 */
public class VectorStore {

    private static final Logger logger = Logger.getLogger(VectorStore.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /** 向量文件路径 */
    private final Path vectorsFile;

    /** 内存中向量映射（chunkId → VectorEntry） */
    private final Map<String, VectorEntry> entries;

    /** 是否已从磁盘加载 */
    private volatile boolean loaded;

    public VectorStore(Path indexDir) {
        this.vectorsFile = indexDir.resolve("vectors.json");
        this.entries = new ConcurrentHashMap<>();
        this.loaded = false;
    }

    // ==================== 生命周期 ====================

    /**
     * 从磁盘加载向量数据
     */
    public synchronized void load() {
        if (loaded) return;

        try {
            if (Files.exists(vectorsFile) && Files.size(vectorsFile) > 0) {
                String json = Files.readString(vectorsFile);
                List<VectorEntry> loadedEntries = MAPPER.readValue(json,
                    new TypeReference<List<VectorEntry>>() {});

                for (VectorEntry entry : loadedEntries) {
                    entries.put(entry.chunkId, entry);
                }
                logger.info("VectorStore loaded " + entries.size() + " vectors from "
                    + vectorsFile);
            } else {
                logger.info("VectorStore: no existing vectors file, starting fresh");
            }
        } catch (IOException e) {
            logger.warning("Failed to load vectors: " + e.getMessage());
        }

        loaded = true;
    }

    /**
     * 持久化向量数据到磁盘
     */
    public synchronized void save() {
        try {
            Files.createDirectories(vectorsFile.getParent());
            List<VectorEntry> list = new ArrayList<>(entries.values());
            String json = MAPPER.writeValueAsString(list);
            Files.writeString(vectorsFile, json);
            logger.fine("VectorStore saved " + list.size() + " vectors to " + vectorsFile);
        } catch (IOException e) {
            logger.warning("Failed to save vectors: " + e.getMessage());
        }
    }

    // ==================== 存储操作 ====================

    /**
     * 存储一个向量
     *
     * @param chunkId  全局唯一块 ID
     * @param vector   嵌入向量
     * @param metadata 关联元数据（filePath, startLine, endLine 等）
     */
    public void store(String chunkId, float[] vector, Map<String, Object> metadata) {
        entries.put(chunkId, new VectorEntry(chunkId, vector, metadata));
    }

    /**
     * 批量存储
     */
    public void storeBatch(Map<String, float[]> vectors,
                           Map<String, Map<String, Object>> metadataMap) {
        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> meta = metadataMap != null
                ? metadataMap.getOrDefault(id, Collections.emptyMap())
                : Collections.emptyMap();
            store(id, entry.getValue(), meta);
        }
    }

    /**
     * 删除指定 chunkId 的向量
     */
    public void delete(String chunkId) {
        entries.remove(chunkId);
    }

    /**
     * 删除指定文件的所有向量
     */
    public void deleteByFile(String filePath) {
        entries.entrySet().removeIf(entry -> {
            Object fp = entry.getValue().metadata.get("filePath");
            return fp != null && fp.toString().equals(filePath);
        });
    }

    /**
     * 清空所有向量
     */
    public void clear() {
        entries.clear();
        logger.info("VectorStore cleared");
    }

    // ==================== 搜索操作 ====================

    /**
     * 余弦相似度 Top-K 搜索
     *
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 按相似度降序排列的搜索结果
     */
    public List<SearchResult> search(float[] queryVector, int topK) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算所有向量与查询向量的余弦相似度
        PriorityQueue<SearchResult> heap = new PriorityQueue<>(
            Comparator.comparingDouble(SearchResult::getSimilarity));

        for (VectorEntry entry : entries.values()) {
            if (entry.vector == null) continue;

            float similarity = cosineSimilarity(queryVector, entry.vector);

            if (heap.size() < topK) {
                heap.offer(new SearchResult(entry.chunkId, similarity, entry.metadata));
            } else if (similarity > heap.peek().getSimilarity()) {
                heap.poll();
                heap.offer(new SearchResult(entry.chunkId, similarity, entry.metadata));
            }
        }

        // 转为降序列表
        List<SearchResult> results = new ArrayList<>(heap);
        results.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        return results;
    }

    /**
     * 搜索并可按文件类型过滤
     */
    public List<SearchResult> search(float[] queryVector, int topK,
                                      Set<String> allowedExtensions) {
        List<SearchResult> results = search(queryVector, topK * 3); // 多召回一些再过滤

        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return results.subList(0, Math.min(topK, results.size()));
        }

        return results.stream()
            .filter(r -> {
                String fp = (String) r.getMetadata().get("filePath");
                if (fp == null) return false;
                String ext = IndexConfig.getFileExtension(fp);
                return allowedExtensions.contains(ext);
            })
            .limit(topK)
            .collect(Collectors.toList());
    }

    // ==================== 向量运算 ====================

    /**
     * 余弦相似度
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ==================== 状态查询 ====================

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 获取所有 chunkId
     */
    public Set<String> getAllChunkIds() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    // ==================== 内部类 ====================

    /**
     * 向量条目
     */
    static class VectorEntry {
        public String chunkId;
        public float[] vector;
        public Map<String, Object> metadata;

        VectorEntry() {}

        VectorEntry(String chunkId, float[] vector, Map<String, Object> metadata) {
            this.chunkId = chunkId;
            this.vector = vector;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }
    }

    /**
     * 搜索结果
     */
    public static class SearchResult {
        private final String chunkId;
        private final double similarity;
        private final Map<String, Object> metadata;

        public SearchResult(String chunkId, double similarity, Map<String, Object> metadata) {
            this.chunkId = chunkId;
            this.similarity = similarity;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }

        public String getChunkId() { return chunkId; }
        public double getSimilarity() { return similarity; }
        public Map<String, Object> getMetadata() { return metadata; }

        /** 便捷方法 */
        public String getFilePath() {
            Object fp = metadata.get("filePath");
            return fp != null ? fp.toString() : null;
        }

        public int getStartLine() {
            Object sl = metadata.get("startLine");
            return sl instanceof Number ? ((Number) sl).intValue() : 0;
        }

        public int getEndLine() {
            Object el = metadata.get("endLine");
            return el instanceof Number ? ((Number) el).intValue() : 0;
        }

        public String getChunkText() {
            Object ct = metadata.get("chunkText");
            return ct != null ? ct.toString() : "";
        }

        @Override
        public String toString() {
            return String.format("SearchResult{chunkId='%s', similarity=%.4f, file='%s', lines=%d-%d}",
                chunkId, similarity, getFilePath(), getStartLine(), getEndLine());
        }
    }
}
