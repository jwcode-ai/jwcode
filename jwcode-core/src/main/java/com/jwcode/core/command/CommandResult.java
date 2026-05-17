package com.jwcode.core.command;

/**
 * 命令执行结果
 */
public class CommandResult {

    /** 结果类型 */
    public enum ResultType {
        SUCCESS,
        ERROR,
        EXIT
    }

    private final ResultType type;
    private final String message;
    private final Object data;

    private CommandResult(ResultType type, String message, Object data) {
        this.type = type;
        this.message = message;
        this.data = data;
    }

    public static CommandResult success(String message) {
        return new CommandResult(ResultType.SUCCESS, message, null);
    }

    public static CommandResult success(String message, Object data) {
        return new CommandResult(ResultType.SUCCESS, message, data);
    }

    public static CommandResult error(String message) {
        return new CommandResult(ResultType.ERROR, message, null);
    }

    public static CommandResult error(String message, Object data) {
        return new CommandResult(ResultType.ERROR, message, data);
    }

    /**
     * 创建退出命令结果。由上层调用者（如 REPL 循环）根据此结果执行实际退出操作。
     */
    public static CommandResult exit(String message) {
        return new CommandResult(ResultType.EXIT, message, null);
    }

    public boolean isSuccess() {
        return type == ResultType.SUCCESS;
    }

    public boolean isExit() {
        return type == ResultType.EXIT;
    }

    public ResultType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        switch (type) {
            case SUCCESS: return "✓ " + message;
            case ERROR:   return "✗ " + message;
            case EXIT:    return "→ " + message;
            default:      return message;
        }
    }
}
