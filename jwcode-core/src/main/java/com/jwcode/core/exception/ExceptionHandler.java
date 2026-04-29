package com.jwcode.core.exception;

import com.jwcode.core.api.ApiResult;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.function.Function;

/**
 * HTTP 层异常处理器
 * 
 * 统一处理所有异常，转换为标准 API 响应。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Slf4j
public final class ExceptionHandler {
    
    private ExceptionHandler() {
        // 工具类，禁止实例化
    }
    
    /**
     * 处理异常并转换为 ApiResult
     */
    public static ApiResult<?> handle(Throwable throwable) {
        String requestId = UUID.randomUUID().toString();
        return handle(throwable, requestId);
    }
    
    /**
     * 处理异常并转换为 ApiResult
     */
    public static ApiResult<?> handle(Throwable throwable, String requestId) {
        // 记录日志
        log.error("[ExceptionHandler] requestId={}, type={}, message={}", 
            requestId, throwable.getClass().getSimpleName(), throwable.getMessage());
        
        if (throwable instanceof BusinessException be) {
            return ApiResult.error(be.getCode(), be.getMessage(), requestId)
                .withDetails(be.getDetails());
        }
        
        if (throwable instanceof ValidationException ve) {
            ApiResult<?> result = ApiResult.validationError(ve.getMessage(), requestId);
            // 转换 ValidationException.FieldError 到 ApiResult.FieldError
            if (ve.getErrors() != null) {
                for (ValidationException.FieldError fe : ve.getErrors()) {
                    result.addFieldError(fe.getField(), fe.getRejectedValue(), fe.getMessage());
                }
            }
            return result;
        }
        
        if (throwable instanceof SystemException se) {
            return ApiResult.systemError(se.getCode(), se.getMessage(), requestId);
        }
        
        if (throwable instanceof IllegalArgumentException iae) {
            return ApiResult.error("INVALID_ARGUMENT", iae.getMessage(), requestId);
        }
        
        if (throwable instanceof IllegalStateException ise) {
            return ApiResult.error("ILLEGAL_STATE", ise.getMessage(), requestId);
        }
        
        // 未知异常
        return ApiResult.systemError("UNKNOWN_ERROR", 
            "An unexpected error occurred. Please contact support with requestId: " + requestId, 
            requestId);
    }
    
    /**
     * 包装 Runnable，捕获异常并处理
     */
    public static Runnable wrap(Runnable runnable, Function<Throwable, ApiResult<?>> errorHandler) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                errorHandler.apply(t);
            }
        };
    }
    
    /**
     * 包装 Runnable，捕获异常并记录
     */
    public static Runnable wrapWithLogging(Runnable runnable) {
        return wrap(runnable, t -> {
            log.error("Exception in wrapped runnable", t);
            return null;
        });
    }
    
    /**
     * 处理异常并返回默认值
     */
    public static <T> T handleWithDefault(Throwable throwable, T defaultValue) {
        handle(throwable);
        return defaultValue;
    }
    
    /**
     * 处理异常并抛出 API 异常
     */
    public static ApiResult<?> handleAndThrow(Throwable throwable) {
        ApiResult<?> result = handle(throwable);
        throw new ApiException(result);
    }
    
    /**
     * API 异常（用于在需要抛出 ApiResult 的地方）
     */
    public static class ApiException extends RuntimeException {
        private final ApiResult<?> result;
        
        public ApiException(ApiResult<?> result) {
            super(result.getMessage());
            this.result = result;
        }
        
        public ApiResult<?> getResult() {
            return result;
        }
    }
}