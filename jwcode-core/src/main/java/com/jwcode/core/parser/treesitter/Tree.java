package com.jwcode.core.parser.treesitter;

/**
 * A tree that represents the syntactic structure of a source code file.
 *
 * <p>Refer to tree-sitter C core: {@code TSTree} (api.h L46)</p>
 */
public class Tree {
    private final Node rootNode;
    private final Language language;
    private final String source;

    public Tree(Node rootNode, Language language, String source) {
        this.rootNode = rootNode;
        this.language = language;
        this.source = source;
    }

    public Node getRootNode() {
        return rootNode;
    }

    public Language getLanguage() {
        return language;
    }

    public String getSource() {
        return source;
    }

    /**
     * Create a shallow copy of the syntax tree. This is very fast.
     */
    public Tree copy() {
        return new Tree(rootNode, language, source);
    }
}
