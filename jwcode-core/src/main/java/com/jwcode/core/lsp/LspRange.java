package com.jwcode.core.lsp;

/**
 * LspRange - LSP 范围信息
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspRange {
    
    private LspPosition start;
    private LspPosition end;
    
    public LspRange() {
    }
    
    public LspRange(LspPosition start, LspPosition end) {
        this.start = start;
        this.end = end;
    }
    
    public LspRange(int startLine, int startChar, int endLine, int endChar) {
        this.start = new LspPosition(startLine, startChar);
        this.end = new LspPosition(endLine, endChar);
    }
    
    public LspPosition getStart() {
        return start;
    }
    
    public void setStart(LspPosition start) {
        this.start = start;
    }
    
    public LspPosition getEnd() {
        return end;
    }
    
    public void setEnd(LspPosition end) {
        this.end = end;
    }
}
