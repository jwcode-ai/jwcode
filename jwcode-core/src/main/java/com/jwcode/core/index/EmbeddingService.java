package com.jwcode.core.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.config.JwcodeConfig;

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
 *
 * <p>v2：支持通过 {@link JwcodeConfig.ProviderConfig} 使用模型池，
 * 在 callEmbeddingApi 中动态获取 API Key，失败时自动轮换 key。</p>
 */
public class EmbeddingService {

    private static final Logger logger = Logger.getLogger(EmbeddingService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** embedding API 端点 */
    private final String apiEndpoint;

    /** API Key（单 key 模式，保留向后兼容） */
    private final String apiKey;

    /** 模型池配置（v2：多 key 轮询模式） */
    private final JwcodeConfig.ProviderConfig providerConfig;

    /** 是否使用模型池模式 */
    private final boolean useProviderPool;

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

    /** 使用模型池时的最大 key 重试次数 */
    private static final int MAX_KEY_RETRIES = 3;

    /**
     * @param apiEndpoint embedding API 端点（如 https://api.openai.com/v1/embeddings）
     * @param apiKey      API Key
     * @param modelName   模型名称（如 text-embedding-ada-002）
     * @param dimension   向量维度
     */
    public EmbeddingService(String apiEndpoint, String apiKey, String modelName, int dimension) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.providerConfig = null;
        this.useProviderPool = false;
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
     * 使用模型池（ProviderConfig）构造 EmbeddingService。
     * 每次调用 API 时从 providerConfig 动态获取 key，失败时自动轮换。
     *
     * @param providerConfig 提供商配置（含多 key 轮询）
     * @param apiEndpoint    embedding API 端点
     * @param modelName      模型名称
     * @param dimension      向量维度
     */
    public EmbeddingService(JwcodeConfig.ProviderConfig providerConfig, String apiEndpoint, String modelName, int dimension) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = null;
        this.providerConfig = providerConfig;
        this.useProviderPool = true;
        this.modelName = modelName;
        this.dimension = dimension;
        this.httpClient = HttpClient.newHttpClient();
        this.cache = new ConcurrentHashMap<>();
        this.available = providerConfig != null
            && !providerConfig.getApiKeys().isEmpty()
            && apiEndpoint != null && !apiEndpoint.isBlank();

        if (available) {
            logger.info("EmbeddingService initialized with provider pool: endpoint=" + apiEndpoint
                + ", model=" + modelName + ", dim=" + dimension
                + ", keys=" + providerConfig.getApiKeys().size());
        } else {
            logger.info("EmbeddingService: no API key configured, using fallback keyword vectors");
        }
    }

    /**
     * 创建禁用远程 API 的本地 fallback 实例
     */
    public static EmbeddingService createLocalFallback(int dimension) {
        // 使用显式 (String) 转型消除构造器重载歧义
        return new EmbeddingService((String) null, (String) null, "local-fallback", dimension);
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
            } catch (EmbeddingApiException e) {
                if (e.isPermanent()) {
                    logger.warning("Embedding API permanently unavailable (HTTP " + e.getStatusCode()
                        + "), disabling API. Using fallback.");
                    available = false;
                } else {
                    logger.warning("Embedding API call failed: " + e.getMessage() + ". Using fallback.");
                }
                vector = fallbackEmbed(text);
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
                } catch (EmbeddingApiException e) {
                    if (e.isPermanent()) {
                        logger.warning("Embedding batch API permanently unavailable (HTTP " + e.getStatusCode()
                            + "), disabling API. Using fallback.");
                        available = false;
                    } else {
                        logger.warning("Embedding batch API failed: " + e.getMessage() + ". Using fallback.");
                    }
                    batchVectors = new ArrayList<>();
                    for (String text : batch) {
                        batchVectors.add(fallbackEmbed(text));
                    }
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
     *
     * <p>如果使用模型池模式（useProviderPool=true），每次调用前从 providerConfig 获取 key，
     * 调用失败时标记 key 不可用并自动轮换重试。</p>
     */
    @SuppressWarnings("unchecked")
    private List<float[]> callEmbeddingApi(List<String> texts) throws Exception {
        if (useProviderPool && providerConfig != null) {
            return callEmbeddingApiWithPool(texts);
        }
        return callEmbeddingApiWithKey(texts, apiKey);
    }

    /**
     * 使用模型池模式调用 embedding API（多 key 轮询 + 失败重试）
     *
     * <p>404/405 等永久性错误不重试（端点不存在，换 key 也无效），直接抛异常让上层 fallback。</p>
     */
    private List<float[]> callEmbeddingApiWithPool(List<String> texts) throws Exception {
        Exception lastException = null;
        List<String> attemptedKeys = new ArrayList<>();
        for (int attempt = 0; attempt < MAX_KEY_RETRIES; attempt++) {
            String currentKey = providerConfig.getCurrentApiKey();
            if (currentKey == null) {
                logger.warning("Embedding API: no healthy API key available (attempt " + (attempt + 1) + "/" + MAX_KEY_RETRIES + ")");
                // 所有 key 都不可用，重置健康状态再试一次
                providerConfig.resetKeyHealth();
                currentKey = providerConfig.getCurrentApiKey();
                if (currentKey == null) {
                    break;
                }
            }

            // 避免同一轮中重复尝试同一个 key
            if (attemptedKeys.contains(currentKey)) {
                continue;
            }
            attemptedKeys.add(currentKey);

            try {
                return callEmbeddingApiWithKey(texts, currentKey);
            } catch (EmbeddingApiException e) {
                lastException = e;
                // 404/405 是永久性错误（端点不存在/方法不允许），换 key 无效，直接终止重试
                if (e.isPermanent()) {
                    logger.warning("Embedding API returned " + e.getStatusCode()
                        + " (permanent error), skipping key retries. "
                        + "Provider may not support embeddings.");
                    throw e;
                }
                // 401/403/429/5xx 是可重试错误，标记 key 并重试
                logger.warning("Embedding API call failed with key (attempt " + (attempt + 1) + "): "
                    + e.getMessage() + ". Marking key as failed and retrying...");
                providerConfig.markKeyFailed(currentKey);
            } catch (Exception e) {
                lastException = e;
                logger.warning("Embedding API call failed with key (attempt " + (attempt + 1) + "): "
                    + e.getMessage() + ". Marking key as failed and retrying...");
                providerConfig.markKeyFailed(currentKey);
            }
        }
        // 所有 key 都重试失败
        if (lastException != null) {
            throw lastException;
        }
        throw new RuntimeException("Embedding API failed after " + MAX_KEY_RETRIES
            + " key retries. All keys exhausted.");
    }

    /**
     * 使用指定 key 调用 embedding API
     *
     * @throws EmbeddingApiException 包含 HTTP 状态码，便于上层区分永久性/可重试错误
     */
    private List<float[]> callEmbeddingApiWithKey(List<String> texts, String key) throws Exception {
        // 构建请求体
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("input", texts);

        String json = MAPPER.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiEndpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + key)
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new EmbeddingApiException(response.statusCode(),
                "Embedding API returned " + response.statusCode()
                    + ": " + truncateBody(response.body()));
        }

        // 解析响应
        Map<String, Object> respMap = MAPPER.readValue(response.body(),
            new TypeReference<Map<String, Object>>() {});

        List<Map<String, Object>> data = (List<Map<String, Object>>) respMap.get("data");
        if (data == null) {
            throw new EmbeddingApiException(response.statusCode(),
                "Embedding API response missing 'data' field");
        }

        // 按 index 排序（API 返回顺序可能不同）
        data.sort(Comparator.comparingInt(d -> ((Number) d.get("index")).intValue()));

        List<float[]> vectors = new ArrayList<>();
        for (Map<String, Object> item : data) {
            List<Double> embedding = (List<Double>) item.get("embedding");
            if (embedding == null) {
                throw new EmbeddingApiException(response.statusCode(),
                    "Embedding API response missing 'embedding' for index "
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

    /**
     * 截断响应体，避免日志/异常信息过长
     */
    private static String truncateBody(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
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

    // ==================== 内部异常类 ====================

    /**
     * Embedding API 异常，携带 HTTP 状态码以便区分永久性/可重试错误。
     *
     * <p>永久性错误（404/405/400）：端点不存在、方法不允许、请求格式错误 — 换 key 无效。</p>
     * <p>可重试错误（401/403/429/5xx）：鉴权、限流、服务端临时故障 — 换 key 或重试可能有效。</p>
     */
    static class EmbeddingApiException extends RuntimeException {
        private final int statusCode;

        EmbeddingApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        int getStatusCode() {
            return statusCode;
        }

        /**
         * 是否为永久性错误（换 key / 重试无效）
         */
        boolean isPermanent() {
            // 404 Not Found — 端点不存在
            // 405 Method Not Allowed — HTTP 方法不对
            // 400 Bad Request — 请求格式错误（模型不支持等）
            return statusCode == 404 || statusCode == 405 || statusCode == 400;
        }
    }
}
