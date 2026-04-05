package com.jwcode.core.lsp;

/**
 * LspTextEdit - LSP 文本编辑
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspTextEdit {
    
    private LspRange range;
    private String newText;
    
    public LspTextEdit() {
    }
    
    public LspTextEdit(LspRange range, String newText) {
        this.range = range;
        this.newText = newText;
    }
    
    public LspRange getRange() {
        return range;
    }
    
    public void setRange(LspRange range) {
        this.range = range;
    }
    
    public String getNewText() {
        return newText;
    }
    
    public void setNewText(String newText) {
        this.newText = newText;
    }
}
