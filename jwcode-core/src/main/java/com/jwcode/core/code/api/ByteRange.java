package com.jwcode.core.code.api;

/**
 * 字节范围（用于底层解析器）
 */
public record ByteRange(int startByte, int endByte) {
    public int length() {
        return endByte - startByte;
    }
}
