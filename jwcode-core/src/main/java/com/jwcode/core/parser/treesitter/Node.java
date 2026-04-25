package com.jwcode.core.parser.treesitter;

import java.util.List;
import java.util.Optional;

/**
 * A single node within a syntax {@link Tree}.
 *
 * <p>Refer to tree-sitter C core: {@code TSNode} (api.h L133)</p>
 */
public class Node {
    private final String type;
    private final String text;
    private final int startByte;
    private final int endByte;
    private final Point startPoint;
    private final Point endPoint;
    private final List<Node> children;
    private final Node parent;
    private final boolean named;
    private final boolean hasError;

    Node(String type, String text, int startByte, int endByte, Point startPoint, Point endPoint,
         List<Node> children, Node parent, boolean named, boolean hasError) {
        this.type = type;
        this.text = text;
        this.startByte = startByte;
        this.endByte = endByte;
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.children = children != null ? List.copyOf(children) : List.of();
        this.parent = parent;
        this.named = named;
        this.hasError = hasError;
    }

    /**
     * Factory method that recursively attaches parent references.
     */
    public static Node of(String type, String text, int startByte, int endByte,
                          Point startPoint, Point endPoint,
                          List<Node> rawChildren, boolean named, boolean hasError) {
        if (rawChildren == null || rawChildren.isEmpty()) {
            return new Node(type, text, startByte, endByte, startPoint, endPoint,
                            List.of(), null, named, hasError);
        }
        // Create parent placeholder to pass into children
        Node parent = new Node(type, text, startByte, endByte, startPoint, endPoint,
                               null, null, named, hasError);
        List<Node> attachedChildren = rawChildren.stream()
            .map(c -> c.withParent(parent))
            .toList();
        return new Node(type, text, startByte, endByte, startPoint, endPoint,
                        attachedChildren, null, named, hasError);
    }

    private Node withParent(Node newParent) {
        if (this.children.isEmpty()) {
            return new Node(this.type, this.text, this.startByte, this.endByte,
                            this.startPoint, this.endPoint, List.of(), newParent,
                            this.named, this.hasError);
        }
        List<Node> attachedChildren = this.children.stream()
            .map(c -> c.withParent(this))
            .toList();
        return new Node(this.type, this.text, this.startByte, this.endByte,
                        this.startPoint, this.endPoint, attachedChildren, newParent,
                        this.named, this.hasError);
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getStartByte() {
        return startByte;
    }

    public int getEndByte() {
        return endByte;
    }

    public Point getStartPoint() {
        return startPoint;
    }

    public Point getEndPoint() {
        return endPoint;
    }

    public long getChildCount() {
        return children.size();
    }

    public Optional<Node> getChild(int index) {
        if (index < 0 || index >= children.size()) {
            return Optional.empty();
        }
        return Optional.of(children.get(index));
    }

    public Node getParent() {
        return parent;
    }

    public Optional<Node> getNextSibling() {
        if (parent == null) {
            return Optional.empty();
        }
        List<Node> siblings = parent.children;
        for (int i = 0; i < siblings.size() - 1; i++) {
            if (siblings.get(i) == this) {
                return Optional.of(siblings.get(i + 1));
            }
        }
        return Optional.empty();
    }

    public Optional<Node> getPrevSibling() {
        if (parent == null) {
            return Optional.empty();
        }
        List<Node> siblings = parent.children;
        for (int i = 1; i < siblings.size(); i++) {
            if (siblings.get(i) == this) {
                return Optional.of(siblings.get(i - 1));
            }
        }
        return Optional.empty();
    }

    public boolean isNamed() {
        return named;
    }

    public boolean isNull() {
        return false;
    }

    public boolean hasError() {
        return hasError;
    }

    public TreeCursor walk() {
        return new TreeCursor(this);
    }

    // Package-private access for TreeCursor
    List<Node> internalChildren() {
        return children;
    }
}
