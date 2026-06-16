package com.jwcode.core.llm.route;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Objects;

/**
 * Route — LLM API 路由（4 轴组合）。
 *
 * <p>将 {@link Protocol}, {@link Endpoint}, {@link Auth}, {@link Transport}
 * 组合为一个可执行的 API 路由定义。
 *
 * <p>使用方式：
 * <pre>{@code
 * Route route = Route.builder()
 *     .protocol(Protocol.ANTHROPIC_MESSAGES)
 *     .endpoint(new Endpoint("https://api.anthropic.com", "/v1/messages"))
 *     .auth(Auth.bearer("sk-ant-xxx"))
 *     .transport(Transport.HTTP_SSE)
 *     .timeout(Duration.ofSeconds(60))
 *     .build();
 * }</pre>
 */
public class Route {

    private final Protocol protocol;
    private final Endpoint endpoint;
    private final Auth auth;
    private final Transport transport;
    private final Duration timeout;

    private Route(Builder b) {
        this.protocol = Objects.requireNonNull(b.protocol, "protocol");
        this.endpoint = Objects.requireNonNull(b.endpoint, "endpoint");
        this.auth = b.auth != null ? b.auth : Auth.none();
        this.transport = b.transport != null ? b.transport : Transport.HTTP_JSON;
        this.timeout = b.timeout != null ? b.timeout : Duration.ofSeconds(60);
    }

    // ==================== Getters ====================

    public Protocol getProtocol() { return protocol; }
    public Endpoint getEndpoint() { return endpoint; }
    public Auth getAuth() { return auth; }
    public Transport getTransport() { return transport; }
    public Duration getTimeout() { return timeout; }

    // ==================== 快捷方法 ====================

    /**
     * 将认证信息应用到 HTTP 请求。
     */
    public HttpRequest.Builder applyAuth(HttpRequest.Builder builder) {
        auth.apply(builder);
        return builder;
    }

    /**
     * 构建完整的 HTTP 请求。
     *
     * @param body 请求体 JSON 字节
     * @return 构建好的 HttpRequest
     */
    public HttpRequest toHttpRequest(byte[] body) {
        URI uri = endpoint.toUri();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .header("Content-Type", "application/json")
            .header("Accept", transport == Transport.HTTP_SSE ? "text/event-stream" : "application/json")
            .method(endpoint.method(), HttpRequest.BodyPublishers.ofByteArray(body))
            .timeout(timeout);

        applyAuth(builder);
        return builder.build();
    }

    /**
     * 派生新 Route（仅覆盖协议）。
     */
    public Route withProtocol(Protocol p) {
        Builder b = toBuilder();
        b.protocol = p;
        return b.build();
    }

    /**
     * 派生新 Route（仅覆盖端点）。
     */
    public Route withEndpoint(Endpoint e) {
        Builder b = toBuilder();
        b.endpoint = e;
        return b.build();
    }

    // ==================== Builder ====================

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
            .protocol(this.protocol)
            .endpoint(this.endpoint)
            .auth(this.auth)
            .transport(this.transport)
            .timeout(this.timeout);
    }

    public static class Builder {
        private Protocol protocol;
        private Endpoint endpoint;
        private Auth auth;
        private Transport transport;
        private Duration timeout;

        public Builder protocol(Protocol p) { this.protocol = p; return this; }
        public Builder endpoint(Endpoint e) { this.endpoint = e; return this; }
        public Builder auth(Auth a) { this.auth = a; return this; }
        public Builder transport(Transport t) { this.transport = t; return this; }
        public Builder timeout(Duration t) { this.timeout = t; return this; }
        public Route build() { return new Route(this); }
    }

    @Override
    public String toString() {
        return "Route{" + protocol + " " + endpoint.toUri() + " via " + transport + "}";
    }
}
