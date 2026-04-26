package com.jwcode.core.code.engine.treesitter;

import com.jwcode.core.code.api.SyntaxNode;
import com.jwcode.core.code.api.SyntaxTree;
import com.jwcode.core.parser.internal.javaparser.JavaParserEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterSyntaxAdapterTest {

    private static final String JAVA_SOURCE = """
        package com.example;

        public class Sample {
            private int value;

            public Sample(int v) {
                this.value = v;
            }

            public int calculate(int x) {
                return x * 2;
            }
        }
        """;

    private SyntaxTree parseJava(String source) {
        var tree = new JavaParserEngine().parse(source);
        assertThat(tree).isNotNull();
        return new TreeSitterSyntaxTree(tree, "Test.java");
    }

    @Test
    void shouldAdaptTreeProperties() {
        SyntaxTree st = parseJava(JAVA_SOURCE);
        assertThat(st.getLanguage()).isEqualTo("java");
        assertThat(st.getFilePath()).isEqualTo("Test.java");
        assertThat(st.getSource()).isEqualTo(JAVA_SOURCE);
        assertThat(st.getRootNode()).isNotNull();
    }

    @Test
    void shouldNavigateNodes() {
        SyntaxTree st = parseJava(JAVA_SOURCE);
        SyntaxNode root = st.getRootNode();
        assertThat(root.getType()).isEqualTo("program");

        var classNodeOpt = root.findFirst(n -> n.getType().equals("class_declaration"));
        assertThat(classNodeOpt).isPresent();
        SyntaxNode classNode = classNodeOpt.get();

        assertThat(classNode.getField("name")).isPresent();
        assertThat(classNode.getField("name").get().getText()).isEqualTo("Sample");

        var methods = classNode.findAll(n -> n.getType().equals("method_declaration"));
        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).getField("name").get().getText()).isEqualTo("calculate");

        var constructors = classNode.findAll(n -> n.getType().equals("constructor_declaration"));
        assertThat(constructors).hasSize(1);
    }

    @Test
    void shouldSupportFindSelector() {
        SyntaxTree st = parseJava(JAVA_SOURCE);
        SyntaxNode root = st.getRootNode();

        // Walk the tree and collect node types to verify structure
        List<String> types = new ArrayList<>();
        root.findAll(n -> { types.add(n.getType()); return false; });
        assertThat(types).contains("class_declaration", "method_declaration");

        // find by direct child relationship
        var directChildren = root.find("program > class_declaration");
        assertThat(directChildren).isNotEmpty();
    }

    @Test
    void shouldGetNodeAtPosition() {
        SyntaxTree st = parseJava(JAVA_SOURCE);
        // Line 3 (0-indexed) is "    private int value;"
        // Column 10 falls inside "value"
        SyntaxNode node = st.getNodeAt(3, 10);
        assertThat(node).isNotNull();
        assertThat(node.getType()).isIn("variable_declarator", "identifier");
    }

    @Test
    void shouldSerializeToSexp() {
        SyntaxTree st = parseJava("class A {}");
        String sexp = st.toSexp();
        assertThat(sexp).contains("(program");
        assertThat(sexp).contains("class_declaration");
    }

    @Test
    void shouldProvideTreeWalker() {
        SyntaxTree st = parseJava("class A { void m() {} }");
        var walker = st.getWalker();
        assertThat(walker).isNotNull();

        int[] count = {0};
        walker.walk(n -> count[0]++);
        assertThat(count[0]).isGreaterThan(3);
    }
}
