package com.jwcode.core.exception;

import lombok.Getter;

/**
 * 系统异常
 * 
 * 用于表示系统层面的错误，如 IO 错误、网络错误、配置错误等。
 * 通常表示不可恢复或需要管理员介入的问题。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Getter
public class SystemException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /** 错误码 */
    private final String code;
    
    /** 原始异常类型 */
    private final String originalType;
    
    public SystemException(String message) {
        this("SYSTEM_ERROR", message, null);
    }
    
    public SystemException(String code, String message) {
        this(code, message, null);
    }
    
    public SystemException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.originalType = cause != null ? cause.getClass().getSimpleName() : null;
    }
    
    public SystemException(String message, Throwable cause) {
        this("SYSTEM_ERROR", message, cause);
    }
    
    // ============ 系统异常工厂方法 ============
    
    /** IO 错误 */
    public static SystemException ioError(String operation, Throwable cause) {
        return new SystemException("IO_ERROR", 
            String.format("I/O error during %s: %s", operation, cause.getMessage()), cause);
    }
    
    /** 配置错误 */
    public static SystemException configError(String key, String reason) {
        return new SystemException("CONFIG_ERROR", 
            String.format("Configuration error for '%s': %s", key, reason));
    }
    
    /** 网络错误 */
    public static SystemException networkError(String endpoint, Throwable cause) {
        return new SystemException("NETWORK_ERROR", 
            String.format("Network error accessing %s: %s", endpoint, cause.getMessage()), cause);
    }
    
    /** 超时错误 */
    public static SystemException timeoutError(String operation, int timeoutSeconds) {
        return new SystemException("TIMEOUT_ERROR", 
            String.format("Operation '%s' timed out after %d seconds", operation, timeoutSeconds));
    }
    
    /** 资源耗尽 */
    public static SystemException resourceExhausted(String resource) {
        return new SystemException("RESOURCE_EXHAUSTED", 
            String.format("Resource exhausted: %s", resource));
    }
    
    /** 初始化失败 */
    public static SystemException initializationFailed(String component, String reason) {
        return new SystemException("INIT_FAILED", 
            String.format("Failed to initialize %s: %s", component, reason));
    }
    
    /** 未知错误 */
    public static SystemException unknownError(Throwable cause) {
        return new SystemException("UNKNOWN_ERROR", 
            "An unexpected error occurred", cause);
    }
}