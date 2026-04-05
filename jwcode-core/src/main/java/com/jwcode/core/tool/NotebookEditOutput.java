package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * NotebookEditOutput - 笔记本编辑工具输出
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class NotebookEditOutput {
    
    private boolean success;
    private String message;
    private String notebookPath;
    private String cellId;
    
    public NotebookEditOutput() {
    }
    
    public NotebookEditOutput(boolean success, String message, String notebookPath, String cellId) {
        this.success = success;
        this.message = message;
        this.notebookPath = notebookPath;
        this.cellId = cellId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
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
}
