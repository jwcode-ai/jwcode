package com.jwcode.core.aicl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ContextBlock — AICL 上下文块模型。
 *
 * <p>每个上下文块统一包装为 {@code <ctx:block>} 元素，
 * 携带优先级、状态、TTL、最后访问时间、代际等生命周期属性。</p>
 */
public class ContextBlock {

    private final String id;
    private final String type;
    private String role;
    private BlockPriority priority;
    private BlockLifecycle state;
    private int ttl;                // TTL 轮数（-1 永久）
    private Instant lastAccess;     // 最后访问时间
    private int accessCount;        // 访问次数
    private int generation;         // 代际
    private String content;
    private String summary;         // 摘要
    private String label;           // 标签
    private String blockAbstract;   // 摘要句（归档用）
    private String format;          // 格式（markdown/text）
    private long estimatedTokens;   // 预估 token 数
    private Map<String, String> attributes; // 扩展属性

    public ContextBlock(String id, String type, BlockPriority priority) {
        this.id = id;
        this.type = type;
        this.priority = priority;
        this.state = BlockLifecycle.ACTIVE;
        this.ttl = 3;
        this.lastAccess = Instant.now();
        this.accessCount = 0;
        this.generation = 0;
        this.estimatedTokens = 0;
        this.role = "default";
        this.format = "markdown";
        this.attributes = new HashMap<>();
    }

    /**
     * 拷贝构造（用于淘汰时创建更新副本）。
     */
    public ContextBlock(ContextBlock other) {
        this.id = other.id;
        this.type = other.type;
        this.role = other.role;
        this.priority = other.priority;
        this.state = other.state;
        this.ttl = other.ttl;
        this.lastAccess = other.lastAccess;
        this.accessCount = other.accessCount;
        this.generation = other.generation;
        this.content = other.content;
        this.summary = other.summary;
        this.label = other.label;
        this.blockAbstract = other.blockAbstract;
        this.format = other.format;
        this.estimatedTokens = other.estimatedTokens;
        this.attributes = new HashMap<>(other.attributes);
    }

    // ==================== Getters ====================

    public String getId() { return id; }
    public String getType() { return type; }
    public String getRole() { return role; }
    public BlockPriority getPriority() { return priority; }
    public BlockLifecycle getState() { return state; }
    public int getTtl() { return ttl; }
    public Instant getLastAccess() { return lastAccess; }
    public int getAccessCount() { return accessCount; }
    public int getGeneration() { return generation; }
    public String getContent() { return content; }
    public String getSummary() { return summary; }
    public String getLabel() { return label; }
    public String getBlockAbstract() { return blockAbstract; }
    public String getFormat() { return format; }
    public long getEstimatedTokens() { return estimatedTokens; }
    public Map<String, String> getAttributes() { return attributes; }

    // ==================== Setters ====================

    public void setRole(String role) { this.role = role; }
    public void setPriority(BlockPriority priority) { this.priority = priority; }
    public void setState(BlockLifecycle state) { this.state = state; }
    public void setTtl(int ttl) { this.ttl = ttl; }
    public void setLastAccess(Instant lastAccess) { this.lastAccess = lastAccess; }
    public void setAccessCount(int accessCount) { this.accessCount = accessCount; }
    public void setGeneration(int generation) { this.generation = generation; }
    public void setContent(String content) { this.content = content; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setLabel(String label) { this.label = label; }
    public void setBlockAbstract(String blockAbstract) { this.blockAbstract = blockAbstract; }
    public void setFormat(String format) { this.format = format; }
    public void setEstimatedTokens(long estimatedTokens) { this.estimatedTokens = estimatedTokens; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }

    // ==================== 生命周期方法 ====================

    /**
     * 访问块，更新最后访问时间和访问计数。
     */
    public void access() {
        this.lastAccess = Instant.now();
        this.accessCount++;
    }

    /**
     * touch() — access() 的别名，用于 ContextAssembler。
     */
    public void touch() {
        access();
    }

    /**
     * TTL 倒计时：递减 ttl，返回是否过期（ttl <= 0）。
     * 与 ContextAssembler.tick() 配合使用。
     */
    public boolean tick() {
        if (ttl > 0) {
            ttl--;
        }
        return ttl == 0;
    }

    /**
     * 生命周期衰减：state 降级到下一级，generation +1。
     */
    public void decay() {
        this.state = this.state.decay();
        this.generation++;
    }

    /**
     * 判断是否已过期（基于 lastAccess + ttl*轮次估算）。
     */
    public boolean isExpired() {
        return ttl == 0 && state != BlockLifecycle.PINNED;
    }

    /**
     * 计算有效 token 数。
     * 根据 state 的 contentIntegrity 比例估算。
     */
    public long effectiveTokens() {
        if (state == BlockLifecycle.DEPRECATED) return 0;
        if (state == BlockLifecycle.ARCHIVED) {
            // 归档态：仅 label + abstract 的 token 数
            long base = (label != null ? label.length() / 3 : 0)
                      + (blockAbstract != null ? blockAbstract.length() / 3 : 0);
            return Math.max(20, base);
        }
        if (state == BlockLifecycle.SUMMARIZED) {
            // 摘要态：使用 summary 长度估算
            long sumLen = summary != null ? summary.length() / 3 : 0;
            return Math.max(50, sumLen);
        }
        // 其他状态：使用 content 长度 * contentIntegrity
        long raw = (content != null ? content.length() / 3 : 0) + estimatedTokens;
        return Math.max(10, (long) (raw * state.getContentIntegrity()));
    }

    /**
     * 计算未使用时长（毫秒）。
     */
    public long getIdleMillis() {
        return java.time.Duration.between(lastAccess, Instant.now()).toMillis();
    }

    // ==================== Builder ====================

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String type = "general";
        private String role = "default";
        private BlockPriority priority = BlockPriority.MEDIUM;
        private BlockLifecycle state = BlockLifecycle.ACTIVE;
        private int ttl = 3;
        private Instant lastAccess = Instant.now();
        private int accessCount = 0;
        private int generation = 0;
        private String content;
        private String summary;
        private String label;
        private String blockAbstract;
        private String format = "markdown";
        private long estimatedTokens = 0;
        private Map<String, String> attributes = new HashMap<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder type(String type) { this.type = type; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder priority(BlockPriority priority) { this.priority = priority; return this; }
        public Builder state(BlockLifecycle state) { this.state = state; return this; }
        public Builder ttl(int ttl) { this.ttl = ttl; return this; }
        public Builder lastAccess(Instant lastAccess) { this.lastAccess = lastAccess; return this; }
        public Builder lastAccess(long epochMillis) {
            this.lastAccess = Instant.ofEpochMilli(epochMillis);
            return this;
        }
        public Builder accessCount(int accessCount) { this.accessCount = accessCount; return this; }
        public Builder generation(int generation) { this.generation = generation; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder label(String label) { this.label = label; return this; }
        public Builder blockAbstract(String blockAbstract) { this.blockAbstract = blockAbstract; return this; }
        public Builder format(String format) { this.format = format; return this; }
        public Builder estimatedTokens(long estimatedTokens) { this.estimatedTokens = estimatedTokens; return this; }
        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }
        public Builder attributes(Map<String, String> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        public ContextBlock build() {
            ContextBlock block = new ContextBlock(id, type, priority);
            block.role = this.role;
            block.state = this.state;
            block.ttl = this.ttl;
            block.lastAccess = this.lastAccess;
            block.accessCount = this.accessCount;
            block.generation = this.generation;
            block.content = this.content;
            block.summary = this.summary;
            block.label = this.label;
            block.blockAbstract = this.blockAbstract;
            block.format = this.format;
            block.estimatedTokens = this.estimatedTokens;
            block.attributes = new HashMap<>(this.attributes);
            return block;
        }
    }
}
