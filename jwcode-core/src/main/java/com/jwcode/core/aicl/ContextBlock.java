package com.jwcode.core.aicl;

import java.time.Instant;
import java.util.*;

/**
 * AICL 上下文块 — 对应 {@code <ctx:block>} 元素。
 *
 * <p>将现有 {@code StructuredMessage} 的概念提升为带有优先级、生命周期、
 * TTL 的独立块。每个块包含 label、abstract、content、summary 等子元素。</p>
 *
 * <pre>
 * &lt;ctx:block id="hist" type="history" role="dialogue"
 *            priority="high" format="markdown" state="active"
 *            ttl="5" last-access="1715515800" access-count="3" generation="0"&gt;
 *   &lt;ctx:label&gt;对话历史&lt;/ctx:label&gt;
 *   &lt;ctx:abstract&gt;摘要描述&lt;/ctx:abstract&gt;
 *   &lt;ctx:content&gt;...&lt;/ctx:content&gt;
 * &lt;/ctx:block&gt;
 * </pre>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class ContextBlock {
    // ===== 核心标识 =====
    private final String id;
    private String type;
    private String role;
    private BlockPriority priority;
    private String format;

    // ===== 生命周期字段 =====
    private BlockLifecycle state;
    private int ttl;
    private long lastAccess;
    private int accessCount;
    private int generation;

    // ===== 内容 =====
    private String label;
    private String blockAbstract;
    private String content;
    private String summary;
    private long estimatedTokens;

    // ===== 元数据 =====
    private final Instant createdAt;
    private Instant updatedAt;
    private final Map<String, String> attributes;

    // ===== 构造函数 =====

    private ContextBlock(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.type = builder.type;
        this.role = builder.role;
        this.priority = builder.priority;
        this.format = builder.format;
        this.state = builder.state;
        this.ttl = builder.ttl;
        this.lastAccess = builder.lastAccess;
        this.accessCount = builder.accessCount;
        this.generation = builder.generation;
        this.label = builder.label;
        this.blockAbstract = builder.blockAbstract;
        this.content = builder.content;
        this.summary = builder.summary;
        this.estimatedTokens = builder.estimatedTokens;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.attributes = new LinkedHashMap<>(builder.attributes);
    }

    /** 复制构造（用于状态变更后创建新实例） */
    public ContextBlock(ContextBlock source) {
        this.id = source.id;
        this.type = source.type;
        this.role = source.role;
        this.priority = source.priority;
        this.format = source.format;
        this.state = source.state;
        this.ttl = source.ttl;
        this.lastAccess = source.lastAccess;
        this.accessCount = source.accessCount;
        this.generation = source.generation;
        this.label = source.label;
        this.blockAbstract = source.blockAbstract;
        this.content = source.content;
        this.summary = source.summary;
        this.estimatedTokens = source.estimatedTokens;
        this.createdAt = source.createdAt;
        this.updatedAt = Instant.now();
        this.attributes = new LinkedHashMap<>(source.attributes);
    }

    // ===== 生命周期操作 =====

    /** 记录访问（更新时间戳和计数） */
    public ContextBlock touch() {
        this.lastAccess = System.currentTimeMillis();
        this.accessCount++;
        this.updatedAt = Instant.now();
        return this;
    }

    /** 衰减到下一生命周期状态 */
    public ContextBlock decay() {
        if (this.state == BlockLifecycle.PINNED) return this;
        BlockLifecycle next = this.state.decay();
        if (next != this.state) {
            this.state = next;
            this.generation++;
            this.updatedAt = Instant.now();
        }
        return this;
    }

    /** 减少 TTL（每轮对话调用），返回 true 表示 TTL 到期 */
    public boolean tick() {
        if (ttl > 0) {
            ttl--;
            return ttl <= 0;
        }
        return false;
    }

    /** 计算当前有效 token 数 */
    public long effectiveTokens() {
        return (long) (estimatedTokens * state.getContentIntegrity());
    }

    // ===== 类型判断 =====

    public boolean isSystem() { return "system".equals(type); }
    public boolean isUser() { return "user".equals(type); }
    public boolean isHistory() { return "history".equals(type); }
    public boolean isTool() { return "tool".equals(type); }
    public boolean isMemory() { return "memory".equals(type); }
    public boolean isReasoning() { return "reasoning".equals(type); }
    public boolean isPinned() { return priority == BlockPriority.PINNED; }

    // ===== Getters =====
    public String getId() { return id; }
    public String getType() { return type; }
    public String getRole() { return role; }
    public BlockPriority getPriority() { return priority; }
    public String getFormat() { return format; }
    public BlockLifecycle getState() { return state; }
    public int getTtl() { return ttl; }
    public long getLastAccess() { return lastAccess; }
    public int getAccessCount() { return accessCount; }
    public int getGeneration() { return generation; }
    public String getLabel() { return label; }
    public String getBlockAbstract() { return blockAbstract; }
    public String getContent() { return content; }
    public String getSummary() { return summary; }
    public long getEstimatedTokens() { return estimatedTokens; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Map<String, String> getAttributes() { return Collections.unmodifiableMap(attributes); }

    // ===== Setters（可变字段） =====
    public void setType(String type) { this.type = type; }
    public void setRole(String role) { this.role = role; }
    public void setPriority(BlockPriority priority) { this.priority = priority; }
    public void setFormat(String format) { this.format = format; }
    public void setState(BlockLifecycle state) { this.state = state; }
    public void setTtl(int ttl) { this.ttl = ttl; }
    public void setLabel(String label) { this.label = label; }
    public void setBlockAbstract(String blockAbstract) { this.blockAbstract = blockAbstract; }
    public void setContent(String content) { this.content = content; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setEstimatedTokens(long estimatedTokens) { this.estimatedTokens = estimatedTokens; }
    public void setAttribute(String key, String value) { this.attributes.put(key, value); }

    @Override
    public String toString() {
        return String.format("ContextBlock{id=%s, type=%s, priority=%s, state=%s, ttl=%d, gen=%d, tokens=%d}",
                id, type, priority.getName(), state.getState(), ttl, generation, estimatedTokens);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContextBlock that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    // ===== Builder =====

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String type = "general";
        private String role = "default";
        private BlockPriority priority = BlockPriority.MEDIUM;
        private String format = "markdown";
        private BlockLifecycle state = BlockLifecycle.ACTIVE;
        private int ttl = 3;
        private long lastAccess = System.currentTimeMillis();
        private int accessCount = 0;
        private int generation = 0;
        private String label = "";
        private String blockAbstract = "";
        private String content = "";
        private String summary = "";
        private long estimatedTokens = 0;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        public Builder(String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
        }

        public Builder type(String type) { this.type = type; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder priority(BlockPriority p) { this.priority = p; return this; }
        public Builder format(String f) { this.format = f; return this; }
        public Builder state(BlockLifecycle s) { this.state = s; return this; }
        public Builder ttl(int t) { this.ttl = t; return this; }
        public Builder lastAccess(long la) { this.lastAccess = la; return this; }
        public Builder accessCount(int ac) { this.accessCount = ac; return this; }
        public Builder generation(int g) { this.generation = g; return this; }
        public Builder label(String l) { this.label = l; return this; }
        public Builder blockAbstract(String a) { this.blockAbstract = a; return this; }
        public Builder content(String c) { this.content = c; this.estimatedTokens = estimateTokens(c); return this; }
        public Builder summary(String s) { this.summary = s; return this; }
        public Builder estimatedTokens(long t) { this.estimatedTokens = t; return this; }
        public Builder attribute(String k, String v) { this.attributes.put(k, v); return this; }
        public Builder attributes(Map<String, String> a) { this.attributes.putAll(a); return this; }

        public ContextBlock build() {
            if (estimatedTokens == 0 && content != null && !content.isEmpty()) {
                estimatedTokens = estimateTokens(content);
            }
            return new ContextBlock(this);
        }

        /** 简单 token 估算：中文字符约 1 token，英文约 4 字符/token */
        private static long estimateTokens(String text) {
            if (text == null || text.isEmpty()) return 0;
            int chineseChars = 0;
            int otherChars = 0;
            for (char c : text.toCharArray()) {
                if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
                    chineseChars++;
                } else {
                    otherChars++;
                }
            }
            return chineseChars + (otherChars / 4);
        }
    }
}
