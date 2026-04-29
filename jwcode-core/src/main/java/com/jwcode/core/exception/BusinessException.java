package com.jwcode.core.exception;

import lombok.Getter;

/**
 * 业务异常
 * 
 * 用于表示业务逻辑层面的错误，如业务规则不满足、资源不存在等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /** 错误码 */
    private final String code;
    
    /** 错误详情 */
    private final Object details;
    
    public BusinessException(String message) {
        this("BUSINESS_ERROR", message, null);
    }
    
    public BusinessException(String code, String message) {
        this(code, message, null);
    }
    
    public BusinessException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
    
    public BusinessException(String message, Throwable cause) {
        this("BUSINESS_ERROR", message, null, cause);
    }
    
    public BusinessException(String code, String message, Object details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details;
    }
    
    // ============ 常用业务异常工厂方法 ============
    
    /** 资源不存在 */
    public static BusinessException notFound(String resource, String id) {
        return new BusinessException("NOT_FOUND", 
            String.format("%s not found: %s", resource, id));
    }
    
    /** 资源已存在 */
    public static BusinessException alreadyExists(String resource, String identifier) {
        return new BusinessException("ALREADY_EXISTS", 
            String.format("%s already exists: %s", resource, identifier));
    }
    
    /** 操作不允许 */
    public static BusinessException notAllowed(String operation) {
        return new BusinessException("NOT_ALLOWED", 
            String.format("Operation not allowed: %s", operation));
    }
    
    /** 参数无效 */
    public static BusinessException invalidArgument(String paramName, String reason) {
        return new BusinessException("INVALID_ARGUMENT", 
            String.format("Invalid argument '%s': %s", paramName, reason));
    }
    
    /** 状态非法 */
    public static BusinessException illegalState(String currentState, String expectedState) {
        return new BusinessException("ILLEGAL_STATE", 
            String.format("Illegal state: current=%s, expected=%s", currentState, expectedState));
    }
}