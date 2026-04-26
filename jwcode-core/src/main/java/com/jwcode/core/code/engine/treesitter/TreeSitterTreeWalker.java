package com.jwcode.core.code.engine.treesitter;

import com.jwcode.core.code.api.SyntaxNode;
import com.jwcode.core.code.api.TreeWalker;
import com.jwcode.core.parser.treesitter.TreeCursor;

/**
 * Adapter: {@link TreeCursor} → {@link TreeWalker}
 */
public class TreeSitterTreeWalker implements TreeWalker {

    private final TreeCursor cursor;
    private final String source;
    private final String filePath;

    public TreeSitterTreeWalker(com.jwcode.core.parser.treesitter.Node root, String source, String filePath) {
        this.cursor = new TreeCursor(root);
        this.source = source;
        this.filePath = filePath;
    }

    @Override
    public boolean hasNext() {
        // TreeCursor doesn't have a built-in hasNext for depth-first.
        // We'll use a manual stack-based approach instead.
        return false; // not used; walk() is overridden
    }

    @Override
    public SyntaxNode next() {
        var node = cursor.getCurrentNode();
        return node != null ? new TreeSitterSyntaxNode(node, source, filePath) : null;
    }

    @Override
    public void enter() {
        cursor.gotoFirstChild();
    }

    @Override
    public void exit() {
        cursor.gotoParent();
    }

    @Override
    public SyntaxNode current() {
        var node = cursor.getCurrentNode();
        return node != null ? new TreeSitterSyntaxNode(node, source, filePath) : null;
    }

    @Override
    public void walk(java.util.function.Consumer<SyntaxNode> consumer) {
        // Manual depth-first walk using TreeCursor API
        boolean visited = false;
        while (true) {
            if (!visited) {
                var n = cursor.getCurrentNode();
                if (n != null) {
                    consumer.accept(new TreeSitterSyntaxNode(n, source, filePath));
                }
            }
            if (!visited && cursor.gotoFirstChild()) {
                visited = false;
                continue;
            }
            visited = true;
            if (cursor.gotoNextSibling()) {
                visited = false;
                continue;
            }
            if (!cursor.gotoParent()) {
                break;
            }
        }
    }

    @Override
    public void reset() {
        cursor.reset(cursor.getCurrentNode()); // best effort
    }

    @Override
    public void close() {
        cursor.close();
    }
}
