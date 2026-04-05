package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * FileWriteOutput - 文件写入工具输出
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class FileWriteOutput {
    
    private boolean success;
    private String message;
    private String path;
    private int bytesWritten;
    
    public FileWriteOutput() {
    }
    
    public FileWriteOutput(boolean success, String message, String path, int bytesWritten) {
        this.success = success;
        this.message = message;
        this.path = path;
        this.bytesWritten = bytesWritten;
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
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public int getBytesWritten() {
        return bytesWritten;
    }
    
    public void setBytesWritten(int bytesWritten) {
        this.bytesWritten = bytesWritten;
    }
}
