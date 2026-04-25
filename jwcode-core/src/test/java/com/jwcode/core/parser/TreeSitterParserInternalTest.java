package com.jwcode.core.parser;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterParserInternalTest {

    private final TreeSitterParser parser = new TreeSitterParser();

    @Test
    void shouldParseJavaFile() throws Exception {
        String source = """
            package com.example;

            import java.util.List;

            public class Sample {
                private int value;

                public Sample(int v) {
                    this.value = v;
                }

                public int calculate(int x) {
                    if (x > 0) {
                        return x * 2;
                    }
                    for (int i = 0; i < 10; i++) {
                        x += i;
                    }
                    return x;
                }
            }
            """;

        Path temp = Files.createTempFile("Sample", ".java");
        Files.writeString(temp, source);
        TreeSitterParser.ParseResult result = parser.parseFile(temp);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getClasses()).containsExactly("Sample");
        assertThat(result.getMethods()).containsExactly("<init>", "calculate");
        assertThat(result.getFields()).containsExactly("value");
        assertThat(result.getImports()).containsExactly("import java.util.List;");
        // complexity includes all control-flow nodes in the full AST
        assertThat(result.getComplexity()).isGreaterThanOrEqualTo(3);
        Files.deleteIfExists(temp);
    }

    @Test
    void shouldParsePythonFile() throws Exception {
        String source = """
            import os

            class Person:
                def __init__(self, name):
                    self.name = name

                def greet(self):
                    if self.name:
                        print(f"Hello {self.name}")
                    for i in range(3):
                        print(i)
            """;

        Path temp = Files.createTempFile("person", ".py");
        Files.writeString(temp, source);
        TreeSitterParser.ParseResult result = parser.parseFile(temp);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getClasses()).containsExactly("Person");
        assertThat(result.getMethods()).containsExactly("__init__", "greet");
        assertThat(result.getImports()).containsExactly("import os");
        // complexity: base 1 + if(1) + for(1) = 3
        assertThat(result.getComplexity()).isEqualTo(3);
        Files.deleteIfExists(temp);
    }

    @Test
    void shouldParseJavaScriptFile() throws Exception {
        String source = """
            import { helper } from './helper';

            class Calculator {
                constructor() {
                    this.result = 0;
                }

                add(x) {
                    if (x > 0) {
                        this.result += x;
                    }
                    return this.result;
                }
            }
            """;

        Path temp = Files.createTempFile("calc", ".js");
        Files.writeString(temp, source);
        TreeSitterParser.ParseResult result = parser.parseFile(temp);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getClasses()).containsExactly("Calculator");
        assertThat(result.getMethods()).containsExactly("constructor", "add");
        Files.deleteIfExists(temp);
    }

    @Test
    void shouldParseGoFile() throws Exception {
        String source = """
            package main

            import "fmt"

            type Person struct {
                Name string
            }

            func (p *Person) Greet() {
                if p.Name != "" {
                    fmt.Println(p.Name)
                }
            }
            """;

        Path temp = Files.createTempFile("main", ".go");
        Files.writeString(temp, source);
        TreeSitterParser.ParseResult result = parser.parseFile(temp);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getClasses()).containsExactly("Person");
        assertThat(result.getMethods()).containsExactly("Greet");
        assertThat(result.getImports()).containsExactly("\"fmt\"");
        Files.deleteIfExists(temp);
    }

    @Test
    void treeCursorShouldTraverseDepthFirst() throws Exception {
        String source = """
            class A {
                void m1() {}
                void m2() {}
            }
            """;

        Path temp = Files.createTempFile("A", ".java");
        Files.writeString(temp, source);
        TreeSitterParser.ParseResult result = parser.parseFile(temp);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getClasses()).containsExactly("A");
        assertThat(result.getMethods()).containsExactly("m1", "m2");
        Files.deleteIfExists(temp);
    }
}
