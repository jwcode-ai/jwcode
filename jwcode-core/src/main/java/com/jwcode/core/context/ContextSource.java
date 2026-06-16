package com.jwcode.core.context;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Typed context source — 带类型的、独立可刷新的上下文值。
 *
 * <p>每个 source 定义：
 * <ul>
 *   <li><b>key</b> — 带命名空间的稳定标识符（如 {@code core/date}, {@code core/environment}）</li>
 *   <li><b>codec</b> — JSON schema 用于比较前后值是否变化</li>
 *   <li><b>loader</b> — 返回当前值或 {@code unavailable} 表示不可用</li>
 *   <li><b>renderer</b> — 基线渲染器和增量更新渲染器</li>
 * </ul>
 *
 * <p>设计参照 opencode SystemContext，但舍去了 Effect-TS 泛型约束，
 * 改用 Java 泛型 + {@link ContextValue} 包装。
 *
 * @param <A> 上下文值的类型
 */
public class ContextSource<A> {

    /** 稳定命名空间 key */
    private final String key;
    /** 值加载器 */
    private final Loader<A> loader;
    /** 基线渲染器 */
    private final Renderer<A> baselineRenderer;
    /** 增量更新渲染器（可为 null） */
    private final Renderer<A> updateRenderer;
    /** 移除渲染器（可为 null） */
    private final Renderer<A> removalRenderer;

    protected ContextSource(Builder<A> b) {
        this.key = Objects.requireNonNull(b.key, "key must not be null");
        this.loader = Objects.requireNonNull(b.loader, "loader must not be null");
        this.baselineRenderer = Objects.requireNonNull(b.baselineRenderer, "baselineRenderer must not be null");
        this.updateRenderer = b.updateRenderer;
        this.removalRenderer = b.removalRenderer;
    }

    public String getKey() { return key; }
    public Loader<A> getLoader() { return loader; }
    public Renderer<A> getBaselineRenderer() { return baselineRenderer; }
    public Renderer<A> getUpdateRenderer() { return updateRenderer; }
    public Renderer<A> getRemovalRenderer() { return removalRenderer; }

    /**
     * 加载当前值。若不可用返回 {@link ContextValue#unavailable()}。
     */
    public ContextValue<A> load() {
        try {
            return loader.load();
        } catch (Exception e) {
            return ContextValue.unavailable();
        }
    }

    // ==================== Builder ====================

    public static <A> Builder<A> builder(String key) {
        return new Builder<>(key);
    }

    public static class Builder<A> {
        private final String key;
        private Loader<A> loader;
        private Renderer<A> baselineRenderer;
        private Renderer<A> updateRenderer;
        private Renderer<A> removalRenderer;

        public Builder(String key) { this.key = key; }
        public Builder<A> loader(Loader<A> loader) { this.loader = loader; return this; }
        public Builder<A> baselineRenderer(Renderer<A> renderer) { this.baselineRenderer = renderer; return this; }
        public Builder<A> updateRenderer(Renderer<A> renderer) { this.updateRenderer = renderer; return this; }
        public Builder<A> removalRenderer(Renderer<A> renderer) { this.removalRenderer = renderer; return this; }
        public ContextSource<A> build() { return new ContextSource<>(this); }
    }

    // ==================== 函数式接口 ====================

    @FunctionalInterface
    public interface Loader<A> {
        ContextValue<A> load();
    }

    @FunctionalInterface
    public interface Renderer<A> {
        String render(A value);
    }

    // ==================== equals/hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContextSource<?> that)) return false;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() { return key.hashCode(); }

    @Override
    public String toString() {
        return "ContextSource{key='" + key + "'}";
    }
}
