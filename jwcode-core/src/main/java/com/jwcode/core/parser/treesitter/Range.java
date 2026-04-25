package com.jwcode.core.parser.treesitter;

/**
 * A range of positions in a multi-line text document, both in terms of bytes
 * and of rows and columns.
 *
 * <p>Refer to tree-sitter C core: {@code TSRange} (api.h L82)</p>
 */
public record Range(int startByte, int endByte, Point startPoint, Point endPoint) {
}
