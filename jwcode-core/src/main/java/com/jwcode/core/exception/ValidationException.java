package com.jwcode.core.exception;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数校验异常
 * 
 * 用于表示输入参数校验失败的情况。
 * 包含详细的校验错误信息，便于前端展示。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Getter
public class ValidationException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /** 错误码 */
    private final String code = "VALIDATION_ERROR";
    
    /** 校验错误列表 */
    private final List<FieldError> errors;
    
    public ValidationException(String message) {
        super(message);
        this.errors = new ArrayList<>();
        this.errors.add(new FieldError(null, null, message));
    }
    
    public ValidationException(String message, List<FieldError> errors) {
        super(message);
        this.errors = errors != null ? errors : new ArrayList<>();
    }
    
    public ValidationException(FieldError error) {
        super(error.getMessage());
        this.errors = new ArrayList<>();
        if (error != null) {
            this.errors.add(error);
        }
    }
    
    public ValidationException(List<FieldError> errors) {
        super("Validation failed with " + (errors != null ? errors.size() : 0) + " error(s)");
        this.errors = errors != null ? errors : new ArrayList<>();
    }
    
    /** 添加错误 */
    public ValidationException withError(String field, String message) {
        this.errors.add(new FieldError(field, null, message));
        return this;
    }
    
    /** 添加错误 */
    public ValidationException withError(String field, Object rejectedValue, String message) {
        this.errors.add(new FieldError(field, rejectedValue, message));
        return this;
    }
    
    // ============ 内部类：字段错误 ============
    
    @Getter
    public static class FieldError {
        /** 字段名 */
        private final String field;
        
        /** 被拒绝的值 */
        private final Object rejectedValue;
        
        /** 错误消息 */
        private final String message;
        
        public FieldError(String field, Object rejectedValue, String message) {
            this.field = field;
            this.rejectedValue = rejectedValue;
            this.message = message;
        }
        
        @Override
        public String toString() {
            if (field == null) {
                return message;
            }
            return String.format("%s: %s (rejected: %s)", field, message, rejectedValue);
        }
    }
    
    // ============ 工厂方法 ============
    
    /** 创建必需字段校验失败异常 */
    public static ValidationException required(String field) {
        return new ValidationException(
            new FieldError(field, null, "Field is required")
        );
    }
    
    /** 创建字符串长度校验失败异常 */
    public static ValidationException lengthExceeded(String field, int maxLength) {
        return new ValidationException(
            new FieldError(field, null, 
                String.format("Length must not exceed %d characters", maxLength))
        );
    }
    
    /** 创建格式校验失败异常 */
    public static ValidationException invalidFormat(String field, String format) {
        return new ValidationException(
            new FieldError(field, null, 
                String.format("Invalid format, expected: %s", format))
        );
    }
}