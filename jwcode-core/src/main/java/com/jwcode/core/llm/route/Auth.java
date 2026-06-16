package com.jwcode.core.llm.route;

import java.net.http.HttpRequest;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Auth — LLM API 认证策略。
 *
 * <p>支持多种认证方式并可通过 {@link #orElse(Auth)} 组合回退。
 * 这是 4 轴抽象的第三轴。
 *
 * <p>使用方式：
 * <pre>{@code
 * Auth auth = Auth.bearer(apiKey).orElse(Auth.env("FALLBACK_KEY"));
 * }</pre>
 */
public abstract class Auth {

    private Auth() {}

    /**
     * 将认证信息设置到 HTTP 请求头。
     *
     * @param builder 请求构建器
     */
    public abstract void apply(HttpRequest.Builder builder);

    /**
     * 获取当前认证头的值（用于日志，脱敏处理）。
     */
    public abstract String describe();

    /**
     * 检查认证是否可用（如环境变量是否存在、token 是否非空）。
     * 供 {@link FallbackAuth} 判断是否应回退。
     */
    public boolean isAvailable() { return true; }

    /**
     * 组合回退：当前 Auth 不可用时使用备用。
     */
    public Auth orElse(Auth fallback) {
        return new FallbackAuth(this, fallback);
    }

    // ==================== 实现 ====================

    /** 字面量 API Key (Bearer token)。 */
    public static Auth bearer(String token) {
        return new BearerAuth(Objects.requireNonNull(token));
    }

    /** 自定义头认证。 */
    public static Auth header(String headerName, String value) {
        return new HeaderAuth(headerName, value);
    }

    /** 从环境变量读取。 */
    public static Auth env(String envVar) {
        return new EnvAuth(envVar);
    }

    /** 从配置/系统属性读取。 */
    public static Auth config(String key) {
        return new ConfigAuth(key);
    }

    /** 可选认证（无认证时跳过）。 */
    public static Auth optional() {
        return OptionalAuth.INSTANCE;
    }

    /** 无认证。 */
    public static Auth none() {
        return NoneAuth.INSTANCE;
    }

    // ==================== 内部实现类 ====================

    private static class BearerAuth extends Auth {
        private final String token;
        BearerAuth(String token) { this.token = token; }
        @Override public void apply(HttpRequest.Builder b) {
            b.header("Authorization", "Bearer " + token);
        }
        @Override public String describe() {
            return "Bearer " + (token.length() > 8 ? token.substring(0, 4) + "..." : "***");
        }
    }

    private static class HeaderAuth extends Auth {
        private final String name, value;
        HeaderAuth(String name, String value) { this.name = name; this.value = value; }
        @Override public void apply(HttpRequest.Builder b) { b.header(name, value); }
        @Override public String describe() { return "Header " + name + "=***"; }
    }

    private static class EnvAuth extends Auth {
        private final String envVar;
        EnvAuth(String envVar) { this.envVar = envVar; }
        @Override public void apply(HttpRequest.Builder b) {
            String val = System.getenv(envVar);
            if (val != null && !val.isEmpty()) b.header("Authorization", "Bearer " + val);
        }
        @Override public boolean isAvailable() {
            String val = System.getenv(envVar);
            return val != null && !val.isEmpty();
        }
        @Override public String describe() { return "Env(" + envVar + ")"; }
    }

    private static class ConfigAuth extends Auth {
        private final String key;
        ConfigAuth(String key) { this.key = key; }
        @Override public void apply(HttpRequest.Builder b) {
            String val = System.getProperty(key);
            if (val != null && !val.isEmpty()) b.header("Authorization", "Bearer " + val);
        }
        @Override public boolean isAvailable() {
            String val = System.getProperty(key);
            return val != null && !val.isEmpty();
        }
        @Override public String describe() { return "Config(" + key + ")"; }
    }

    private static class FallbackAuth extends Auth {
        private final Auth primary, fallback;
        FallbackAuth(Auth primary, Auth fallback) { this.primary = primary; this.fallback = fallback; }
        @Override public void apply(HttpRequest.Builder b) {
            // 尝试 primary，如果不可用或有异常则用 fallback
            if (primary.isAvailable()) {
                try {
                    primary.apply(b);
                    return;
                } catch (Exception e) {
                    // 异常也回退
                }
            }
            fallback.apply(b);
        }
        @Override public String describe() {
            return primary.describe() + " || " + fallback.describe();
        }
    }

    private static class OptionalAuth extends Auth {
        static final OptionalAuth INSTANCE = new OptionalAuth();
        @Override public void apply(HttpRequest.Builder b) { /* skip */ }
        @Override public boolean isAvailable() { return false; }
        @Override public String describe() { return "optional"; }
    }

    private static class NoneAuth extends Auth {
        static final NoneAuth INSTANCE = new NoneAuth();
        @Override public void apply(HttpRequest.Builder b) { /* skip */ }
        @Override public boolean isAvailable() { return false; }
        @Override public String describe() { return "none"; }
    }
}
