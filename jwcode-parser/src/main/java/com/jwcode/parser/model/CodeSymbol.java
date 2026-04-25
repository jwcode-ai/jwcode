package com.jwcode.parser.model;

import lombok.Data;
import java.util.List;

/**
 * 代码符号信息
 */
@Data
public class CodeSymbol {
    private String name;
    private SymbolKind kind;
    private int startLine;
    private int startCol;
    private int endLine;
    private int endCol;
    private String signature;
    private String docstring;
    private String parent;
    private List<String> children;
    private List<String> modifiers;
    
    public enum SymbolKind {
        CLASS, INTERFACE, METHOD, FUNCTION, 
        VARIABLE, FIELD, ENUM, ANNOTATION, 
        IMPORT, PACKAGE
    }
    
    /**
     * 检查位置是否在符号范围内
     */
    public boolean contains(int line, int col) {
        if (line < startLine || line > endLine) {
            return false;
        }
        if (line == startLine && col < startCol) {
            return false;
        }
        if (line == endLine && col > endCol) {
            return false;
        }
        return true;
    }
    
    /**
     * 获取完整签名
     */
    public String getFullSignature() {
        StringBuilder sb = new StringBuilder();
        if (modifiers != null && !modifiers.isEmpty()) {
            sb.append(String.join(" ", modifiers)).append(" ");
        }
        sb.append(kind).append(" ").append(name);
        if (signature != null) {
            sb.append(" ").append(signature);
        }
        return sb.toString();
    }
}
