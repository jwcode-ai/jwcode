package com.jwcode.web.auth;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import org.java_websocket.WebSocket;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WebSocketAuthenticator — 处理 WebSocket 连接的认证逻辑。
 *
 * <p>从 {@code StreamingWebSocketHandler} 中提取，负责 token 验证和连接认证状态管理。
 * 支持从系统属性、环境变量、YAML 配置、token 文件加载 token。
 */
public class WebSocketAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(WebSocketAuthenticator.class.getName());

    /** 消息类型常量 */
    public static final String MSG_AUTH_SUCCESS = "auth_success";
    public static final String MSG_AUTH_FAILED = "auth_failed";
    public static final String MSG_AUTH_REQUIRED = "auth_required";

    /** 消息发送回调接口 */
    @FunctionalInterface
    public interface MessageSender {
        void send(WebSocket conn, String type, String payload);
    }

    private volatile String validToken = "default-token";
    private final Map<WebSocket, Boolean> authenticatedConnections = new ConcurrentHashMap<>();
    private final MessageSender messageSender;

    /**
     * @param messageSender (conn, type, payload) — 发送消息的回调
     */
    public WebSocketAuthenticator(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    /**
     * 从系统属性 → 环境变量 → YAML 配置 → 生成随机 token 的优先级加载 token。
     */
    public void loadToken() {
        // 1. 系统属性
        String tokenFromProp = System.getProperty("jwcode.websocket.token");
        if (tokenFromProp != null && !tokenFromProp.isEmpty()) {
            this.validToken = tokenFromProp;
            LOGGER.info("从系统属性加载 WebSocket token");
            return;
        }

        // 2. 环境变量
        String tokenFromEnv = System.getenv("JWCODE_WEBSOCKET_TOKEN");
        if (tokenFromEnv != null && !tokenFromEnv.isEmpty()) {
            this.validToken = tokenFromEnv;
            LOGGER.info("从环境变量加载 WebSocket token");
            return;
        }

        // 3. YAML 配置文件
        try {
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            if (config != null && config.getSettings() != null) {
                JwcodeConfig.AdvancedSettings advanced = config.getSettings().getAdvanced();
                if (advanced != null) {
                    String wsToken = null;
                    // 尝试从高级设置中获取 websocket token
                    if (advanced instanceof java.util.Map) {
                        wsToken = (String) ((java.util.Map<?, ?>) advanced).get("websocket-token");
                    }
                    if (wsToken != null && !wsToken.isEmpty()) {
                        this.validToken = wsToken;
                        LOGGER.info("从 YAML 配置加载 WebSocket token");
                        return;
                    }
                }
                // 兼容旧配置：从 providers 字段读取
                if (config.getProviders() != null && !config.getProviders().isEmpty()) {
                    String firstKey = config.getProviders().keySet().iterator().next();
                    JwcodeConfig.ProviderConfig firstProvider = config.getProviders().get(firstKey);
                    if (firstProvider != null && firstProvider.getApiKeys() != null
                        && !firstProvider.getApiKeys().isEmpty()) {
                        this.validToken = firstProvider.getApiKeys().get(0);
                        LOGGER.info("从第一个 provider 的 API key 生成 WebSocket token");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Cannot load token from YAML config: " + e.getMessage());
        }

        // 4. 生成随机 token 并保存到文件
        try {
            String generated = "jwcode_" + java.util.UUID.randomUUID().toString().replace("-", "");
            Path tokenFile = Path.of(System.getProperty("user.home"), ".jwcode", ".ws_token");
            java.nio.file.Files.createDirectories(tokenFile.getParent());
            java.nio.file.Files.writeString(tokenFile, generated);
            this.validToken = generated;
            LOGGER.info("已生成随机 WebSocket token 并保存至: " + tokenFile);
        } catch (Exception e) {
            LOGGER.warning("Cannot save WebSocket token file, using default: " + e.getMessage());
        }
    }

    /**
     * 获取当前有效 token（脱敏显示）。
     */
    public String getMaskedToken() {
        return maskToken(validToken);
    }

    /**
     * 检查连接是否已认证。
     */
    public boolean isAuthenticated(WebSocket conn) {
        Boolean authenticated = authenticatedConnections.get(conn);
        return authenticated != null && authenticated;
    }

    /**
     * 处理认证消息。
     *
     * @param conn  连接
     * @param token 客户端提供的 token
     */
    public void handleAuth(WebSocket conn, String token) {
        if (validToken == null || validToken.isEmpty()) {
            LOGGER.severe("认证失败: validToken 未配置");
            messageSender.send(conn, MSG_AUTH_FAILED, "Server configuration error: token not set");
            return;
        }

        if (token == null || token.isEmpty()) {
            LOGGER.warning("认证失败: token 为空");
            messageSender.send(conn, MSG_AUTH_FAILED, "Token is required");
            return;
        }

        if (validToken.equals(token)) {
            authenticatedConnections.put(conn, true);
            LOGGER.info("认证成功: " + conn.getRemoteSocketAddress());
            messageSender.send(conn, MSG_AUTH_SUCCESS, "Authenticated");
        } else {
            LOGGER.warning("认证失败: " + conn.getRemoteSocketAddress() + ", token=" + maskToken(token));
            messageSender.send(conn, MSG_AUTH_FAILED, "Invalid token");
        }
    }

    /**
     * 检查连接是否已认证，未认证时发送拒绝消息。
     *
     * @return true 如果已认证
     */
    public boolean checkAuthentication(WebSocket conn) {
        if (!isAuthenticated(conn)) {
            messageSender.send(conn, MSG_AUTH_REQUIRED, "Authentication required");
            return false;
        }
        return true;
    }

    /**
     * 连接关闭时清理认证状态。
     */
    public void removeConnection(WebSocket conn) {
        authenticatedConnections.remove(conn);
    }

    /**
     * 脱敏显示 token。
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
