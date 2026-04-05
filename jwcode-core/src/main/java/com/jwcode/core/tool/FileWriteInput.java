package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * FileWriteInput - 文件写入工具输入
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class FileWriteInput {
    
    private String path;
    private String content;
    private boolean append;
    
    public FileWriteInput() {
    }
    
    public FileWriteInput(String path, String content) {
        this.path = path;
        this.content = content;
        this.append = false;
    }
    
    public FileWriteInput(String path, String content, boolean append) {
        this.path = path;
        this.content = content;
        this.append = append;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public boolean isAppend() {
        return append;
    }
    
    public void setAppend(boolean append) {
        this.append = append;
    }
}
