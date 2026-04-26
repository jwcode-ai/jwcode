package com.jwcode.core.code.engine.treesitter;

import com.jwcode.core.code.api.QueryMatch;
import com.jwcode.core.code.api.SyntaxTree;
import com.jwcode.core.parser.internal.javaparser.JavaParserEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterSyntaxQueryTest {

    private static final String JAVA_SOURCE = """
        public class Sample {
            private int value;

            public Sample(int v) {
                this.value = v;
            }

            public int calculate(int x) {
                return x * 2;
            }

            private void helper() {
            }
        }
        """;

    private SyntaxTree parse(String source) {
        var tree = new JavaParserEngine().parse(source);
        return new TreeSitterSyntaxTree(tree, "Test.java");
    }

    @Test
    void shouldMatchMethodDeclarations() {
        var tree = parse(JAVA_SOURCE);
        var query = new TreeSitterSyntaxQuery("(method_declaration)");
        List<QueryMatch> matches = query.execute(tree);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).getText()).contains("calculate");
        assertThat(matches.get(1).getText()).contains("helper");
    }

    @Test
    void shouldMatchWithCapture() {
        var tree = parse(JAVA_SOURCE);
        var query = new TreeSitterSyntaxQuery("(method_declaration name: (identifier) @name)");
        List<QueryMatch> matches = query.execute(tree);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).getCapture("name").getText()).isEqualTo("calculate");
        assertThat(matches.get(1).getCapture("name").getText()).isEqualTo("helper");
    }

    @Test
    void shouldMatchClassDeclaration() {
        var tree = parse(JAVA_SOURCE);
        var query = new TreeSitterSyntaxQuery("(class_declaration) @class");
        List<QueryMatch> matches = query.execute(tree);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getCapture("class")).isNotNull();
    }

    @Test
    void shouldMatchWithEqPredicate() {
        var tree = parse(JAVA_SOURCE);
        var queryNoPred = new TreeSitterSyntaxQuery(
            "(method_declaration name: (identifier) @name)"
        );
        List<QueryMatch> noPredMatches = queryNoPred.execute(tree);
        assertThat(noPredMatches).hasSize(2);

        var query = new TreeSitterSyntaxQuery(
            "(method_declaration name: (identifier) @name (#eq? @name \"calculate\"))"
        );
        System.out.println("Predicates: " + query.getPredicateDebug());
        List<QueryMatch> matches = query.execute(tree);
        System.out.println("Matches with predicate: " + matches.size());

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getCapture("name").getText()).isEqualTo("calculate");
    }

    @Test
    void shouldMatchWithMatchPredicate() {
        var tree = parse(JAVA_SOURCE);
        var query = new TreeSitterSyntaxQuery(
            "(method_declaration name: (identifier) @name (#match? @name \"cal.*\"))"
        );
        List<QueryMatch> matches = query.execute(tree);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getCapture("name").getText()).isEqualTo("calculate");
    }

    @Test
    void shouldMatchConstructor() {
        var tree = parse(JAVA_SOURCE);
        var query = new TreeSitterSyntaxQuery("(constructor_declaration)");
        List<QueryMatch> matches = query.execute(tree);

        assertThat(matches).hasSize(1);
    }

    @Test
    void shouldReturnEmptyForNoMatch() {
        var tree = parse(JAVA_SOURCE);
        var query = new TreeSitterSyntaxQuery("(enum_declaration)");
        List<QueryMatch> matches = query.execute(tree);

        assertThat(matches).isEmpty();
    }
}
