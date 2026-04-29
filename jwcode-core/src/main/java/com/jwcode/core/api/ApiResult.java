package com.jwcode.core.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用 API 响应体
 * 
 * 统一所有 API 响应的格式，包括成功和失败情况。
 * 
 * <pre>
 * 成功响应:
 * {
 *     "success": true,
 *     "data": {...},
 *     "timestamp": 1234567890,
 *     "requestId": "uuid"
 * }
 * 
 * 失败响应:
 * {
 *     "success": false,
 *     "error": {
 *         "code": "ERROR_CODE",
 *         "message": "错误描述",
 *         "details": {...}
 *     },
 *     "fieldErrors": [...],
 *     "timestamp": 1234567890,
 *     "requestId": "uuid"
 * }
 * </pre>
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {
    
    /** 是否成功 */
    private boolean success;
    
    /** 成功时返回的数据 */
    private T data;
    
    /** 错误信息（失败时） */
    private ErrorInfo error;
    
    /** 字段错误列表（校验失败时） */
    @JsonProperty("fieldErrors")
    private List<FieldError> fieldErrors;
    
    /** 时间戳 */
    private long timestamp;
    
    /** 请求 ID，用于追踪 */
    private String requestId;
    
    // ============ 成功响应 ============
    
    /** 创建成功响应 */
    public static <T> ApiResult<T> success(T data) {
        return ApiResult.<T>builder()
            .success(true)
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /** 创建成功响应（无数据） */
    public static ApiResult<Void> success() {
        return ApiResult.<Void>builder()
            .success(true)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    // ============ 失败响应 ============
    
    /** 创建错误响应 */
    public static <T> ApiResult<T> error(String code, String message) {
        return error(code, message, null);
    }
    
    /** 创建错误响应 */
    public static <T> ApiResult<T> error(String code, String message, String requestId) {
        return ApiResult.<T>builder()
            .success(false)
            .error(ErrorInfo.builder()
                .code(code)
                .message(message)
                .build())
            .timestamp(System.currentTimeMillis())
            .requestId(requestId)
            .build();
    }
    
    /** 创建业务异常响应 */
    public static <T> ApiResult<T> businessError(String message) {
        return error("BUSINESS_ERROR", message);
    }
    
    /** 创建校验异常响应 */
    public static <T> ApiResult<T> validationError(String message, String requestId) {
        return ApiResult.<T>builder()
            .success(false)
            .error(ErrorInfo.builder()
                .code("VALIDATION_ERROR")
                .message(message)
                .build())
            .timestamp(System.currentTimeMillis())
            .requestId(requestId)
            .build();
    }
    
    /** 创建系统异常响应 */
    public static <T> ApiResult<T> systemError(String code, String message, String requestId) {
        return ApiResult.<T>builder()
            .success(false)
            .error(ErrorInfo.builder()
                .code(code)
                .message(message)
                .build())
            .timestamp(System.currentTimeMillis())
            .requestId(requestId)
            .build();
    }
    
    // ============ 链式方法 ============
    
    /** 设置请求 ID */
    public ApiResult<T> withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
    
    /** 设置错误详情 */
    public ApiResult<T> withDetails(Object details) {
        if (this.error != null) {
            this.error.setDetails(details);
        }
        return this;
    }
    
    /** 设置字段错误列表 */
    public ApiResult<T> withFieldErrors(List<FieldError> errors) {
        this.fieldErrors = errors;
        return this;
    }
    
    /** 添加字段错误 */
    public ApiResult<T> addFieldError(String field, Object rejectedValue, String message) {
        if (this.fieldErrors == null) {
            this.fieldErrors = new ArrayList<>();
        }
        this.fieldErrors.add(new FieldError(field, rejectedValue, message));
        return this;
    }
    
    /** 获取错误消息 */
    public String getMessage() {
        if (error != null) {
            return error.getMessage();
        }
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            return fieldErrors.get(0).getMessage();
        }
        return null;
    }
    
    /** 获取错误码 */
    public String getErrorCode() {
        return error != null ? error.getCode() : null;
    }
    
    // ============ 内部类 ============
    
    /** 错误信息 */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo {
        private String code;
        private String message;
        private Object details;
    }
    
    /** 字段错误 */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {
        private String field;
        private Object rejectedValue;
        private String message;
        
        public FieldError(String field, Object rejectedValue, String message) {
            this.field = field;
            this.rejectedValue = rejectedValue;
            this.message = message;
        }
    }
    
    // ============ 兼容方法 ============
    
    /** 获取数据（类型转换） */
    @SuppressWarnings("unchecked")
    public <R> R getDataAs(Class<R> clazz) {
        if (data == null) {
            return null;
        }
        if (clazz.isInstance(data)) {
            return (R) data;
        }
        throw new ClassCastException("Cannot cast " + data.getClass() + " to " + clazz);
    }
}