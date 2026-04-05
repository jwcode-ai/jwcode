package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * NotebookEditInput - 笔记本编辑工具输入
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class NotebookEditInput {
    
    private String notebookPath;
    private String cellId;
    private String content;
    private String operation; // add, edit, delete, move
    
    public NotebookEditInput() {
    }
    
    public NotebookEditInput(String notebookPath, String cellId, String content, String operation) {
        this.notebookPath = notebookPath;
        this.cellId = cellId;
        this.content = content;
        this.operation = operation;
    }
    
    public String getNotebookPath() {
        return notebookPath;
    }
    
    public void setNotebookPath(String notebookPath) {
        this.notebookPath = notebookPath;
    }
    
    public String getCellId() {
        return cellId;
    }
    
    public void setCellId(String cellId) {
        this.cellId = cellId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public void setOperation(String operation) {
        this.operation = operation;
    }
}
