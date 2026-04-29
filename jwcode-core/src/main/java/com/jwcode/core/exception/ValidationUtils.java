package com.jwcode.core.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 参数校验工具类
 * 
 * 手动实现类似 Spring 的 @Valid 功能。
 * 使用 Jakarta Validation API 进行参数校验。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Slf4j
public final class ValidationUtils {
    
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    
    private ValidationUtils() {
        // 工具类，禁止实例化
    }
    
    /**
     * 获取校验器实例
     */
    public static Validator getValidator() {
        return FACTORY.getValidator();
    }
    
    /**
     * 校验对象，如果校验失败则抛出 ValidationException
     * 
     * @param object 待校验对象
     * @param <T> 对象类型
     * @return 校验通过的对象
     * @throws ValidationException 校验失败时抛出
     */
    public static <T> T validate(T object) {
        if (object == null) {
            throw new ValidationException("Object to validate cannot be null");
        }
        
        Set<ConstraintViolation<T>> violations = getValidator().validate(object);
        
        if (!violations.isEmpty()) {
            throw new ValidationException(
                violations.stream()
                    .map(v -> new ValidationException.FieldError(
                        getPropertyPath(v),
                        v.getInvalidValue(),
                        v.getMessage()))
                    .collect(Collectors.toList())
            );
        }
        
        return object;
    }
    
    /**
     * 校验对象，返回校验结果（不抛出异常）
     * 
     * @param object 待校验对象
     * @param <T> 对象类型
     * @return 校验结果
     */
    public static <T> ValidationResult<T> validateResult(T object) {
        if (object == null) {
            return ValidationResult.failure(
                Collections.singletonList(new ValidationException.FieldError(
                    null, null, "Object to validate cannot be null")));
        }
        
        Set<ConstraintViolation<T>> violations = getValidator().validate(object);
        
        if (violations.isEmpty()) {
            return ValidationResult.success(object);
        }
        
        return ValidationResult.failure(
            violations.stream()
                .map(v -> new ValidationException.FieldError(
                    getPropertyPath(v),
                    v.getInvalidValue(),
                    v.getMessage()))
                .collect(Collectors.toList())
        );
    }
    
    /**
     * 获取属性路径
     */
    private static String getPropertyPath(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        // 如果是嵌套属性，返回完整路径
        return path;
    }
    
    // ============ 常用校验方法 ============
    
    /** 校验非空字符串 */
    public static void requireNonNull(Object obj, String fieldName) {
        if (obj == null) {
            throw ValidationException.required(fieldName);
        }
    }
    
    /** 校验非空字符串 */
    public static void requireNonBlank(String str, String fieldName) {
        requireNonNull(str, fieldName);
        if (str.trim().isEmpty()) {
            throw new ValidationException(
                new ValidationException.FieldError(fieldName, str, "Field cannot be blank"));
        }
    }
    
    /** 校验字符串长度 */
    public static void requireMaxLength(String str, int maxLength, String fieldName) {
        if (str != null && str.length() > maxLength) {
            throw ValidationException.lengthExceeded(fieldName, maxLength);
        }
    }
    
    /** 校验正整数 */
    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(
                new ValidationException.FieldError(fieldName, value, "Value must be positive"));
        }
    }
    
    /** 校验非负整数 */
    public static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new ValidationException(
                new ValidationException.FieldError(fieldName, value, "Value must be non-negative"));
        }
    }
    
    /** 校验范围 */
    public static void requireInRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new ValidationException(
                new ValidationException.FieldError(fieldName, value,
                    String.format("Value must be between %d and %d", min, max)));
        }
    }
    
    // ============ 校验结果 ============
    
    /**
     * 校验结果封装
     */
    public static class ValidationResult<T> {
        private final boolean valid;
        private final T value;
        private final java.util.List<ValidationException.FieldError> errors;
        
        private ValidationResult(boolean valid, T value, java.util.List<ValidationException.FieldError> errors) {
            this.valid = valid;
            this.value = value;
            this.errors = errors;
        }
        
        public static <T> ValidationResult<T> success(T value) {
            return new ValidationResult<>(true, value, null);
        }
        
        public static <T> ValidationResult<T> failure(java.util.List<ValidationException.FieldError> errors) {
            return new ValidationResult<>(false, null, errors);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public T getValue() {
            return value;
        }
        
        public java.util.List<ValidationException.FieldError> getErrors() {
            return errors;
        }
        
        /** 如果校验失败，抛出异常 */
        public T orElseThrow() {
            if (!valid) {
                throw new ValidationException(errors);
            }
            return value;
        }
    }
}