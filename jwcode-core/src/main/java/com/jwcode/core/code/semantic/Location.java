package com.jwcode.core.code.semantic;

import com.jwcode.core.code.api.Range;

/**
 * 代码位置
 */
public record Location(String file, int line, int column, int endLine, int endColumn) {
    public static Location from(Range range) {
        return new Location(range.file(), range.startLine(), range.startColumn(),
                          range.endLine(), range.endColumn());
    }
}
