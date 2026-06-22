package com.jwcode.core.llm.route;

import com.jwcode.core.llm.ServiceConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * RouteRegistry — Route 注册中心和配置映射器。
 *
 * <p>将现有的 {@link ServiceConfig} 映射到新的 {@link Route}。
 * 支持编程式注册和配置驱动注册。
 *
 * <p>这是向下兼容层：旧的 ServiceConfig → Route 转换，不需要重写现有代码。
 */
public class RouteRegistry {

    private static final Logger logger = Logger.getLogger(RouteRegistry.class.getName());

    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, String> modelToRoute = new ConcurrentHashMap<>();

    /**
     * 清空所有路由和模型映射。
     * 在配置热重载时使用，保证旧路由不会残留。
     */
    public void clear() {
        routes.clear();
        modelToRoute.clear();
        logger.info("[RouteRegistry] All routes cleared");
    }

    // ==================== 注册 ====================

    /**
     * 注册一个 Route。
     *
     * @param name  路由名称（如 "claude", "deepseek"）
     * @param route Route 定义
     */
    public void register(String name, Route route) {
        routes.put(name, route);
        logger.info("[RouteRegistry] Registered: " + name + " → " + route);
    }

    /**
     * 从 ServiceConfig 映射并注册 Route。
     */
    public Route registerFromConfig(String name, ServiceConfig config) {
        Protocol protocol = switch (config.getApiType()) {
            case "anthropic-messages" -> Protocol.ANTHROPIC_MESSAGES;
            case "openai-completions" -> Protocol.OPENAI_COMPATIBLE_CHAT;
            default -> Protocol.OPENAI_COMPATIBLE_CHAT;
        };

        // 剥离 baseUrl 中的路径段，避免与 endpoint path 重复（如 /v1/v1/chat/completions）
        String baseUrl = config.getBaseUrl();
        String cleanBaseUrl = baseUrl;
        try {
            URI uri = new URI(baseUrl);
            if (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) {
                cleanBaseUrl = new URI(uri.getScheme(), uri.getAuthority(), null, null, null).toString();
            }
        } catch (URISyntaxException e) {
            logger.warning("Invalid baseUrl: " + baseUrl + " — " + e.getMessage());
        }

        Endpoint endpoint = new Endpoint(cleanBaseUrl, "/v1/messages");
        if (protocol == Protocol.OPENAI_COMPATIBLE_CHAT) {
            endpoint = new Endpoint(cleanBaseUrl, "/v1/chat/completions");
        }

        // 从 config 构建 Auth
        List<String> keys = config.getApiKeys();
        Auth auth;
        if (keys == null || keys.isEmpty()) {
            auth = Auth.optional();
        } else if (keys.size() == 1) {
            auth = Auth.bearer(keys.get(0));
        } else {
            // 多 key → 第一个 bearer，回退到第二个
            auth = Auth.bearer(keys.get(0));
            for (int i = 1; i < keys.size(); i++) {
                final String fallbackKey = keys.get(i);
                auth = auth.orElse(Auth.bearer(fallbackKey));
            }
        }

        Route route = Route.builder()
            .protocol(protocol)
            .endpoint(endpoint)
            .auth(auth)
            .transport(Transport.HTTP_SSE)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .build();

        register(name, route);

        // 注册 model → route 映射
        if (config.getModel() != null && !config.getModel().isEmpty()) {
            for (String modelId : config.getModel().split(",")) {
                modelToRoute.put(modelId.trim(), name);
            }
        }

        return route;
    }

    // ==================== 查询 ====================

    /**
     * 按名称查找 Route。
     */
    public Optional<Route> getRoute(String name) {
        return Optional.ofNullable(routes.get(name));
    }

    /**
     * 通过模型 ID 查找 Route。
     */
    public Optional<Route> getRouteForModel(String modelId) {
        String routeName = modelToRoute.get(modelId);
        if (routeName == null) return Optional.empty();
        return getRoute(routeName);
    }

    /**
     * 获取所有已注册路由。
     */
    public Map<String, Route> getAllRoutes() {
        return Collections.unmodifiableMap(routes);
    }

    /**
     * 获取所有已注册的模型映射。
     */
    public Map<String, String> getModelMappings() {
        return Collections.unmodifiableMap(modelToRoute);
    }
}
