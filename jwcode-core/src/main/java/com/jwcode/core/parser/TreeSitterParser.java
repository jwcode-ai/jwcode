package com.jwcode.core.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jwcode.core.parser.internal.LanguageRegistry;
import com.jwcode.core.parser.treesitter.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tree-sitter Parser Wrapper for JwCode
 * 
 * Provides:
 * - Code structure analysis (classes, methods, fields)
 * - Dependency relationship extraction
 * - Complexity metrics calculation
 * 
 * Note: Requires tree-sitter language bindings (bundled or downloaded)
 */
public class TreeSitterParser {
    
    private static final Logger logger = LoggerFactory.getLogger(TreeSitterParser.class);
    
    private final Parser parser;
    private final Map<String, Language> languageCache = new ConcurrentHashMap<>();
    private final Map<Path, ParseResult> parseResultCache = new ConcurrentHashMap<>();
    
    public TreeSitterParser() {
        this.parser = new Parser();
    }
    
    /**
     * Parse a single file and return structured result
     */
    public ParseResult parseFile(Path filePath) {
        return parseResultCache.computeIfAbsent(filePath, this::doParse);
    }
    
    /**
     * Parse a single file with explicit language
     */
    public ParseResult parseFile(Path filePath, String language) {
        return doParseWithLanguage(filePath, language);
    }
    
    /**
     * Batch parse multiple files
     */
    public Map<String, ParseResult> parseBatch(Map<String, Path> files) {
        Map<String, ParseResult> results = new ConcurrentHashMap<>();
        files.forEach((key, path) -> {
            try {
                results.put(key, parseFile(path));
            } catch (Exception e) {
                logger.warn("Failed to parse {}: {}", path, e.getMessage());
                results.put(key, ParseResult.error(key, e.getMessage()));
            }
        });
        return results;
    }
    
    private ParseResult doParse(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String extension = getExtension(filePath);
            String language = mapExtensionToLanguage(extension);
            
            return doParseWithLanguage(filePath, language);
        } catch (IOException e) {
            return ParseResult.error(filePath.toString(), "Read error: " + e.getMessage());
        }
    }
    
    private ParseResult doParseWithLanguage(Path filePath, String language) {
        try {
            Language lang = getLanguage(language);
            if (lang == null) {
                return ParseResult.error(filePath.toString(), "Language not available: " + language);
            }
            parser.setLanguage(lang);
            
            String content = Files.readString(filePath);
            
            // Parse returns Optional<Tree>
            Optional<Tree> treeOpt = parser.parse(content);
            if (treeOpt.isEmpty()) {
                return ParseResult.error(filePath.toString(), "Parse failed");
            }
            Tree tree = treeOpt.get();
            
            return analyzeTree(filePath.toString(), content, tree, language);
        } catch (Exception e) {
            return ParseResult.error(filePath.toString(), e.getMessage());
        }
    }
    
    private Language getLanguage(String languageName) {
        return languageCache.computeIfAbsent(languageName, name -> {
            Language loaded = LanguageRegistry.forExtension(name);
            logger.debug("Loaded language '{}' for extension '{}'", loaded.getName(), name);
            return loaded;
        });
    }
    
    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1).toLowerCase() : "";
    }
    
    private String mapExtensionToLanguage(String extension) {
        return switch (extension) {
            case "java" -> "java";
            case "py" -> "python";
            case "js", "jsx", "mjs" -> "javascript";
            case "ts", "tsx" -> "typescript";
            case "go" -> "go";
            case "rs" -> "rust";
            case "c", "h" -> "c";
            case "cpp", "cc", "cxx", "hpp" -> "cpp";
            case "cs" -> "c_sharp";
            case "rb" -> "ruby";
            case "php" -> "php";
            case "swift" -> "swift";
            case "kt", "kts" -> "kotlin";
            case "scala" -> "scala";
            default -> "tree-sitter";
        };
    }
    
    private ParseResult analyzeTree(String filePath, String content, Tree tree, String language) {
        Node rootNode = tree.getRootNode();
        
        List<String> classes = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        List<String> functions = new ArrayList<>();
        
        // Walk the AST and collect results
        try (TreeCursor cursor = rootNode.walk()) {
            collectNodes(cursor, language, classes, methods, fields, imports, functions);
        }
        
        // Calculate complexity
        int complexity = calculateComplexity(rootNode);
        int linesOfCode = content.split("\n").length;
        
        return new ParseResult(
            filePath,
            language,
            classes.size(),
            methods.size(),
            fields.size(),
            functions.size(),
            imports.size(),
            classes,
            methods,
            fields,
            imports,
            functions,
            complexity,
            linesOfCode,
            null
        );
    }
    
    private void collectNodes(TreeCursor cursor, String language,
                               List<String> classes, List<String> methods,
                               List<String> fields, List<String> imports, List<String> functions) {
        // Depth-first traversal using tree-cursor API
        boolean visited = false;
        while (true) {
            if (!visited) {
                Node currentNode = cursor.getCurrentNode();
                String type = currentNode.getType();

                String nodeName = extractNodeName(currentNode, language);

                if (isClassNode(type, language)) {
                    classes.add(nodeName);
                } else if (isMethodNode(type, language)) {
                    methods.add(nodeName);
                } else if (isFieldNode(type, language)) {
                    Node parent = currentNode.getParent();
                    if (parent != null && isClassNode(parent.getType(), language)) {
                        fields.add(nodeName);
                    }
                } else if (isFunctionNode(type, language)) {
                    functions.add(nodeName);
                } else if (isImportNode(type, language)) {
                    imports.add(nodeName);
                }
            }

            if (!visited && cursor.gotoFirstChild()) {
                visited = false;
                continue;
            }

            visited = true;
            if (cursor.gotoNextSibling()) {
                visited = false;
                continue;
            }

            if (!cursor.gotoParent()) {
                break;
            }
        }
    }
    
    private String extractNodeName(Node node, String language) {
        String type = node.getType();

        // For import declarations, return the full text (e.g., "import java.util.List;")
        if ("import_declaration".equals(type)) {
            return node.getText().trim();
        }

        long childCount = node.getChildCount();

        // Prefer explicit identifier child
        for (int i = 0; i < childCount; i++) {
            Optional<Node> childOpt = node.getChild(i);
            if (childOpt.isPresent() && "identifier".equals(childOpt.get().getType())) {
                String text = childOpt.get().getText();
                if (text != null && !text.isEmpty()) {
                    return text;
                }
            }
        }

        // Skip structural / modifier children
        for (int i = 0; i < childCount; i++) {
            Optional<Node> childOpt = node.getChild(i);
            if (childOpt.isPresent()) {
                Node child = childOpt.get();
                String childType = child.getType();
                if ("modifiers".equals(childType) || "block".equals(childType)
                        || "class_body".equals(childType) || "package".equals(childType)) {
                    continue;
                }
                String text = child.getText();
                if (text != null && !text.isEmpty() && !text.matches("[{}\\[\\]();]")) {
                    return text;
                }
            }
        }
        return node.getText().trim();
    }
    
    private boolean isClassNode(String type, String language) {
        return switch (language) {
            case "java" -> type.equals("class_declaration") || type.equals("interface_declaration")
                    || type.equals("enum_declaration") || type.equals("annotation_type_declaration");
            case "python" -> type.equals("class_definition") || type.equals("class_declaration");
            case "javascript", "typescript" -> type.equals("class_declaration") || type.equals("class_expression");
            case "go" -> type.equals("type_declaration") || type.equals("type_spec") || type.equals("class_declaration");
            default -> type.contains("class");
        };
    }
    
    private boolean isMethodNode(String type, String language) {
        return switch (language) {
            case "java" -> type.equals("method_declaration") || type.equals("constructor_declaration");
            case "python" -> type.equals("function_definition") || type.equals("function_declaration");
            case "javascript", "typescript" -> type.equals("function_declaration") || type.equals("method_definition");
            case "go" -> type.equals("function_declaration");
            default -> type.contains("method") || type.contains("function");
        };
    }
    
    private boolean isFieldNode(String type, String language) {
        return switch (language) {
            case "java" -> type.equals("field_declaration") || type.equals("variable_declarator");
            case "python" -> type.equals("assignment") || type.equals("annassign") || type.equals("field_declaration");
            case "javascript", "typescript" -> type.equals("property_definition") || type.equals("variable_declaration")
                    || type.equals("field_declaration");
            default -> type.contains("field") || type.contains("variable");
        };
    }
    
    private boolean isFunctionNode(String type, String language) {
        return type.equals("function_declaration") || type.equals("function_definition") 
            || type.equals("function_expression");
    }
    
    private boolean isImportNode(String type, String language) {
        return type.equals("import_declaration") || type.equals("import_statement")
            || type.equals("import") || type.equals("require_call");
    }
    
    private int calculateComplexity(Node node) {
        if (node == null) return 1;
        
        int complexity = 1;
        String type = node.getType();
        
        // Increment for control flow structures
        if (type.contains("if") || type.contains("else") || type.contains("case")
            || type.contains("for") || type.contains("while") || type.contains("catch")
            || type.contains("conditional") || type.contains("switch")) {
            complexity++;
        }
        
        // Recurse children
        long childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            Optional<Node> childOpt = node.getChild(i);
            if (childOpt.isPresent()) {
                Node child = childOpt.get();
                complexity += calculateComplexity(child) - 1;
            }
        }
        
        return complexity;
    }
    
    /**
     * Clear cache
     */
    public void clearCache() {
        parseResultCache.clear();
    }
    
    @FunctionalInterface
    private interface NodeConsumer {
        void accept(String type, String name);
    }
    
    /**
     * Parse result data class
     */
    public static class ParseResult {
        private final String filePath;
        private final String language;
        private final int classCount;
        private final int methodCount;
        private final int fieldCount;
        private final int functionCount;
        private final int importCount;
        private final List<String> classes;
        private final List<String> methods;
        private final List<String> fields;
        private final List<String> imports;
        private final List<String> functions;
        private final int complexity;
        private final int linesOfCode;
        private final String error;
        
        private ParseResult(String filePath, String language, int classCount, int methodCount,
                          int fieldCount, int functionCount, int importCount,
                          List<String> classes, List<String> methods, List<String> fields,
                          List<String> imports, List<String> functions, int complexity,
                          int linesOfCode, String error) {
            this.filePath = filePath;
            this.language = language;
            this.classCount = classCount;
            this.methodCount = methodCount;
            this.fieldCount = fieldCount;
            this.functionCount = functionCount;
            this.importCount = importCount;
            this.classes = classes;
            this.methods = methods;
            this.fields = fields;
            this.imports = imports;
            this.functions = functions;
            this.complexity = complexity;
            this.linesOfCode = linesOfCode;
            this.error = error;
        }
        
        public static ParseResult error(String filePath, String error) {
            return new ParseResult(filePath, null, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, error);
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public String getLanguage() { return language; }
        public int getClassCount() { return classCount; }
        public int getMethodCount() { return methodCount; }
        public int getFieldCount() { return fieldCount; }
        public int getFunctionCount() { return functionCount; }
        public int getImportCount() { return importCount; }
        public List<String> getClasses() { return classes; }
        public List<String> getMethods() { return methods; }
        public List<String> getFields() { return fields; }
        public List<String> getImports() { return imports; }
        public List<String> getFunctions() { return functions; }
        public int getComplexity() { return complexity; }
        public int getLinesOfCode() { return linesOfCode; }
        public String getError() { return error; }
        public boolean isSuccess() { return error == null; }
        
        /**
         * Get a summary string
         */
        public String getSummary() {
            if (error != null) {
                return "Error: " + error;
            }
            return String.format("%s: %d classes, %d methods, %d fields, complexity=%d, LOC=%d",
                filePath, classCount, methodCount, fieldCount, complexity, linesOfCode);
        }
    }
}