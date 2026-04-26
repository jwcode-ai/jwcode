package com.jwcode.core.code.analysis;

import com.jwcode.core.code.api.SyntaxNode;
import com.jwcode.core.code.api.SyntaxTree;
import com.jwcode.core.code.semantic.*;

import java.nio.file.Path;
import java.util.*;

/**
 * 从 {@link SyntaxTree} 提取符号并构建 {@link SymbolGraph}。
 *
 * <p>识别范围：</p>
 * <ul>
 *   <li>类 / 接口 / 枚举 / 注解定义</li>
 *   <li>方法 / 构造函数定义</li>
 *   <li>字段 / 变量定义</li>
 *   <li>方法调用关系</li>
 *   <li>继承 / 实现关系</li>
 * </ul>
 */
public class SymbolGraphBuilder {

    private final SymbolGraph graph = new SymbolGraph();
    private String currentFile = "";
    private final Deque<String> scopeStack = new ArrayDeque<>();

    public SymbolGraph build(List<SyntaxTree> trees) {
        for (SyntaxTree tree : trees) {
            currentFile = tree.getFilePath() != null ? tree.getFilePath() : "";
            scopeStack.clear();
            String fileNodeId = "file:" + currentFile;
            graph.getOrCreateNode(fileNodeId, currentFile, SymbolKind.FILE,
                new Location(currentFile, 0, 0, 0, 0));
            processNode(tree.getRootNode(), fileNodeId);
        }
        return graph;
    }

    private void processNode(SyntaxNode node, String parentId) {
        String type = node.getType();
        String nodeId = null;
        SymbolKind kind = null;
        String name = extractName(node);

        switch (type) {
            case "class_declaration", "interface_declaration",
                 "enum_declaration", "annotation_type_declaration" -> {
                kind = switch (type) {
                    case "interface_declaration" -> SymbolKind.INTERFACE;
                    case "enum_declaration" -> SymbolKind.ENUM;
                    case "annotation_type_declaration" -> SymbolKind.ANNOTATION;
                    default -> SymbolKind.CLASS;
                };
                nodeId = qualify(parentId, name);
                addSymbol(nodeId, name, kind, node);
                graph.addRelation(parentId, nodeId, RelationType.CONTAINS, node);
                if (type.equals("class_declaration")) {
                    extractInheritance(node, nodeId);
                }
                scopeStack.push(nodeId);
                for (SyntaxNode child : node.getChildren()) {
                    processNode(child, nodeId);
                }
                scopeStack.pop();
                return;
            }
            case "method_declaration", "constructor_declaration" -> {
                kind = type.equals("constructor_declaration") ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
                nodeId = qualify(parentId, name + signatureHint(node));
                addSymbol(nodeId, name, kind, node);
                graph.addRelation(parentId, nodeId, RelationType.DEFINES, node);
                scopeStack.push(nodeId);
                for (SyntaxNode child : node.getChildren()) {
                    processNode(child, nodeId);
                }
                scopeStack.pop();
                return;
            }
            case "field_declaration", "variable_declarator" -> {
                kind = SymbolKind.FIELD;
                nodeId = qualify(parentId, name);
                addSymbol(nodeId, name, kind, node);
                graph.addRelation(parentId, nodeId, RelationType.DEFINES, node);
            }
            case "method_invocation" -> {
                String target = extractMethodTarget(node);
                if (target != null && !scopeStack.isEmpty()) {
                    String caller = scopeStack.peek();
                    // Best-effort: we don't resolve to actual definition yet
                    String calleeId = "unresolved:" + target;
                    graph.getOrCreateNode(calleeId, target, SymbolKind.METHOD,
                        locationOf(node));
                    graph.addRelation(caller, calleeId, RelationType.CALLS, node);
                }
            }
        }

        for (SyntaxNode child : node.getChildren()) {
            processNode(child, parentId);
        }
    }

    private void addSymbol(String id, String name, SymbolKind kind, SyntaxNode node) {
        graph.getOrCreateNode(id, name, kind, locationOf(node))
            .getMetadata(); // ensure created
    }

    private Location locationOf(SyntaxNode node) {
        var r = node.getRange();
        return new Location(r.file(), r.startLine(), r.startColumn(), r.endLine(), r.endColumn());
    }

    private String qualify(String parent, String name) {
        return parent + "#" + name;
    }

    private String extractName(SyntaxNode node) {
        return node.getField("name")
            .map(SyntaxNode::getText)
            .orElseGet(() -> {
                // fallback: first identifier child
                for (SyntaxNode child : node.getChildren()) {
                    if (child.getType().equals("identifier")) {
                        return child.getText();
                    }
                }
                return "<anonymous>";
            });
    }

    private String signatureHint(SyntaxNode methodNode) {
        // Include parameter count to disambiguate overloads
        var paramsOpt = methodNode.getField("parameters");
        if (paramsOpt.isPresent()) {
            var params = paramsOpt.get();
            // JavaParserEngine uses variable_declarator for parameters;
            // fallback to counting all non-punctuation children
            int count = (int) params.getChildren().stream()
                .filter(c -> c.isNamed() && !c.getType().equals("(") && !c.getType().equals(")") && !c.getType().equals(","))
                .count();
            return "(" + count + ")";
        }
        return "()";
    }

    private void extractInheritance(SyntaxNode classNode, String classId) {
        // Look for extends_clause and implements_clause children
        for (SyntaxNode child : classNode.getChildren()) {
            String ct = child.getType();
            if (ct.equals("extends_clause") || ct.equals("superclass")) {
                String superName = extractTypeName(child);
                if (superName != null) {
                    String superId = "unresolved:" + superName;
                    graph.getOrCreateNode(superId, superName, SymbolKind.CLASS, locationOf(child));
                    graph.addRelation(classId, superId, RelationType.INHERITS, child);
                }
            } else if (ct.equals("implements_clause") || ct.equals("super_interfaces")) {
                String ifaceName = extractTypeName(child);
                if (ifaceName != null) {
                    String ifaceId = "unresolved:" + ifaceName;
                    graph.getOrCreateNode(ifaceId, ifaceName, SymbolKind.INTERFACE, locationOf(child));
                    graph.addRelation(classId, ifaceId, RelationType.IMPLEMENTS, child);
                }
            }
        }
    }

    private String extractTypeName(SyntaxNode node) {
        return node.findFirst(n -> n.getType().equals("identifier") || n.getType().equals("type_identifier"))
            .map(SyntaxNode::getText)
            .orElse(null);
    }

    private String extractMethodTarget(SyntaxNode invocationNode) {
        // method_invocation: method_select + arguments
        return invocationNode.getField("name")
            .map(SyntaxNode::getText)
            .orElseGet(() -> {
                for (SyntaxNode child : invocationNode.getChildren()) {
                    if (child.getType().equals("identifier")) {
                        return child.getText();
                    }
                }
                return null;
            });
    }
}
