package com.jwcode.parser;

import com.jwcode.parser.model.CodeSymbol;
import com.jwcode.parser.model.ParseResult;
import com.jwcode.parser.model.SemanticInfo;
import com.jwcode.parser.model.TypeDescriptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Java AST 解析器 — 基于正则表达式的轻量级实现。
 *
 * <p>在不依赖 Tree-sitter 原生库或 Python 服务的情况下，
 * 对 Java 源码进行 AST 解析和语义分析。
 * 支持：类/接口/枚举/注解/方法/字段/变量的提取，
 * 导入分析、包名提取、修饰符解析、类型引用分析。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>零外部依赖 — 仅使用标准库正则表达式</li>
 *   <li>容错解析 — 即使遇到语法错误也尽可能提取信息</li>
 *   <li>可组合 — 解析结果可被 TreeSitterClient 增强</li>
 * </ul>
 */
public class JavaAstParser {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;", Pattern.MULTILINE);

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([a-zA-Z_][\\w.]*(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);

    // 类/接口/枚举/注解/记录定义
    private static final Pattern TYPE_DECL_PATTERN = Pattern.compile(
            "(?<=^|\\n)\\s*" +
            "(?:(\\w+(?:\\s+\\w+)*)\\s+)?" +            // 修饰符组 (group 1)
            "(class|interface|@interface|enum|record)\\s+" + // 类型关键字 (group 2)
            "(\\w+)" +                                      // 类型名称 (group 3)
            "(?:\\s*<([^>]+))?" +                          // 泛型参数 (group 4)
            "(?:\\s+extends\\s+([^\\{]+?))?" +             // extends (group 5)
            "(?:\\s+implements\\s+([^\\{]+?))?" +          // implements (group 6)
            "\\s*\\{",
            Pattern.MULTILINE
    );

    // 方法定义
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(?<=^|\\n)\\s*" +
            "(?:(\\w+(?:\\s+\\w+)*)\\s+)?" +            // 修饰符+返回类型 (group 1)
            "(\\w+)" +                                      // 方法名 (group 2)
            "\\s*\\(" +
            "([^)]*)" +                                     // 参数列表 (group 3)
            "\\)" +
            "(?:\\s*throws\\s+([^{;]+))?" +               // throws (group 4)
            "\\s*(?:\\{|;)",
            Pattern.MULTILINE
    );

    // 字段/变量定义
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "(?<=^|\\n)\\s*" +
            "(?:(\\w+(?:\\s+\\w+)*)\\s+)?" +            // 修饰符+类型 (group 1)
            "(\\w+)" +                                      // 字段名 (group 2)
            "\\s*(?:=|;)",
            Pattern.MULTILINE
    );

    // 注解
    private static final Pattern ANNOTATION_PATTERN =
            Pattern.compile("@(\\w+)(?:\\(([^)]*)\\))?");

    // 字符串字面量（用于跳过注释和字符串）
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /**
     * 解析 Java 源码文件
     *
     * @param filePath Java 文件路径
     * @return 解析结果
     * @throws IOException 如果文件读取失败
     */
    public ParseResult parseFile(Path filePath) throws IOException {
        String source = Files.readString(filePath, StandardCharsets.UTF_8);
        return parseSource(source, filePath);
    }

    /**
     * 解析 Java 源码字符串
     *
     * @param source   Java 源码
     * @param filePath 文件路径（用于错误报告）
     * @return 解析结果
     */
    public ParseResult parseSource(String source, Path filePath) {
        ParseResult result = new ParseResult();
        result.setLanguage("java");

        // 预处理：移除注释和字符串字面量，保留行号
        String cleaned = preprocess(source);

        // 提取包名
        result.setPackageName(extractPackage(cleaned));

        // 提取导入
        result.setImports(extractImports(cleaned));

        // 提取类型定义
        List<CodeSymbol> symbols = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            symbols.addAll(extractTypeDeclarations(cleaned, source));
            symbols.addAll(extractMethods(cleaned, source));
            symbols.addAll(extractFields(cleaned, source));
        } catch (Exception e) {
            errors.add("Parse warning: " + e.getMessage());
        }

        // 建立父子关系
        buildHierarchy(symbols);

        result.setSymbols(symbols);
        result.setErrors(errors);
        return result;
    }

    /**
     * 执行语义分析 — 提取类型引用、方法调用等语义信息
     *
     * @param source Java 源码
     * @param parseResult 已有的解析结果
     * @return 语义分析结果
     */
    public SemanticInfo analyzeSemantics(String source, ParseResult parseResult) {
        SemanticInfo info = new SemanticInfo();
        String cleaned = preprocess(source);

        // 提取类型引用（new 表达式、类型转换等）
        Set<String> typeReferences = new LinkedHashSet<>();
        Pattern newPattern = Pattern.compile("new\\s+(\\w+)");
        Matcher newMatcher = newPattern.matcher(cleaned);
        while (newMatcher.find()) {
            typeReferences.add(newMatcher.group(1));
        }

        // 提取方法调用
        List<SemanticInfo.MethodCall> methodCalls = new ArrayList<>();
        Pattern callPattern = Pattern.compile("(\\w+)\\.(\\w+)\\s*\\(");
        Matcher callMatcher = callPattern.matcher(cleaned);
        while (callMatcher.find()) {
            String target = callMatcher.group(1);
            String methodName = callMatcher.group(2);
            if (!isKeyword(target)) {
                methodCalls.add(new SemanticInfo.MethodCall(target, methodName));
            }
        }

        // 提取类型引用（从 import 和 extends/implements）
        if (parseResult.getImports() != null) {
            for (String imp : parseResult.getImports()) {
                if (!imp.endsWith(".*")) {
                    int lastDot = imp.lastIndexOf('.');
                    if (lastDot > 0) {
                        typeReferences.add(imp.substring(lastDot + 1));
                    }
                }
            }
        }

        info.setTypeReferences(new ArrayList<>(typeReferences));
        info.setMethodCalls(methodCalls);
        info.setImportCount(parseResult.getImports() != null ? parseResult.getImports().size() : 0);
        info.setSymbolCount(parseResult.getSymbols() != null ? parseResult.getSymbols().size() : 0);

        return info;
    }

    // ==================== 内部解析方法 ====================

    /**
     * 预处理：移除注释，将字符串字面量替换为占位符。
     * 注释被完全移除，字符串字面量被替换为 "" 以保持行号结构。
     *
     * <p>处理顺序：先保护字符串字面量（替换为占位符），再移除注释，
     * 防止字符串内的 // 或 /* 被误移除。</p>
     */
    String preprocess(String source) {
        // 先保护字符串字面量
        String protected_ = STRING_LITERAL.matcher(source).replaceAll("\"\"");
        // 再移除注释
        String result = LINE_COMMENT.matcher(protected_).replaceAll("");
        result = BLOCK_COMMENT.matcher(result).replaceAll("");
        return result;
    }

    private String extractPackage(String cleaned) {
        Matcher m = PACKAGE_PATTERN.matcher(cleaned);
        return m.find() ? m.group(1) : null;
    }

    private List<String> extractImports(String cleaned) {
        List<String> imports = new ArrayList<>();
        Matcher m = IMPORT_PATTERN.matcher(cleaned);
        while (m.find()) {
            imports.add(m.group(1));
        }
        return imports;
    }

    private List<CodeSymbol> extractTypeDeclarations(String cleaned, String originalSource) {
        List<CodeSymbol> symbols = new ArrayList<>();
        Matcher m = TYPE_DECL_PATTERN.matcher(cleaned);
        while (m.find()) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setKind(mapTypeKind(m.group(2)));
            symbol.setName(m.group(3));

            // 修饰符
            String modifiers = m.group(1);
            if (modifiers != null) {
                symbol.setModifiers(Arrays.asList(modifiers.split("\\s+")));
            }

            // 签名
            StringBuilder sig = new StringBuilder();
            if (m.group(4) != null) sig.append("<").append(m.group(4)).append(">");
            if (m.group(5) != null) sig.append(" extends ").append(m.group(5).trim());
            if (m.group(6) != null) sig.append(" implements ").append(m.group(6).trim());
            symbol.setSignature(sig.length() > 0 ? sig.toString().trim() : null);

            // 行号（在原始源码中查找）
            int startPos = findInOriginal(originalSource, m.group(0).substring(0, Math.min(m.group(0).length(), 20)));
            if (startPos >= 0) {
                symbol.setStartLine(lineNumber(originalSource, startPos));
            }

            symbols.add(symbol);
        }
        return symbols;
    }

    private List<CodeSymbol> extractMethods(String cleaned, String originalSource) {
        List<CodeSymbol> symbols = new ArrayList<>();
        Matcher m = METHOD_PATTERN.matcher(cleaned);
        while (m.find()) {
            String modifiersAndReturn = m.group(1);
            String methodName = m.group(2);

            // 过滤：不是关键字开头的才是方法
            if (isKeyword(methodName) || isTypeKeyword(m.group(0))) {
                continue;
            }

            CodeSymbol symbol = new CodeSymbol();
            symbol.setKind(CodeSymbol.SymbolKind.METHOD);
            symbol.setName(methodName);

            // 解析修饰符和返回类型
            if (modifiersAndReturn != null) {
                String[] parts = modifiersAndReturn.split("\\s+");
                List<String> mods = new ArrayList<>();
                StringBuilder returnType = new StringBuilder();
                for (String part : parts) {
                    if (isModifier(part)) {
                        mods.add(part);
                    } else {
                        if (returnType.length() > 0) returnType.append(" ");
                        returnType.append(part);
                    }
                }
                symbol.setModifiers(mods);
                symbol.setReturnType(returnType.toString());
            }

            // 参数列表
            String params = m.group(3);
            symbol.setSignature("(" + params.trim() + ")");
            if (m.group(4) != null) {
                symbol.setSignature(symbol.getSignature() + " throws " + m.group(4).trim());
            }

            // 行号
            int startPos = findInOriginal(originalSource, methodName);
            if (startPos >= 0) {
                symbol.setStartLine(lineNumber(originalSource, startPos));
            }

            symbols.add(symbol);
        }
        return symbols;
    }

    private List<CodeSymbol> extractFields(String cleaned, String originalSource) {
        List<CodeSymbol> symbols = new ArrayList<>();
        Matcher m = FIELD_PATTERN.matcher(cleaned);
        while (m.find()) {
            String typeAndModifiers = m.group(1);
            String fieldName = m.group(2);

            if (isKeyword(fieldName) || fieldName.equals(extractPackage(cleaned))) {
                continue;
            }

            CodeSymbol symbol = new CodeSymbol();
            symbol.setKind(CodeSymbol.SymbolKind.FIELD);
            symbol.setName(fieldName);

            if (typeAndModifiers != null) {
                String[] parts = typeAndModifiers.split("\\s+");
                List<String> mods = new ArrayList<>();
                StringBuilder type = new StringBuilder();
                for (String part : parts) {
                    if (isModifier(part)) {
                        mods.add(part);
                    } else {
                        if (type.length() > 0) type.append(" ");
                        type.append(part);
                    }
                }
                symbol.setModifiers(mods);
                symbol.setReturnType(type.toString());
            }

            int startPos = findInOriginal(originalSource, fieldName);
            if (startPos >= 0) {
                symbol.setStartLine(lineNumber(originalSource, startPos));
            }

            symbols.add(symbol);
        }
        return symbols;
    }

    /**
     * 建立符号间的父子层次关系
     */
    private void buildHierarchy(List<CodeSymbol> symbols) {
        // 按行号排序
        List<CodeSymbol> sorted = new ArrayList<>(symbols);
        sorted.sort(Comparator.comparingInt(CodeSymbol::getStartLine));

        Map<String, CodeSymbol> symbolMap = new LinkedHashMap<>();
        for (CodeSymbol s : sorted) {
            symbolMap.put(s.getName(), s);
        }

        // 类型作为根节点，方法和字段作为子节点
        CodeSymbol currentType = null;
        for (CodeSymbol s : sorted) {
            if (s.getKind() == CodeSymbol.SymbolKind.CLASS ||
                s.getKind() == CodeSymbol.SymbolKind.INTERFACE ||
                s.getKind() == CodeSymbol.SymbolKind.ENUM) {
                currentType = s;
            } else if (currentType != null &&
                       (s.getKind() == CodeSymbol.SymbolKind.METHOD ||
                        s.getKind() == CodeSymbol.SymbolKind.FIELD)) {
                s.setParent(currentType.getName());
                if (currentType.getChildren() == null) {
                    currentType.setChildren(new ArrayList<>());
                }
                currentType.getChildren().add(s.getName());
            }
        }
    }

    // ==================== 辅助方法 ====================

    private CodeSymbol.SymbolKind mapTypeKind(String keyword) {
        return switch (keyword) {
            case "class" -> CodeSymbol.SymbolKind.CLASS;
            case "interface" -> CodeSymbol.SymbolKind.INTERFACE;
            case "enum" -> CodeSymbol.SymbolKind.ENUM;
            case "@interface" -> CodeSymbol.SymbolKind.ANNOTATION;
            case "record" -> CodeSymbol.SymbolKind.CLASS;
            default -> CodeSymbol.SymbolKind.CLASS;
        };
    }

    private boolean isKeyword(String word) {
        return switch (word) {
            case "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                 "class", "const", "continue", "default", "do", "double", "else", "enum",
                 "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                 "import", "instanceof", "int", "interface", "long", "native", "new",
                 "package", "private", "protected", "public", "return", "short", "static",
                 "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
                 "transient", "try", "void", "volatile", "while", "true", "false", "null",
                 "var", "yield", "sealed", "permits", "record" -> true;
            default -> false;
        };
    }

    private boolean isTypeKeyword(String text) {
        return text.contains(" class ") || text.contains(" interface ") ||
               text.contains(" enum ") || text.contains(" record ");
    }

    private boolean isModifier(String word) {
        return switch (word) {
            case "public", "private", "protected", "static", "final", "abstract",
                 "synchronized", "volatile", "transient", "native", "strictfp",
                 "default", "sealed", "non-sealed" -> true;
            default -> false;
        };
    }

    private int findInOriginal(String source, String fragment) {
        int idx = source.indexOf(fragment);
        return idx >= 0 ? idx : -1;
    }

    private int lineNumber(String source, int position) {
        int line = 1;
        for (int i = 0; i < position && i < source.length(); i++) {
            if (source.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * 解析类型描述符（从类型声明中提取）
     */
    public TypeDescriptor parseTypeDescriptor(String cleaned, String typeName) {
        TypeDescriptor desc = new TypeDescriptor();
        desc.setName(typeName);

        // 查找类型声明
        String regex = "(?:public|private|protected|static|final|abstract|sealed)\\s+" +
                       "(class|interface|enum|@interface|record)\\s+" + typeName +
                       "(?:<([^>]+))?" +
                       "(?:\\s+extends\\s+(\\w+(?:\\.\\w+)*(?:<[^>]+>)?))?" +
                       "(?:\\s+implements\\s+([^\\{]+))?";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(cleaned);
        if (m.find()) {
            desc.setKind(m.group(1));
            desc.setSuperClass(m.group(3));
            if (m.group(4) != null) {
                desc.setInterfaces(Arrays.stream(m.group(4).split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()));
            }
        }

        return desc;
    }
}
