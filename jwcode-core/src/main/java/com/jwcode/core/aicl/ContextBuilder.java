package com.jwcode.core.aicl;

import java.util.HashMap;
import java.util.Map;

/**
 * ContextBuilder - 上下文构建器
 *
 * <p>使用 Builder 模式构建 AI 上下文信息。
 * 支持设置 ID、类型和负载数据，最终构建为 {@link BuiltContext} 对象。</p>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class ContextBuilder {

    private String id;
    private String type;
    private Map<String, Object> payload;

    /**
     * 构造函数
     */
    public ContextBuilder() {
        this.payload = new HashMap<>();
    }

    /**
     * 设置上下文 ID
     *
     * @param id 上下文 ID
     * @return this（链式调用）
     */
    public ContextBuilder withId(String id) {
        this.id = id;
        return this;
    }

    /**
     * 设置上下文类型
     *
     * @param type 上下文类型
     * @return this（链式调用）
     */
    public ContextBuilder withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * 设置上下文负载数据
     *
     * @param payload 负载数据
     * @return this（链式调用）
     */
    public ContextBuilder withPayload(Map<String, Object> payload) {
        this.payload = payload;
        return this;
    }

    /**
     * 构建上下文
     *
     * @return 构建好的上下文对象
     * @throws IllegalArgumentException 如果 ID 为空
     */
    public BuiltContext build() {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Context ID must not be empty");
        }
        return new BuiltContext(id, type, payload);
    }

    /**
     * BuiltContext - 构建后的上下文对象
     *
     * <p>不可变的上下文信息载体，包含 ID、类型和负载数据。</p>
     */
    public static class BuiltContext {
        private final String id;
        private final String type;
        private final Map<String, Object> payload;

        /**
         * 构造函数
         */
        public BuiltContext(String id, String type, Map<String, Object> payload) {
            this.id = id;
            this.type = type;
            this.payload = payload != null ? new HashMap<>(payload) : new HashMap<>();
        }

        /**
         * 获取上下文 ID
         *
         * @return 上下文 ID
         */
        public String getId() {
            return id;
        }

        /**
         * 获取上下文类型
         *
         * @return 上下文类型
         */
        public String getType() {
            return type;
        }

        /**
         * 获取上下文负载数据
         *
         * @return 负载数据
         */
        public Map<String, Object> getPayload() {
            return payload;
        }
    }
}
