package com.jwcode.core.lsp;

import java.util.List;
import java.util.Map;

/**
 * LspWorkspaceEdit - LSP 工作区编辑
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspWorkspaceEdit {
    
    private Map<String, List<LspTextEdit>> changes;
    private List<LspDocumentChange> documentChanges;
    
    public LspWorkspaceEdit() {
    }
    
    public Map<String, List<LspTextEdit>> getChanges() {
        return changes;
    }
    
    public void setChanges(Map<String, List<LspTextEdit>> changes) {
        this.changes = changes;
    }
    
    public List<LspDocumentChange> getDocumentChanges() {
        return documentChanges;
    }
    
    public void setDocumentChanges(List<LspDocumentChange> documentChanges) {
        this.documentChanges = documentChanges;
    }
}
