package com.jwcode.core.lsp;

/**
 * LspHover - LSP 悬停信息
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspHover {
    
    private String contents;
    private LspRange range;
    
    public LspHover() {
    }
    
    public LspHover(String contents, LspRange range) {
        this.contents = contents;
        this.range = range;
    }
    
    public String getContents() {
        return contents;
    }
    
    public void setContents(String contents) {
        this.contents = contents;
    }
    
    public LspRange getRange() {
        return range;
    }
    
    public void setRange(LspRange range) {
        this.range = range;
    }
}
