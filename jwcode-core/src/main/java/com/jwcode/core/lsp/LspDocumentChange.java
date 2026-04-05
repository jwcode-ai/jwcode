package com.jwcode.core.lsp;

/**
 * LspDocumentChange - LSP 文档变更
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspDocumentChange {
    
    private String uri;
    private String kind; // create, rename, delete
    private String newUri;
    
    public LspDocumentChange() {
    }
    
    public String getUri() {
        return uri;
    }
    
    public void setUri(String uri) {
        this.uri = uri;
    }
    
    public String getKind() {
        return kind;
    }
    
    public void setKind(String kind) {
        this.kind = kind;
    }
    
    public String getNewUri() {
        return newUri;
    }
    
    public void setNewUri(String newUri) {
        this.newUri = newUri;
    }
}
