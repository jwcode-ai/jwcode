package com.jwcode.parser;

import com.jwcode.parser.model.CodeSymbol;
import com.jwcode.parser.model.ParseResult;
import com.jwcode.parser.model.SemanticInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JavaAstParser 单元测试
 */
@DisplayName("JavaAstParser 解析测试")
class JavaAstParserTest {

    private JavaAstParser parser;

    @BeforeEach
    void setUp() {
        parser = new JavaAstParser();
    }

    @Test
    @DisplayName("应正确解析包名")
    void shouldExtractPackage() {
        String source = "package com.jwcode.test;\n\npublic class Foo { }";
        ParseResult result = parser.parseSource(source, Path.of("Foo.java"));
        assertEquals("com.jwcode.test", result.getPackageName());
    }

    @Test
    @DisplayName("应正确解析导入语句")
    void shouldExtractImports() {
        String source = """
                package com.example;
                import java.util.List;
                import java.io.IOException;
                import static java.lang.Math.max;
                public class Foo { }
                """;
        ParseResult result = parser.parseSource(source, Path.of("Foo.java"));
        assertNotNull(result.getImports());
        assertTrue(result.getImports().contains("java.util.List"));
        assertTrue(result.getImports().contains("java.io.IOException"));
    }

    @Test
    @DisplayName("应正确解析类定义")
    void shouldParseClass() {
        String source = """
                package com.example;
                public class UserService {
                    private String name;
                    public String getName() { return name; }
                }
                """;
        ParseResult result = parser.parseSource(source, Path.of("UserService.java"));
        List<CodeSymbol> symbols = result.getSymbols();

        CodeSymbol clazz = symbols.stream()
                .filter(s -> s.getKind() == CodeSymbol.SymbolKind.CLASS)
                .findFirst().orElse(null);
        assertNotNull(clazz, "Should find class symbol");
        assertEquals("UserService", clazz.getName());
        assertTrue(clazz.getModifiers().contains("public"));
    }

    @Test
    @DisplayName("应正确解析方法定义")
    void shouldParseMethod() {
        String source = """
                package com.example;
                public class Calc {
                    public int add(int a, int b) { return a + b; }
                    private void log(String msg) { }
                }
                """;
        ParseResult result = parser.parseSource(source, Path.of("Calc.java"));
        List<CodeSymbol> methods = result.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbol.SymbolKind.METHOD)
                .toList();

        assertEquals(2, methods.size());
        assertTrue(methods.stream().anyMatch(m -> "add".equals(m.getName())));
        assertTrue(methods.stream().anyMatch(m -> "log".equals(m.getName())));
    }

    @Test
    @DisplayName("应正确解析字段定义")
    void shouldParseField() {
        String source = """
                package com.example;
                public class Person {
                    private String name;
                    public int age;
                    protected static final double PI = 3.14;
                }
                """;
        ParseResult result = parser.parseSource(source, Path.of("Person.java"));
        List<CodeSymbol> fields = result.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbol.SymbolKind.FIELD)
                .toList();

        assertTrue(fields.stream().anyMatch(f -> "name".equals(f.getName())));
        assertTrue(fields.stream().anyMatch(f -> "age".equals(f.getName())));
        assertTrue(fields.stream().anyMatch(f -> "PI".equals(f.getName())));
    }

    @Test
    @DisplayName("应正确建立父子层次关系")
    void shouldBuildHierarchy() {
        String source = """
                package com.example;
                public class Service {
                    private String data;
                    public void process() { }
                    public int count() { return 0; }
                }
                """;
        ParseResult result = parser.parseSource(source, Path.of("Service.java"));
        CodeSymbol clazz = result.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbol.SymbolKind.CLASS)
                .findFirst().orElse(null);

        assertNotNull(clazz);
        assertNotNull(clazz.getChildren());
        assertTrue(clazz.getChildren().contains("data"));
        assertTrue(clazz.getChildren().contains("process"));
        assertTrue(clazz.getChildren().contains("count"));

        // 方法和字段应有 parent 引用
        result.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbol.SymbolKind.METHOD ||
                             s.getKind() == CodeSymbol.SymbolKind.FIELD)
                .forEach(s -> assertEquals("Service", s.getParent()));
    }

    @Test
    @DisplayName("应正确执行语义分析")
    void shouldAnalyzeSemantics() {
        String source = """
                package com.example;
                import java.util.List;
                public class Analyzer {
                    public void run() {
                        List<String> items = new ArrayList<>();
                        System.out.println("hello");
                    }
                }
                """;
        ParseResult result = parser.parseSource(source, Path.of("Analyzer.java"));
        SemanticInfo info = parser.analyzeSemantics(source, result);

        assertNotNull(info);
        assertTrue(info.getImportCount() > 0);
        assertTrue(info.getSymbolCount() > 0);
        assertNotNull(info.getMethodCalls());
    }

    @Test
    @DisplayName("应正确处理空文件")
    void shouldHandleEmptyFile() {
        ParseResult result = parser.parseSource("", Path.of("Empty.java"));
        assertNotNull(result);
        assertTrue(result.getSymbols().isEmpty());
    }

    @Test
    @DisplayName("应正确处理接口定义")
    void shouldParseInterface() {
        String source = """
                package com.example;
                public interface DataSource {
                    String read();
                    void write(String data);
                }
                """;
        ParseResult result = parser.parseSource(source, Path.of("DataSource.java"));
        CodeSymbol iface = result.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbol.SymbolKind.INTERFACE)
                .findFirst().orElse(null);
        assertNotNull(iface, "Should find interface symbol");
        assertEquals("DataSource", iface.getName());
    }

    @Test
    @DisplayName("应正确解析枚举定义")
    void shouldParseEnum() {
        String source = """
                package com.example;
                public enum Status {
                    PENDING, ACTIVE, COMPLETED
                }
                """;
        ParseResult result = parser.parseSource(source, Path.of("Status.java"));
        CodeSymbol en = result.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbol.SymbolKind.ENUM)
                .findFirst().orElse(null);
        assertNotNull(en, "Should find enum symbol");
        assertEquals("Status", en.getName());
    }

    @Test
    @DisplayName("应正确预处理移除注释")
    void shouldRemoveComments() {
        String source = """
                // line comment
                package com.example;
                /* block comment */
                public class Foo {
                    // another comment
                    String s = "hello // not a comment";
                }
                """;
        String cleaned = parser.preprocess(source);
        assertFalse(cleaned.contains("line comment"), "Should remove line comments");
        assertFalse(cleaned.contains("block comment"), "Should remove block comments");
        // 字符串字面量被替换为 "" 占位符，避免字符串内的 // 被误移除
        assertTrue(cleaned.contains("\"\""), "String literals should be replaced with placeholder");
        // 字符串声明中的 // 没有被当作注释移除（因为先保护了字符串字面量）
        assertTrue(cleaned.contains("String s"), "String declaration should remain");
    }

    @Test
    @DisplayName("应正确处理 extends 和 implements")
    void shouldParseExtendsImplements() {
        String source = """
                package com.example;
                import java.io.Serializable;
                public class DataRecord extends BaseRecord implements Serializable, Cloneable {
                    private String value;
                }
                """;
        ParseResult result = parser.parseSource(source, Path.of("DataRecord.java"));
        CodeSymbol clazz = result.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbol.SymbolKind.CLASS)
                .findFirst().orElse(null);
        assertNotNull(clazz);
        assertNotNull(clazz.getSignature());
        assertTrue(clazz.getSignature().contains("extends"));
        assertTrue(clazz.getSignature().contains("implements"));
    }
}
