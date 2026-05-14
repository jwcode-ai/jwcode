package com.jwcode.core.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * EmbeddingService — 嵌入向量生成服务。
 *
 * <p>调用 LLM API 的 embedding 端点（OpenAI 兼容：/v1/embeddings）生成向量。
 * 支持基于 contentHash 的本地缓存，避免重复计算。</p>
 *
 * <p>当 LLM API 不可用时，回退到基于关键词的 TF-IDF 风格稀疏向量。</p>
 */
public class EmbeddingService {

    private static final Logger logger = Logger.getLogger(EmbeddingService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** embedding API 端点 */
    private final String apiEndpoint;

    /** API Key */
    private final String apiKey;

    /** 模型名称 */
    private final String modelName;

    /** 向量维度 */
    private final int dimension;

    /** HTTP 客户端 */
    private final HttpClient httpClient;

    /** 嵌入缓存（contentHash → vector） */
    private final Map<String, float[]> cache;

    /** 是否可用 */
    private volatile boolean available;

    /** 最大批量大小 */
    private static final int MAX_BATCH_SIZE = 20;

    /**
     * @param apiEndpoint embedding API 端点（如 https://api.openai.com/v1/embeddings）
     * @param apiKey      API Key
     * @param modelName   模型名称（如 text-embedding-ada-002）
     * @param dimension   向量维度
     */
    public EmbeddingService(String apiEndpoint, String apiKey, String modelName, int dimension) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.dimension = dimension;
        this.httpClient = HttpClient.newHttpClient();
        this.cache = new ConcurrentHashMap<>();
        this.available = apiKey != null && !apiKey.isBlank() && apiEndpoint != null && !apiEndpoint.isBlank();

        if (available) {
            logger.info("EmbeddingService initialized: endpoint=" + apiEndpoint
                + ", model=" + modelName + ", dim=" + dimension);
        } else {
            logger.info("EmbeddingService: no API key configured, using fallback keyword vectors");
        }
    }

    /**
     * 创建禁用远程 API 的本地 fallback 实例
     */
    public static EmbeddingService createLocalFallback(int dimension) {
        return new EmbeddingService(null, null, "local-fallback", dimension);
    }

    // ==================== 单文本嵌入 ====================

    /**
     * 为单个文本生成嵌入向量
     *
     * @param text 文本内容
     * @return 嵌入向量
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }

        String hash = hashContent(text);

        // 检查缓存
        float[] cached = cache.get(hash);
        if (cached != null) {
            return cached;
        }

        float[] vector;
        if (available) {
            try {
                vector = callEmbeddingApi(List.of(text)).get(0);
            } catch (Exception e) {
                logger.warning("Embedding API call failed: " + e.getMessage() + ". Using fallback.");
                vector = fallbackEmbed(text);
            }
        } else {
            vector = fallbackEmbed(text);
        }

        cache.put(hash, vector);
        return vector;
    }

    /**
     * 批量生成嵌入向量
     *
     * @param texts 文本列表
     * @return 嵌入向量列表（与输入顺序一致）
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> results = new ArrayList<>(texts.size());
        List<String> toEmbed = new ArrayList<>();
        List<Integer> toEmbedIndices = new ArrayList<>();

        // 先查缓存
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.isBlank()) {
                results.add(new float[dimension]);
                continue;
            }
            String hash = hashContent(text);
            float[] cached = cache.get(hash);
            if (cached != null) {
                results.add(cached);
            } else {
                // 占位
                results.add(null);
                toEmbed.add(text);
                toEmbedIndices.add(i);
            }
        }

        if (toEmbed.isEmpty()) {
            return results;
        }

        // 分批调用 API
        for (int batchStart = 0; batchStart < toEmbed.size(); batchStart += MAX_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + MAX_BATCH_SIZE, toEmbed.size());
            List<String> batch = toEmbed.subList(batchStart, batchEnd);

            List<float[]> batchVectors;
            if (available) {
                try {
                    batchVectors = callEmbeddingApi(batch);
                } catch (Exception e) {
                    logger.warning("Embedding batch API failed: " + e.getMessage() + ". Using fallback.");
                    batchVectors = new ArrayList<>();
                    for (String text : batch) {
                        batchVectors.add(fallbackEmbed(text));
                    }
                }
            } else {
                batchVectors = new ArrayList<>();
                for (String text : batch) {
                    batchVectors.add(fallbackEmbed(text));
                }
            }

            // 回填结果并缓存
            for (int j = 0; j < batchVectors.size(); j++) {
                int originalIndex = toEmbedIndices.get(batchStart + j);
                float[] vec = batchVectors.get(j);
                results.set(originalIndex, vec);
                cache.put(hashContent(toEmbed.get(batchStart + j)), vec);
            }
        }

        return results;
    }

    // ==================== API 调用 ====================

    /**
     * 调用 embedding API
     */
    @SuppressWarnings("unchecked")
    private List<float[]> callEmbeddingApi(List<String> texts) throws Exception {
        // 构建请求体
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("input", texts);

        String json = MAPPER.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiEndpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Embedding API returned " + response.statusCode()
                + ": " + response.body());
        }

        // 解析响应
        Map<String, Object> respMap = MAPPER.readValue(response.body(),
            new TypeReference<Map<String, Object>>() {});

        List<Map<String, Object>> data = (List<Map<String, Object>>) respMap.get("data");
        if (data == null) {
            throw new RuntimeException("Embedding API response missing 'data' field");
        }

        // 按 index 排序（API 返回顺序可能不同）
        data.sort(Comparator.comparingInt(d -> ((Number) d.get("index")).intValue()));

        List<float[]> vectors = new ArrayList<>();
        for (Map<String, Object> item : data) {
            List<Double> embedding = (List<Double>) item.get("embedding");
            if (embedding == null) {
                throw new RuntimeException("Embedding API response missing 'embedding' for index "
                    + item.get("index"));
            }
            float[] vec = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vec[i] = embedding.get(i).floatValue();
            }
            vectors.add(vec);
        }

        return vectors;
    }

    // ==================== Fallback 嵌入 ====================

    /**
     * 本地 fallback：基于关键词的稀疏向量（TF-IDF 风格）
     *
     * <p>将文本拆分为词，用哈希映射到固定维度向量。
     * 虽然不如 LLM embedding 精确，但能提供基本的语义相关性。</p>
     */
    float[] fallbackEmbed(String text) {
        float[] vec = new float[dimension];
        if (text == null || text.isBlank()) return vec;

        String[] words = text.toLowerCase()
            .replaceAll("[^a-z0-9_\\u4e00-\\u9fff]", " ")
            .split("\\s+");

        for (String word : words) {
            if (word.length() < 2) continue;
            int bucket = Math.abs(word.hashCode() % dimension);
            vec[bucket] += 1.0f;
        }

        // L2 归一化
        float norm = 0;
        for (float v : vec) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] /= norm;
            }
        }

        return vec;
    }

    // ==================== 工具方法 ====================

    /**
     * 计算内容哈希（SHA-256 截前16位）
     */
    public static String hashContent(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(content.hashCode());
        }
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cache.clear();
        logger.fine("Embedding cache cleared");
    }

    /**
     * 检查是否可用
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 设置可用性
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getDimension() {
        return dimension;
    }
}
