package com.jwcode.core.code.engine.treesitter;

import com.jwcode.core.code.api.ByteRange;
import com.jwcode.core.code.api.Range;
import com.jwcode.core.code.api.SyntaxNode;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Adapter: {@link com.jwcode.core.parser.treesitter.Node} → {@link SyntaxNode}
 */
public class TreeSitterSyntaxNode implements SyntaxNode {

    private final com.jwcode.core.parser.treesitter.Node inner;
    private final String source;
    private final String filePath;

    public TreeSitterSyntaxNode(com.jwcode.core.parser.treesitter.Node inner, String source, String filePath) {
        this.inner = Objects.requireNonNull(inner);
        this.source = source;
        this.filePath = filePath;
    }

    @Override
    public String getType() {
        return inner.getType();
    }

    @Override
    public String getText() {
        return inner.getText();
    }

    @Override
    public Range getRange() {
        var start = inner.getStartPoint();
        var end = inner.getEndPoint();
        return new Range(filePath, start.row(), start.column(), end.row(), end.column());
    }

    @Override
    public ByteRange getByteRange() {
        return new ByteRange(inner.getStartByte(), inner.getEndByte());
    }

    @Override
    public Optional<SyntaxNode> getParent() {
        var parent = inner.getParent();
        return parent != null ? Optional.of(wrap(parent)) : Optional.empty();
    }

    @Override
    public List<SyntaxNode> getChildren() {
        int count = (int) inner.getChildCount();
        List<SyntaxNode> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            inner.getChild(i).ifPresent(c -> result.add(wrap(c)));
        }
        return result;
    }

    @Override
    public Optional<SyntaxNode> getField(String fieldName) {
        // Heuristic mapping for common AST fields
        List<SyntaxNode> children = getChildren();
        return switch (fieldName) {
            case "name" -> findFirstChildByType(
                "identifier", "type_identifier", "scoped_identifier"
            );
            case "body" -> findFirstChildByType(
                "block", "class_body", "interface_body", "enum_body",
                "method_body", "constructor_body"
            );
            case "modifiers" -> findFirstChildByType("modifiers");
            case "parameters" -> findFirstChildByType("formal_parameters", "parameters");
            case "type" -> findFirstChildByType(
                "type_identifier", "primitive_type", "array_type", "parameterized_type"
            );
            case "return_type" -> findFirstChildByType(
                "type_identifier", "primitive_type", "void_type"
            );
            default -> children.stream()
                .filter(c -> c.getType().equals(fieldName))
                .findFirst();
        };
    }

    private Optional<SyntaxNode> findFirstChildByType(String... types) {
        Set<String> set = Set.of(types);
        return getChildren().stream()
            .filter(c -> set.contains(c.getType()))
            .findFirst();
    }

    @Override
    public Optional<SyntaxNode> getNextSibling() {
        return inner.getNextSibling().map(this::wrap);
    }

    @Override
    public Optional<SyntaxNode> getPrevSibling() {
        return inner.getPrevSibling().map(this::wrap);
    }

    @Override
    public boolean isNamed() {
        return inner.isNamed();
    }

    @Override
    public boolean isMissing() {
        return false; // parser layer doesn't track missing nodes
    }

    @Override
    public boolean isError() {
        return false; // parser layer marks hasError on tree, not per node
    }

    @Override
    public boolean hasError() {
        return inner.hasError();
    }

    @Override
    public Optional<SyntaxNode> findFirst(Predicate<SyntaxNode> predicate) {
        if (predicate.test(this)) {
            return Optional.of(this);
        }
        for (SyntaxNode child : getChildren()) {
            Optional<SyntaxNode> found = child.findFirst(predicate);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    @Override
    public List<SyntaxNode> findAll(Predicate<SyntaxNode> predicate) {
        List<SyntaxNode> result = new ArrayList<>();
        collectAll(this, predicate, result);
        return result;
    }

    private void collectAll(SyntaxNode node, Predicate<SyntaxNode> predicate, List<SyntaxNode> result) {
        if (predicate.test(node)) {
            result.add(node);
        }
        for (SyntaxNode child : node.getChildren()) {
            collectAll(child, predicate, result);
        }
    }

    @Override
    public List<SyntaxNode> find(String selector) {
        if (selector == null || selector.isBlank()) {
            return List.of();
        }
        String s = selector.trim();

        // Direct child: "parent > child"
        if (s.contains(">")) {
            String[] parts = s.split("\\s*>\\s*");
            List<SyntaxNode> current = List.of(this);
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                // If first part matches current node, skip to next part
                if (i == 0 && current.size() == 1 && current.get(0) == this
                    && matchesSelector(this, part)) {
                    continue;
                }
                List<SyntaxNode> next = new ArrayList<>();
                for (SyntaxNode node : current) {
                    for (SyntaxNode child : node.getChildren()) {
                        if (matchesSelector(child, part)) {
                            next.add(child);
                        }
                    }
                }
                current = next;
                if (current.isEmpty()) break;
            }
            return current;
        }

        // Attribute selector: "[field]"
        if (s.startsWith("[") && s.endsWith("]")) {
            String field = s.substring(1, s.length() - 1);
            return findAll(n -> n.getField(field).isPresent());
        }

        // Simple type match (descendants)
        return findAll(n -> matchesSelector(n, s));
    }

    private boolean matchesSelector(SyntaxNode node, String selector) {
        return node.getType().equals(selector)
            || selector.equals("*")
            || (selector.equals("[named]") && node.isNamed());
    }

    @Override
    public List<SyntaxNode> getPath() {
        List<SyntaxNode> path = new ArrayList<>();
        SyntaxNode current = this;
        while (current != null) {
            path.add(0, current);
            current = current.getParent().orElse(null);
        }
        return path;
    }

    @Override
    public boolean contains(SyntaxNode node) {
        if (this.equals(node)) return true;
        for (SyntaxNode child : getChildren()) {
            if (child.contains(node)) return true;
        }
        return false;
    }

    @Override
    public String toSexp() {
        StringBuilder sb = new StringBuilder();
        toSexp(this, sb);
        return sb.toString();
    }

    private void toSexp(SyntaxNode node, StringBuilder sb) {
        sb.append("(").append(node.getType());
        String text = node.getText();
        if (text != null && !text.isBlank() && text.length() < 40) {
            sb.append(" \"").append(text.replace("\"", "\\\"")).append("\"");
        }
        for (SyntaxNode child : node.getChildren()) {
            sb.append(" ");
            toSexp(child, sb);
        }
        sb.append(")");
    }

    @Override
    public String describe() {
        return String.format("%s [%s] '%s'",
            getType(),
            getRange(),
            getText().length() > 30 ? getText().substring(0, 30) + "..." : getText()
        );
    }

    private TreeSitterSyntaxNode wrap(com.jwcode.core.parser.treesitter.Node node) {
        return new TreeSitterSyntaxNode(node, source, filePath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TreeSitterSyntaxNode)) return false;
        TreeSitterSyntaxNode that = (TreeSitterSyntaxNode) o;
        return inner == that.inner;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(inner);
    }
}
