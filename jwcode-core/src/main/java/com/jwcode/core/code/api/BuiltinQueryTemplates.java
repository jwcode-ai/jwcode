package com.jwcode.core.code.api;

/**
 * 内置查询模板库
 */
public enum BuiltinQueryTemplates {
    
    // Java 查询
    JAVA_PUBLIC_METHODS("java", "public-methods", 
        "(method_declaration (modifiers (modifier \"public\")) name: (identifier) @name)",
        "查找所有 public 方法"),
    
    JAVA_TEST_METHODS("java", "test-methods",
        "(method_declaration (modifiers (annotation name: (identifier) @anno (#eq? @anno \"Test\"))) @method)",
        "查找所有测试方法（带 @Test 注解）"),
    
    JAVA_CLASSES("java", "classes",
        "(class_declaration name: (identifier) @name) @class",
        "查找所有类定义"),
    
    JAVA_IMPORTS("java", "imports",
        "(import_declaration (scoped_identifier) @import) @stmt",
        "查找所有导入语句"),
    
    // Rust 查询
    RUST_FUNCTIONS("rust", "functions",
        "(function_item name: (identifier) @name) @func",
        "查找所有函数"),
    
    RUST_STRUCTS("rust", "structs",
        "(struct_item name: (identifier) @name) @struct",
        "查找所有结构体"),
    
    // TypeScript 查询
    TS_FUNCTIONS("typescript", "functions",
        "(function_declaration name: (identifier) @name) @func",
        "查找所有函数"),
    
    TS_CLASSES("typescript", "classes", 
        "(class_declaration name: (type_identifier) @name) @class",
        "查找所有类");
    
    private final String language;
    private final String name;
    private final String pattern;
    private final String description;
    
    BuiltinQueryTemplates(String language, String name, String pattern, String description) {
        this.language = language;
        this.name = name;
        this.pattern = pattern;
        this.description = description;
    }
    
    public String getLanguage() { return language; }
    public String getName() { return name; }
    public String getPattern() { return pattern; }
    public String getDescription() { return description; }
    
    /**
     * 根据语言和名称查找模板
     */
    public static BuiltinQueryTemplates find(String language, String name) {
        for (BuiltinQueryTemplates t : values()) {
            if (t.language.equals(language) && t.name.equals(name)) {
                return t;
            }
        }
        return null;
    }
}