package com.jwcode.core.parser.internal.javaparser;

import com.jwcode.core.parser.treesitter.Node;
import com.jwcode.core.parser.treesitter.Point;


import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Java source code using the JDK built-in compiler API and converts
 * the resulting {@link CompilationUnitTree} into our internal tree-sitter-compatible AST.
 */
public class JavaParserEngine {

    public com.jwcode.core.parser.treesitter.Tree parse(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return null;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        JavaFileObject file = new SourceStringObject("Test.java", source);
        Iterable<? extends JavaFileObject> units = List.of(file);

        JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, null, null, units);
        try {
            Iterable<? extends CompilationUnitTree> asts = task.parse();
            CompilationUnitTree unit = asts.iterator().next();

            Trees trees = Trees.instance(task);
            SourcePositions positions = trees.getSourcePositions();
            LineMap lineMap = unit.getLineMap();
            boolean hasErrors = !diagnostics.getDiagnostics().isEmpty();

            Node root = new AstConverter(source, unit, positions, lineMap, hasErrors)
                    .visitCompilationUnit(unit, null);
            return new com.jwcode.core.parser.treesitter.Tree(root, new JavaLanguage(), source);
        } catch (IOException e) {
            return null;
        }
    }

    private static class AstConverter extends TreeScanner<Node, Void> {
        private final String source;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final LineMap lineMap;
        private final boolean hasErrors;

        AstConverter(String source, CompilationUnitTree unit, SourcePositions positions,
                     LineMap lineMap, boolean hasErrors) {
            this.source = source;
            this.unit = unit;
            this.positions = positions;
            this.lineMap = lineMap;
            this.hasErrors = hasErrors;
        }

        private Node identifierNode(String name) {
            return Node.of("identifier", name, 0, 0, new Point(0, 0), new Point(0, 0), List.of(), true, false);
        }

        private Node createNode(String type, Tree tree, List<Node> children) {
            long startPos = positions.getStartPosition(unit, tree);
            long endPos = positions.getEndPosition(unit, tree);
            if (startPos < 0) startPos = 0;
            if (endPos < 0 || endPos < startPos) endPos = startPos;
            if (endPos > source.length()) endPos = source.length();

            int startByte = (int) startPos;
            int endByte = (int) endPos;

            long startLine = lineMap != null ? lineMap.getLineNumber(startPos) : 1;
            long startCol = lineMap != null ? lineMap.getColumnNumber(startPos) : 1;
            long endLine = lineMap != null ? lineMap.getLineNumber(endPos) : 1;
            long endCol = lineMap != null ? lineMap.getColumnNumber(endPos) : 1;

            String text = source.substring(startByte, endByte);
            Point startPoint = new Point((int) startLine - 1, (int) startCol - 1);
            Point endPoint = new Point((int) endLine - 1, (int) endCol - 1);

            return Node.of(type, text, startByte, endByte, startPoint, endPoint, children, true, hasErrors);
        }

        private List<Node> scanList(Iterable<? extends Tree> trees) {
            List<Node> result = new ArrayList<>();
            if (trees != null) {
                for (Tree t : trees) {
                    if (t != null) {
                        Node n = scan(t, null);
                        if (n != null) result.add(n);
                    }
                }
            }
            return result;
        }

        @SafeVarargs
        private final List<Node> scanAll(Iterable<? extends Tree>... iterables) {
            List<Node> result = new ArrayList<>();
            for (Iterable<? extends Tree> iterable : iterables) {
                result.addAll(scanList(iterable));
            }
            return result;
        }

        @Override
        public Node visitCompilationUnit(CompilationUnitTree node, Void p) {
            return createNode("program", node, scanAll(node.getPackageAnnotations(), node.getImports(), node.getTypeDecls()));
        }

        @Override
        public Node visitClass(ClassTree node, Void p) {
            String type = switch (node.getKind()) {
                case INTERFACE -> "interface_declaration";
                case ENUM -> "enum_declaration";
                case ANNOTATION_TYPE -> "annotation_type_declaration";
                default -> "class_declaration";
            };
            List<Node> children = new ArrayList<>();
            children.add(identifierNode(node.getSimpleName().toString()));
            if (node.getModifiers() != null) children.add(scan(node.getModifiers(), p));
            if (node.getExtendsClause() != null) children.add(scan(node.getExtendsClause(), p));
            node.getImplementsClause().forEach(t -> children.add(scan(t, p)));
            node.getTypeParameters().forEach(t -> children.add(scan(t, p)));
            node.getMembers().forEach(t -> children.add(scan(t, p)));
            return createNode(type, node, children);
        }

        @Override
        public Node visitMethod(MethodTree node, Void p) {
            String type = node.getName().toString().equals("<init>") ? "constructor_declaration" : "method_declaration";
            List<Node> children = new ArrayList<>();
            children.add(identifierNode(node.getName().toString()));
            if (node.getModifiers() != null) children.add(scan(node.getModifiers(), p));
            if (node.getReturnType() != null) children.add(scan(node.getReturnType(), p));
            node.getParameters().forEach(t -> children.add(scan(t, p)));
            node.getThrows().forEach(t -> children.add(scan(t, p)));
            if (node.getBody() != null) children.add(scan(node.getBody(), p));
            if (node.getDefaultValue() != null) children.add(scan(node.getDefaultValue(), p));
            return createNode(type, node, children);
        }

        @Override
        public Node visitVariable(VariableTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.add(identifierNode(node.getName().toString()));
            if (node.getType() != null) children.add(scan(node.getType(), p));
            if (node.getInitializer() != null) children.add(scan(node.getInitializer(), p));
            return createNode("variable_declarator", node, children);
        }

        @Override
        public Node visitImport(ImportTree node, Void p) {
            Node qual = scan(node.getQualifiedIdentifier(), p);
            return createNode("import_declaration", node, qual != null ? List.of(qual) : List.of());
        }

        @Override
        public Node visitBlock(BlockTree node, Void p) {
            return createNode("block", node, scanList(node.getStatements()));
        }

        @Override
        public Node visitIf(IfTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.add(scan(node.getCondition(), p));
            children.add(scan(node.getThenStatement(), p));
            if (node.getElseStatement() != null) children.add(scan(node.getElseStatement(), p));
            return createNode("if_statement", node, children);
        }

        @Override
        public Node visitForLoop(ForLoopTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.addAll(scanList(node.getInitializer()));
            children.add(scan(node.getCondition(), p));
            children.addAll(scanList(node.getUpdate()));
            children.add(scan(node.getStatement(), p));
            return createNode("for_statement", node, children);
        }

        @Override
        public Node visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
            return createNode("enhanced_for_statement", node, List.of(
                scan(node.getVariable(), p),
                scan(node.getExpression(), p),
                scan(node.getStatement(), p)
            ));
        }

        @Override
        public Node visitWhileLoop(WhileLoopTree node, Void p) {
            return createNode("while_statement", node, List.of(
                scan(node.getCondition(), p),
                scan(node.getStatement(), p)
            ));
        }

        @Override
        public Node visitDoWhileLoop(DoWhileLoopTree node, Void p) {
            return createNode("do_statement", node, List.of(
                scan(node.getStatement(), p),
                scan(node.getCondition(), p)
            ));
        }

        @Override
        public Node visitSwitch(SwitchTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.add(scan(node.getExpression(), p));
            children.addAll(scanList(node.getCases()));
            return createNode("switch_statement", node, children);
        }

        @Override
        public Node visitCase(CaseTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.addAll(scanList(node.getExpressions()));
            children.addAll(scanList(node.getStatements()));
            if (node.getBody() != null) children.add(scan(node.getBody(), p));
            return createNode("switch_block_statement_group", node, children);
        }

        @Override
        public Node visitTry(TryTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.add(scan(node.getBlock(), p));
            children.addAll(scanList(node.getCatches()));
            if (node.getFinallyBlock() != null) children.add(scan(node.getFinallyBlock(), p));
            return createNode("try_statement", node, children);
        }

        @Override
        public Node visitCatch(CatchTree node, Void p) {
            return createNode("catch_clause", node, List.of(
                scan(node.getParameter(), p),
                scan(node.getBlock(), p)
            ));
        }

        @Override
        public Node visitModifiers(ModifiersTree node, Void p) {
            return createNode("modifiers", node, scanList(node.getAnnotations()));
        }

        @Override
        public Node visitAnnotation(AnnotationTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.add(scan(node.getAnnotationType(), p));
            children.addAll(scanList(node.getArguments()));
            return createNode("annotation", node, children);
        }

        @Override
        public Node visitLambdaExpression(LambdaExpressionTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.addAll(scanList(node.getParameters()));
            children.add(scan(node.getBody(), p));
            return createNode("lambda_expression", node, children);
        }

        @Override
        public Node visitNewClass(NewClassTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.add(scan(node.getIdentifier(), p));
            children.addAll(scanList(node.getArguments()));
            if (node.getClassBody() != null) children.add(scan(node.getClassBody(), p));
            return createNode("object_creation_expression", node, children);
        }

        @Override
        public Node visitMethodInvocation(MethodInvocationTree node, Void p) {
            List<Node> children = new ArrayList<>();
            if (node.getMethodSelect() != null) children.add(scan(node.getMethodSelect(), p));
            children.addAll(scanList(node.getArguments()));
            return createNode("method_invocation", node, children);
        }

        @Override
        public Node visitExpressionStatement(ExpressionStatementTree node, Void p) {
            return createNode("expression_statement", node, List.of(scan(node.getExpression(), p)));
        }

        @Override
        public Node visitReturn(ReturnTree node, Void p) {
            List<Node> children = new ArrayList<>();
            if (node.getExpression() != null) children.add(scan(node.getExpression(), p));
            return createNode("return_statement", node, children);
        }

        @Override
        public Node visitThrow(ThrowTree node, Void p) {
            return createNode("throw_statement", node, List.of(scan(node.getExpression(), p)));
        }

        @Override
        public Node visitAssignment(AssignmentTree node, Void p) {
            return createNode("assignment_expression", node, List.of(
                scan(node.getVariable(), p),
                scan(node.getExpression(), p)
            ));
        }

        @Override
        public Node visitMemberSelect(MemberSelectTree node, Void p) {
            return createNode("field_access", node, List.of(scan(node.getExpression(), p)));
        }

        @Override
        public Node visitIdentifier(IdentifierTree node, Void p) {
            return createNode("identifier", node, List.of());
        }

        @Override
        public Node visitPrimitiveType(PrimitiveTypeTree node, Void p) {
            return createNode("primitive_type", node, List.of());
        }

        @Override
        public Node visitArrayType(ArrayTypeTree node, Void p) {
            return createNode("array_type", node, List.of(scan(node.getType(), p)));
        }

        @Override
        public Node visitParameterizedType(ParameterizedTypeTree node, Void p) {
            List<Node> children = new ArrayList<>();
            children.add(scan(node.getType(), p));
            children.addAll(scanList(node.getTypeArguments()));
            return createNode("parameterized_type", node, children);
        }

        @Override
        public Node visitLiteral(LiteralTree node, Void p) {
            return createNode("literal", node, List.of());
        }

        @Override
        public Node visitBreak(BreakTree node, Void p) {
            return createNode("break_statement", node, List.of());
        }

        @Override
        public Node visitContinue(ContinueTree node, Void p) {
            return createNode("continue_statement", node, List.of());
        }

        @Override
        public Node visitEmptyStatement(EmptyStatementTree node, Void p) {
            return createNode("empty_statement", node, List.of());
        }

    }

    private static class SourceStringObject extends SimpleJavaFileObject {
        private final String source;

        SourceStringObject(String name, String source) {
            super(URI.create("string:///" + name), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
