package com.jwcode.ui.layout;

/**
 * Align - Flexbox 对齐方式枚举
 *
 * <p>用于 {@code justifyContent}、{@code alignItems} 和 {@code alignSelf}。</p>
 */
public enum Align {
    /** 起始对齐 */
    FLEX_START,
    /** 居中对齐 */
    CENTER,
    /** 末尾对齐 */
    FLEX_END,
    /** 拉伸填满 */
    STRETCH,
    /** 间距均匀分布（仅 justifyContent） */
    SPACE_BETWEEN,
    /** 间距均匀分布，两端也有空间（仅 justifyContent） */
    SPACE_AROUND,
    /** 间距均匀分布，两端空间相等（仅 justifyContent） */
    SPACE_EVENLY
}
