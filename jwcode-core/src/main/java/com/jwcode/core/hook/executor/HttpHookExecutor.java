package com.jwcode.core.hook.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hook.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HttpHookExecutor — 基于 HTTP REST 端点的 Hook 执行器。
 *
 * <p>通过 HTTP POST 将 {@link HookContext} 发送到外部端点，
 * 解析 JSON 响应为 {@link HookResult}。</p>
 *
 * <h3>端点契约</h3>
 * <ul>
 *   <li><b>请求</b>：POST JSON（HookContext 序列化）</li>
 *   <li><b>响应</b>：200 OK + JSON（HookResult 结构）</li>
 *   <li><b>非200</b>：视为执行异常，按 fail-open/fail-closed 策略处理</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class HttpHookExecutor implements HookExecutor {

    private static final Logger logger = Logger.getLogger(HttpHookExecutor.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String url;
    private final HookPriority priority;
    private final long timeoutMs;
    private final boolean failOpen;
    private final boolean enabled;
    private final HttpClient httpClient;

    public HttpHookExecutor(String name, String url) {
        this(name, url, HookPriority.USER, 10_000, true, true);
    }

    public HttpHookExecutor(String name, String url,
                             HookPriority priority, long timeoutMs,
                             boolean failOpen, boolean enabled) {
        this.name = name;
        this.url = url;
        this.priority = priority;
        this.timeoutMs = timeoutMs;
        this.failOpen = failOpen;
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
    }

    public static HttpHookExecutor fromConfig(HookConfig config) {
        return new HttpHookExecutor(
            config.getName(),
            config.getUrl(),
            config.getPriority(),
            config.getTimeoutMs(),
            config.isFailOpen(),
            config.isEnabled()
        );
    }

    @Override
    public CompletableFuture<HookResult> execute(HookContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 构建 JSON 请求体
                String requestBody = MAPPER.writeValueAsString(context.toJson());

                // 2. 构建 HTTP 请求
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("X-Hook-Name", name)
                    .header("X-Event-Type", context.getEventType().name())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

                // 3. 发送请求
                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                // 4. 处理响应
                if (response.statusCode() == 200) {
                    return parseResult(response.body());
                } else {
                    logger.warning("[HttpHook] " + name + " returned HTTP " + response.statusCode()
                        + ": " + response.body());
                    return failOpen
                        ? HookResult.error(name, "HTTP " + response.statusCode())
                        : HookResult.errorFailClosed(name, "HTTP " + response.statusCode());
                }

            } catch (java.net.http.HttpTimeoutException e) {
                logger.warning("[HttpHook] " + name + " timed out after " + timeoutMs + "ms");
                return failOpen
                    ? HookResult.timeout(name)
                    : HookResult.deny(name, "HTTP hook timed out (fail-closed)");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[HttpHook] " + name + " request failed", e);
                return failOpen
                    ? HookResult.error(name, e.getMessage())
                    : HookResult.errorFailClosed(name, e.getMessage());
            }
        });
    }

    /**
     * 解析 HTTP 响应 JSON。
     */
    private HookResult parseResult(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);

            String decisionStr = root.has("decision")
                ? root.get("decision").asText().toUpperCase()
                : "ALLOW";

            HookDecision decision;
            try {
                decision = HookDecision.valueOf(decisionStr);
            } catch (IllegalArgumentException e) {
                decision = HookDecision.ALLOW;
            }

            String reason = root.has("reason") ? root.get("reason").asText() : "";

            HookResult.Builder builder = new HookResult.Builder(decision, name).reason(reason);

            if (root.has("modifiedInput")) builder.modifiedInput(root.get("modifiedInput"));
            if (root.has("askPayload")) builder.askPayload(root.get("askPayload").asText());
            if (root.has("deferToken")) builder.deferToken(root.get("deferToken").asText());

            return builder.build();
        } catch (Exception e) {
            logger.warning("[HttpHook] " + name + " failed to parse response: " + responseBody);
            return HookResult.allow(name, "Failed to parse HTTP response, defaulting to ALLOW");
        }
    }

    @Override
    public HookImplementationType getType() { return HookImplementationType.HTTP; }

    @Override
    public String getName() { return name; }

    @Override
    public HookPriority getPriority() { return priority; }

    @Override
    public long getTimeoutMs() { return timeoutMs; }

    @Override
    public boolean isFailOpen() { return failOpen; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String toString() {
        return String.format("HttpHookExecutor{name='%s', url='%s', priority=%s}", name, url, priority);
    }
}
