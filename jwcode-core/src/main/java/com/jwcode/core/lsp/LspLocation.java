package com.jwcode.core.lsp;

/**
 * LspLocation - LSP 位置信息
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspLocation {
    
    private String uri;
    private LspRange range;
    
    public LspLocation() {
    }
    
    public LspLocation(String uri, LspRange range) {
        this.uri = uri;
        this.range = range;
    }
    
    public String getUri() {
        return uri;
    }
    
    public void setUri(String uri) {
        this.uri = uri;
    }
    
    public LspRange getRange() {
        return range;
    }
    
    public void setRange(LspRange range) {
        this.range = range;
    }
}
