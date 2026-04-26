package com.jwcode.core.code.engine.treesitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.code.api.SyntaxNode;
import com.jwcode.core.code.api.SyntaxTree;
import com.jwcode.core.code.api.TextEdit;
import com.jwcode.core.code.api.TreeWalker;

import java.util.List;

/**
 * Adapter: {@link com.jwcode.core.parser.treesitter.Tree} → {@link SyntaxTree}
 */
public class TreeSitterSyntaxTree implements SyntaxTree {

    private final com.jwcode.core.parser.treesitter.Tree inner;
    private final String filePath;

    public TreeSitterSyntaxTree(com.jwcode.core.parser.treesitter.Tree inner, String filePath) {
        this.inner = inner;
        this.filePath = filePath;
    }

    @Override
    public String getLanguage() {
        return inner.getLanguage().getName();
    }

    @Override
    public SyntaxNode getRootNode() {
        return new TreeSitterSyntaxNode(inner.getRootNode(), inner.getSource(), filePath);
    }

    @Override
    public String getSource() {
        return inner.getSource();
    }

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public SyntaxTree edit(List<TextEdit> edits) {
        // TODO: implement true incremental parsing
        // For now, return a copy indicating no incremental support
        String modified = applyEdits(inner.getSource(), edits);
        // Re-parse through the language
        var tree = inner.getLanguage().parse(modified, inner);
        return tree != null ? new TreeSitterSyntaxTree(tree, filePath) : this;
    }

    private String applyEdits(String source, List<TextEdit> edits) {
        StringBuilder sb = new StringBuilder(source);
        // Apply edits in reverse order to preserve offsets
        List<TextEdit> sorted = edits.stream()
            .sorted((a, b) -> Integer.compare(b.startOffset(), a.startOffset()))
            .toList();
        for (TextEdit edit : sorted) {
            sb.replace(edit.startOffset(), edit.endOffset(), edit.newText());
        }
        return sb.toString();
    }

    @Override
    public SyntaxNode getNodeAt(int line, int column) {
        return findNodeAt(getRootNode(), line, column);
    }

    private SyntaxNode findNodeAt(SyntaxNode node, int line, int column) {
        var range = node.getRange();
        if (!range.contains(line, column)) {
            return null;
        }
        for (SyntaxNode child : node.getChildren()) {
            SyntaxNode found = findNodeAt(child, line, column);
            if (found != null) {
                return found;
            }
        }
        return node;
    }

    @Override
    public SyntaxNode getNodeAtByte(int byteOffset) {
        return findNodeAtByte(getRootNode(), byteOffset);
    }

    private SyntaxNode findNodeAtByte(SyntaxNode node, int byteOffset) {
        var range = node.getByteRange();
        if (byteOffset < range.startByte() || byteOffset > range.endByte()) {
            return null;
        }
        for (SyntaxNode child : node.getChildren()) {
            SyntaxNode found = findNodeAtByte(child, byteOffset);
            if (found != null) {
                return found;
            }
        }
        return node;
    }

    @Override
    public TreeWalker getWalker() {
        return new TreeSitterTreeWalker(inner.getRootNode(), inner.getSource(), filePath);
    }

    @Override
    public boolean hasErrors() {
        return inner.getRootNode().hasError();
    }

    @Override
    public List<SyntaxNode> getErrorNodes() {
        return getRootNode().findAll(SyntaxNode::hasError);
    }

    @Override
    public JsonNode getMetadata() {
        ObjectNode meta = JsonNodeFactory.instance.objectNode();
        meta.put("language", getLanguage());
        meta.put("filePath", filePath);
        return meta;
    }

    @Override
    public String toSexp() {
        return getRootNode().toSexp();
    }

    @Override
    public String toXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        toXml(getRootNode(), sb, 0);
        return sb.toString();
    }

    private void toXml(SyntaxNode node, StringBuilder sb, int depth) {
        String indent = "  ".repeat(depth);
        String type = escapeXml(node.getType());
        sb.append(indent).append("<").append(type);
        var range = node.getRange();
        sb.append(" startLine=\"").append(range.startLine()).append("\"");
        sb.append(" startCol=\"").append(range.startColumn()).append("\"");
        sb.append(" endLine=\"").append(range.endLine()).append("\"");
        sb.append(" endCol=\"").append(range.endColumn()).append("\"");

        List<SyntaxNode> children = node.getChildren();
        if (children.isEmpty()) {
            String text = escapeXml(node.getText());
            if (!text.isBlank()) {
                sb.append(">").append(text).append("</").append(type).append(">\n");
            } else {
                sb.append("/>\n");
            }
        } else {
            sb.append(">\n");
            for (SyntaxNode child : children) {
                toXml(child, sb, depth + 1);
            }
            sb.append(indent).append("</").append(type).append(">\n");
        }
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    @Override
    public JsonNode toJson() {
        return toJsonNode(getRootNode());
    }

    private JsonNode toJsonNode(SyntaxNode node) {
        ObjectNode obj = JsonNodeFactory.instance.objectNode();
        obj.put("type", node.getType());
        obj.put("text", node.getText());
        var range = node.getRange();
        ObjectNode pos = JsonNodeFactory.instance.objectNode();
        pos.put("startLine", range.startLine());
        pos.put("startColumn", range.startColumn());
        pos.put("endLine", range.endLine());
        pos.put("endColumn", range.endColumn());
        obj.set("range", pos);

        ArrayNode children = JsonNodeFactory.instance.arrayNode();
        for (SyntaxNode child : node.getChildren()) {
            children.add(toJsonNode(child));
        }
        obj.set("children", children);
        return obj;
    }

    @Override
    public String toDot() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph AST {\n");
        sb.append("  node [shape=box];\n");
        dotNode(getRootNode(), sb, new int[]{0});
        sb.append("}\n");
        return sb.toString();
    }

    private int dotNode(SyntaxNode node, StringBuilder sb, int[] idCounter) {
        int myId = idCounter[0]++;
        String label = node.getType().replace("\"", "\\\"");
        String text = node.getText();
        if (text.length() > 20) {
            text = text.substring(0, 20) + "...";
        }
        text = text.replace("\"", "\\\"").replace("\n", "\\n");
        sb.append(String.format("  n%d [label=\"%s\\n%s\"];\n", myId, label, text));

        for (SyntaxNode child : node.getChildren()) {
            int childId = dotNode(child, sb, idCounter);
            sb.append(String.format("  n%d -> n%d;\n", myId, childId));
        }
        return myId;
    }
}
