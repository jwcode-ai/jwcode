package com.jwcode.core.parser.treesitter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A stateful object that is used to walk a syntax tree more efficiently than is
 * possible using the {@link Node} functions.
 *
 * <p>Refer to tree-sitter C core: {@code TSTreeCursor} (api.h L139)</p>
 */
public class TreeCursor implements AutoCloseable {
    private Node root;
    private Node current;
    private final Deque<Node> nodeStack;
    private final Deque<Integer> depthStack;

    public TreeCursor(Node node) {
        this.root = node;
        this.current = node;
        this.nodeStack = new ArrayDeque<>();
        this.depthStack = new ArrayDeque<>();
    }

    public Node getCurrentNode() {
        return current;
    }

    public boolean gotoFirstChild() {
        List<Node> children = current.internalChildren();
        if (children.isEmpty()) {
            return false;
        }
        nodeStack.push(current);
        depthStack.push(0);
        current = children.get(0);
        return true;
    }

    public boolean gotoNextSibling() {
        if (nodeStack.isEmpty()) {
            return false;
        }
        Node parent = nodeStack.peek();
        int currentIndex = depthStack.pop();
        int nextIndex = currentIndex + 1;
        List<Node> children = parent.internalChildren();
        if (nextIndex >= children.size()) {
            depthStack.push(currentIndex);
            return false;
        }
        depthStack.push(nextIndex);
        current = children.get(nextIndex);
        return true;
    }

    public boolean gotoParent() {
        if (nodeStack.isEmpty()) {
            return false;
        }
        current = nodeStack.pop();
        depthStack.pop();
        return true;
    }

    public boolean gotoLastChild() {
        List<Node> children = current.internalChildren();
        if (children.isEmpty()) {
            return false;
        }
        nodeStack.push(current);
        depthStack.push(children.size() - 1);
        current = children.get(children.size() - 1);
        return true;
    }

    public void reset(Node node) {
        this.root = node;
        this.current = node;
        this.nodeStack.clear();
        this.depthStack.clear();
    }

    @Override
    public void close() {
        // No native resources to free
    }
}
