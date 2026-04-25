package com.jwcode.core.parser.treesitter;

/**
 * A position in a multi-line text document, in terms of rows and columns.
 * Rows and columns are zero-based.
 *
 * <p>Refer to tree-sitter C core: {@code TSPoint} (api.h L77)</p>
 */
public record Point(int row, int column) {
    public Point {
        if (row < 0) row = 0;
        if (column < 0) column = 0;
    }

    @Override
    public String toString() {
        return "(" + row + ", " + column + ")";
    }
}
