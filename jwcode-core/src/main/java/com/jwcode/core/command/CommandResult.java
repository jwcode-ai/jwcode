package com.jwcode.core.command;

/**
 * 命令执行结果
 */
public class CommandResult {
    
    private final boolean success;
    private final String message;
    private final Object data;
    
    private CommandResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    public static CommandResult success(String message) {
        return new CommandResult(true, message, null);
    }
    
    public static CommandResult success(String message, Object data) {
        return new CommandResult(true, message, data);
    }
    
    public static CommandResult error(String message) {
        return new CommandResult(false, message, null);
    }
    
    public static CommandResult error(String message, Object data) {
        return new CommandResult(false, message, data);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getData() {
        return data;
    }
    
    @Override
    public String toString() {
        return success ? "✓ " + message : "✗ " + message;
    }
}
