package com.jwcode.core.tool;

import java.util.List;

/**
 * 工具引用 — 类型安全、自描述的工具标识符。
 *
 * <p>取代到处传递的字符串工具名，提供编译期检查和语义明确的引用方式。</p>
 *
 * <p>三种引用形式：</p>
 * <ul>
 *   <li>{@link ByName} — 按名称引用（与旧版兼容）</li>
 *   <li>{@link ByType} — 按类型引用（编译期安全）</li>
 *   <li>{@link Composite} — 组合引用（批量操作）</li>
 * </ul>
 */
public interface ToolRef {

    /**
     * 按名称引用工具
     */
    record ByName(String name) implements ToolRef {
        public ByName {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Tool name cannot be null or blank");
            }
        }
    }

    /**
     * 按类型引用工具
     */
    record ByType(Class<? extends Tool<?, ?, ?>> type) implements ToolRef {
        public ByType {
            if (type == null) {
                throw new IllegalArgumentException("Tool type cannot be null");
            }
        }
    }

    /**
     * 组合引用 — 一次引用多个工具
     */
    record Composite(List<ToolRef> refs) implements ToolRef {
        public Composite {
            if (refs == null || refs.isEmpty()) {
                throw new IllegalArgumentException("Composite refs cannot be null or empty");
            }
        }

        public Composite(ToolRef... refs) {
            this(List.of(refs));
        }
    }
}
