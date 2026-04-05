package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工具参数验证结果
 */
public class ToolValidationResult {
    
    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;
    
    private ToolValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.errors = errors != null ? errors : Collections.emptyList();
        this.warnings = warnings != null ? warnings : Collections.emptyList();
    }
    
    /**
     * 创建有效结果
     */
    public static ToolValidationResult valid() {
        return new ToolValidationResult(true, null, null);
    }
    
    /**
     * 创建有效结果（带警告）
     */
    public static ToolValidationResult validWithWarnings(List<String> warnings) {
        return new ToolValidationResult(true, null, warnings);
    }
    
    /**
     * 创建无效结果
     */
    public static ToolValidationResult invalid(String error) {
        return new ToolValidationResult(false, List.of(error), null);
    }
    
    /**
     * 创建无效结果（多错误）
     */
    public static ToolValidationResult invalid(List<String> errors) {
        return new ToolValidationResult(false, errors, null);
    }
    
    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 检查是否有效
     */
    public boolean isValid() {
        return valid;
    }
    
    /**
     * 获取错误列表
     */
    public List<String> getErrors() {
        return errors;
    }
    
    /**
     * 获取警告列表
     */
    public List<String> getWarnings() {
        return warnings;
    }
    
    /**
     * 获取第一个错误
     */
    public String getFirstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }
    
    /**
     * 是否有错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * 是否有警告
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * 获取格式化的错误信息
     */
    public String getFormattedErrors() {
        if (errors.isEmpty()) return "";
        if (errors.size() == 1) return errors.get(0);
        return String.join("; ", errors);
    }
    
    @Override
    public String toString() {
        if (valid) {
            return hasWarnings() ? "Valid with warnings: " + warnings : "Valid";
        }
        return "Invalid: " + getFormattedErrors();
    }
    
    /**
     * 构建器
     */
    public static class Builder {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public Builder addError(String error) {
            errors.add(error);
            return this;
        }
        
        public Builder addWarning(String warning) {
            warnings.add(warning);
            return this;
        }
        
        public ToolValidationResult build() {
            return new ToolValidationResult(errors.isEmpty(), errors, warnings);
        }
    }
}
