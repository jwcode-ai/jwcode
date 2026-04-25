package com.jwcode.core.parser.treesitter;

/**
 * A summary of a change to a text document.
 *
 * <p>Refer to tree-sitter C core: {@code TSInputEdit} (api.h L124)</p>
 */
public record InputEdit(
    int startByte,
    int oldEndByte,
    int newEndByte,
    Point startPoint,
    Point oldEndPoint,
    Point newEndPoint
) {
}
