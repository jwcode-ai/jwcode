package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * ToolProgress - 工具进度封装
 * 
 * 功能说明：
 * 封装工具执行过程中的进度信息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ToolProgress<T> {
    
    private final T data;
    private final String message;
    private final int percentComplete;
    
    public ToolProgress(T data) {
        this(data, null, -1);
    }
    
    public ToolProgress(T data, String message) {
        this(data, message, -1);
    }
    
    public ToolProgress(T data, String message, int percentComplete) {
        this.data = data;
        this.message = message;
        this.percentComplete = percentComplete;
    }
    
    public T getData() {
        return data;
    }
    
    public String getMessage() {
        return message;
    }
    
    public int getPercentComplete() {
        return percentComplete;
    }
    
    public static <T> ToolProgress<T> of(T data) {
        return new ToolProgress<>(data);
    }
    
    public static <T> ToolProgress<T> withMessage(T data, String message) {
        return new ToolProgress<>(data, message);
    }
    
    public static <T> ToolProgress<T> withPercent(T data, int percent) {
        return new ToolProgress<>(data, null, percent);
    }
}