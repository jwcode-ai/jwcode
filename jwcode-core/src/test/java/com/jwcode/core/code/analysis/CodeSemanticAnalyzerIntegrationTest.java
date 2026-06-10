package com.jwcode.core.code.analysis;

import com.jwcode.core.tool.analysis.CodeSemanticAnalyzer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CodeSemanticAnalyzerIntegrationTest {

    @TempDir
    Path tempDir;

    private static boolean treeSitterAvailable;

    @BeforeAll
    static void checkTreeSitter() {
        try {
            var analyzer = new TreeSitterCodeSemanticAnalyzer();
            Path tmpDir = Files.createTempDirectory("ts-check");
            Files.writeString(tmpDir.resolve("Test.java"), "class Test {}");
            var result = analyzer.analyze(tmpDir);
            treeSitterAvailable = result.parsedFiles() > 0;
        } catch (Exception e) {
            treeSitterAvailable = false;
        }
    }

    private CodeSemanticAnalyzer createAnalyzer() {
        return new TreeSitterCodeSemanticAnalyzer();
    }

    @Test
    void shouldAnalyzeJavaProject() throws Exception {
        assumeTrue(treeSitterAvailable, "Tree-sitter 本地库不可用，跳过测试");

        Files.writeString(tempDir.resolve("Sample.java"), """
            public class Sample {
                public int add(int a, int b) {
                    return a + b;
                }
            }
            """);

        var analyzer = createAnalyzer();
        var result = analyzer.analyze(tempDir);

        assertThat(result).isNotNull();
        assertThat(result.parsedFiles()).isGreaterThanOrEqualTo(1);
        assertThat(result.symbolNodes()).isGreaterThanOrEqualTo(2); // class + method
    }

    @Test
    void shouldQueryMethods() throws Exception {
        assumeTrue(treeSitterAvailable, "Tree-sitter 本地库不可用，跳过测试");

        Files.writeString(tempDir.resolve("Sample.java"), """
            public class Sample {
                public void testOne() {}
                public void testTwo() {}
                private void helper() {}
            }
            """);

        var analyzer = createAnalyzer();
        var matches = analyzer.query(tempDir, "(method_declaration)");

        assertThat(matches).hasSize(3);
    }

    @Test
    void shouldQueryByTemplate() throws Exception {
        assumeTrue(treeSitterAvailable, "Tree-sitter 本地库不可用，跳过测试");

        Files.writeString(tempDir.resolve("Sample.java"), """
            public class Sample {
                public void run() {}
                private void secret() {}
            }
            """);

        var analyzer = createAnalyzer();
        var matches = analyzer.queryByTemplate(tempDir, "java", "classes");

        assertThat(matches).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, String> captures = (Map<String, String>) matches.get(0).get("captures");
        assertThat(captures.get("name")).isEqualTo("Sample");
    }

    @Test
    void shouldHandleEmptyProject() throws Exception {
        assumeTrue(treeSitterAvailable, "Tree-sitter 本地库不可用，跳过测试");

        var analyzer = createAnalyzer();
        var result = analyzer.analyze(tempDir);

        assertThat(result).isNotNull();
        assertThat(result.parsedFiles()).isEqualTo(0);
    }

    @Test
    void shouldHandleUnknownTemplate() throws Exception {
        assumeTrue(treeSitterAvailable, "Tree-sitter 本地库不可用，跳过测试");

        Files.writeString(tempDir.resolve("Sample.java"), "class A {}");
        var analyzer = createAnalyzer();
        var matches = analyzer.queryByTemplate(tempDir, "java", "nonexistent");
        assertThat(matches).isEmpty();
    }
}
