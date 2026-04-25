package com.jwcode.parser.model;

import lombok.Data;
import java.util.Collections;
import java.util.List;

/**
 * 文件解析结果
 */
@Data
public class ParseResult {
    private List<CodeSymbol> symbols;
    private List<String> imports;
    private String package;
    private String language;
    private List<String> errors;
    
    public ParseResult() {
        this.symbols = Collections.emptyList();
        this.imports = Collections.emptyList();
        this.errors = Collections.emptyList();
    }
    
    /**
     * 是否解析成功
     */
    public boolean isSuccess() {
        return errors == null || errors.isEmpty();
    }
    
    /**
     * 获取所有类定义
     */
    public List<CodeSymbol> getClasses() {
        return symbols.stream()
            .filter(s -> s.getKind() == CodeSymbol.SymbolKind.CLASS)
            .toList();
    }
    
    /**
     * 获取所有方法/函数定义
     */
    public List<CodeSymbol> getMethods() {
        return symbols.stream()
            .filter(s -> s.getKind() == CodeSymbol.SymbolKind.METHOD || 
                        s.getKind() == CodeSymbol.SymbolKind.FUNCTION)
            .toList();
    }
    
    /**
     * 获取指定名称的符号
     */
    public CodeSymbol findSymbol(String name) {
        return symbols.stream()
            .filter(s -> s.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 获取指定位置的符号
     */
    public CodeSymbol findSymbolAt(int line, int col) {
        return symbols.stream()
            .filter(s -> s.contains(line, col))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 获取子符号
     */
    public List<CodeSymbol> getChildren(CodeSymbol parent) {
        if (parent.getChildren() == null) {
            return Collections.emptyList();
        }
        return symbols.stream()
            .filter(s -> parent.getChildren().contains(s.getName()))
            .toList();
    }
}
