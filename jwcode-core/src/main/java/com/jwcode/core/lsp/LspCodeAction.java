package com.jwcode.core.lsp;

/**
 * LspCodeAction - LSP 代码操作
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspCodeAction {
    
    private String title;
    private String kind;
    private LspWorkspaceEdit edit;
    private String command;
    
    public LspCodeAction() {
    }
    
    public LspCodeAction(String title, String kind) {
        this.title = title;
        this.kind = kind;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getKind() {
        return kind;
    }
    
    public void setKind(String kind) {
        this.kind = kind;
    }
    
    public LspWorkspaceEdit getEdit() {
        return edit;
    }
    
    public void setEdit(LspWorkspaceEdit edit) {
        this.edit = edit;
    }
    
    public String getCommand() {
        return command;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
}
