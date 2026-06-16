package com.jwcode.core.llm.route;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Endpoint — LLM API 端点。
 *
 * <p>封装 base URL、路径和 HTTP 方法。
 * 这是 4 轴抽象的第二轴。
 *
 * @param baseUrl  基础 URL（如 {@code https://api.anthropic.com}）
 * @param path     路径（如 {@code /v1/messages}）
 * @param method   HTTP 方法（默认 POST）
 */
public record Endpoint(String baseUrl, String path, String method) {

    public Endpoint {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(path, "path must not be null");
        if (method == null || method.isEmpty()) method = "POST";
    }

    public Endpoint(String baseUrl, String path) {
        this(baseUrl, path, "POST");
    }

    /**
     * 获取完整 URI。
     */
    public URI toUri() {
        try {
            String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String p = path.startsWith("/") ? path : "/" + path;
            return new URI(url + p);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + baseUrl + path, e);
        }
    }

    /**
     * 派生新 endpoint（覆盖路径）。
     */
    public Endpoint withPath(String newPath) {
        return new Endpoint(baseUrl, newPath, method);
    }
}
