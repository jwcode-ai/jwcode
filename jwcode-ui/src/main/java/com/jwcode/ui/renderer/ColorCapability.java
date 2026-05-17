package com.jwcode.ui.renderer;

/**
 * ColorCapability - 终端色彩能力级别枚举
 *
 * <p>用于 ANSI 渲染器自动降级策略。</p>
 */
public enum ColorCapability {
    /** 仅支持 16 色（标准 ANSI 色） */
    COLORS_16,
    /** 支持 256 色（8-bit 色） */
    COLORS_256,
    /** 支持真彩色（24-bit，1670 万色） */
    TRUE_COLOR
}
