package com.jwcode.core.config;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 配置验证器
 * 用于验证配置项的有效性、检查必需配置、验证配置范围
 */
public class ConfigValidator {
    
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]+(\\.[a-zA-Z0-9_\\-]+)*$");
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(".*(password|secret|key|token|credential|auth).*", Pattern.CASE_INSENSITIVE);
    
    private final List<ValidationRule> rules;
    private final Set<String> requiredKeys;
    private final Map<String, Set<ConfigScope>> allowedScopes;
    
    public ConfigValidator() {
        this.rules = new ArrayList<>();
        this.requiredKeys = new HashSet<>();
        this.allowedScopes = new HashMap<>();
        
        // 添加默认规则
        addDefaultRules();
    }
    
    /**
     * 验证规则接口
     */
    @FunctionalInterface
    public interface ValidationRule {
        ValidationResult validate(ConfigItem<?> item);
    }
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String key;
        
        private ValidationResult(boolean valid, String message, String key) {
            this.valid = valid;
            this.message = message;
            this.key = key;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult success(String key) {
            return new ValidationResult(true, null, key);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }
        
        public static ValidationResult error(String key, String message) {
            return new ValidationResult(false, message, key);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getKey() {
            return key;
        }
    }
    
    /**
     * 验证报告
     */
    public static class ValidationReport {
        private final List<ValidationResult> results;
        private final int totalChecked;
        
        public ValidationReport(List<ValidationResult> results, int totalChecked) {
            this.results = results;
            this.totalChecked = totalChecked;
        }
        
        public boolean isValid() {
            return results.stream().allMatch(ValidationResult::isValid);
        }
        
        public List<ValidationResult> getErrors() {
            return results.stream()
                .filter(r -> !r.isValid())
                .toList();
        }
        
        public List<ValidationResult> getResults() {
            return new ArrayList<>(results);
        }
        
        public int getTotalChecked() {
            return totalChecked;
        }
        
        public int getErrorCount() {
            return (int) results.stream().filter(r -> !r.isValid()).count();
        }
        
        @Override
        public String toString() {
            if (isValid()) {
                return String.format("Validation passed: %d items checked", totalChecked);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Validation failed: %d errors out of %d items\n", getErrorCount(), totalChecked));
            for (ValidationResult error : getErrors()) {
                sb.append(String.format("  - [%s] %s\n", error.getKey(), error.getMessage()));
            }
            return sb.toString();
        }
    }
    
    // ==================== 规则管理 ====================
    
    /**
     * 添加验证规则
     */
    public void addRule(ValidationRule rule) {
        rules.add(rule);
    }
    
    /**
     * 添加必需配置键
     */
    public void addRequiredKey(String key) {
        requiredKeys.add(key);
    }
    
    /**
     * 添加必需配置键列表
     */
    public void addRequiredKeys(String... keys) {
        requiredKeys.addAll(Arrays.asList(keys));
    }
    
    /**
     * 设置配置键允许的作用域
     */
    public void setAllowedScopes(String key, ConfigScope... scopes) {
        allowedScopes.put(key, new HashSet<>(Arrays.asList(scopes)));
    }
    
    /**
     * 添加默认验证规则
     */
    private void addDefaultRules() {
        // 键名格式规则
        rules.add(item -> {
            if (!KEY_PATTERN.matcher(item.getKey()).matches()) {
                return ValidationResult.error(item.getKey(), 
                    "Invalid key format. Keys must match pattern: " + KEY_PATTERN.pattern());
            }
            return ValidationResult.success(item.getKey());
        });
        
        // 空值检查规则
        rules.add(item -> {
            if (item.getKey() == null || item.getKey().trim().isEmpty()) {
                return ValidationResult.error("", "Key cannot be empty");
            }
            return ValidationResult.success(item.getKey());
        });
        
        // 敏感信息检查规则（警告级别）
        rules.add(item -> {
            String key = item.getKey();
            if (SENSITIVE_KEY_PATTERN.matcher(key).matches()) {
                // 敏感配置应该在 USER 或 SYSTEM 作用域，不应该在 PROJECT
                if (item.getScope() == ConfigScope.PROJECT) {
                    return ValidationResult.error(key, 
                        "Sensitive configuration should not be stored in project scope");
                }
            }
            return ValidationResult.success(key);
        });
    }
    
    // ==================== 验证方法 ====================
    
    /**
     * 验证单个配置项
     */
    public ValidationResult validate(ConfigItem<?> item) {
        // 首先检查作用域限制
        Set<ConfigScope> allowed = allowedScopes.get(item.getKey());
        if (allowed != null && !allowed.contains(item.getScope())) {
            return ValidationResult.error(item.getKey(), 
                String.format("Configuration '%s' cannot be set in scope '%s'. Allowed scopes: %s",
                    item.getKey(), item.getScope(), allowed));
        }
        
        // 执行所有验证规则
        for (ValidationRule rule : rules) {
            ValidationResult result = rule.validate(item);
            if (!result.isValid()) {
                return result;
            }
        }
        
        return ValidationResult.success(item.getKey());
    }
    
    /**
     * 验证配置存储
     */
    public ValidationReport validate(ConfigManager manager) {
        List<ValidationResult> results = new ArrayList<>();
        int totalChecked = 0;
        
        // 验证所有作用域的配置
        for (ConfigScope scope : ConfigScope.values()) {
            Map<String, String> configs = manager.getAll(scope);
            totalChecked += configs.size();
            
            configs.forEach((key, value) -> {
                ConfigItem<String> item = ConfigItem.of(key, value, scope);
                results.add(validate(item));
            });
        }
        
        // 检查必需配置
        for (String requiredKey : requiredKeys) {
            if (manager.get(requiredKey) == null) {
                results.add(ValidationResult.error(requiredKey, 
                    "Required configuration is missing"));
            }
        }
        
        return new ValidationReport(results, totalChecked);
    }
    
    /**
     * 验证作用域配置
     */
    public ValidationReport validateScope(ConfigScopeConfig scopeConfig) {
        List<ValidationResult> results = new ArrayList<>();
        
        for (ConfigItem<?> item : scopeConfig.getAllItems().values()) {
            results.add(validate(item));
        }
        
        return new ValidationReport(results, scopeConfig.size());
    }
    
    /**
     * 检查配置键是否有效
     */
    public boolean isValidKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }
    
    /**
     * 检查配置是否敏感
     */
    public boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }
    
    /**
     * 获取必需的配置键
     */
    public Set<String> getRequiredKeys() {
        return new HashSet<>(requiredKeys);
    }
    
    /**
     * 检查是否为必需配置
     */
    public boolean isRequired(String key) {
        return requiredKeys.contains(key);
    }
    
    // ==================== 预定义验证器 ====================
    
    /**
     * 创建 URL 格式验证规则
     */
    public static ValidationRule urlFormatRule() {
        return item -> {
            String value = item.getValueAsString();
            if (value == null || value.isEmpty()) {
                return ValidationResult.success(item.getKey());
            }
            try {
                new java.net.URL(value);
                return ValidationResult.success(item.getKey());
            } catch (Exception e) {
                return ValidationResult.error(item.getKey(), "Invalid URL format: " + value);
            }
        };
    }
    
    /**
     * 创建数值范围验证规则
     */
    public static ValidationRule numberRangeRule(double min, double max) {
        return item -> {
            try {
                double value = item.getValueAsDouble();
                if (value < min || value > max) {
                    return ValidationResult.error(item.getKey(),
                        String.format("Value must be between %.2f and %.2f, got: %.2f", min, max, value));
                }
                return ValidationResult.success(item.getKey());
            } catch (NumberFormatException e) {
                return ValidationResult.error(item.getKey(), "Not a valid number");
            }
        };
    }
    
    /**
     * 创建字符串长度验证规则
     */
    public static ValidationRule stringLengthRule(int min, int max) {
        return item -> {
            String value = item.getValueAsString();
            if (value == null) {
                return ValidationResult.success(item.getKey());
            }
            int length = value.length();
            if (length < min || length > max) {
                return ValidationResult.error(item.getKey(),
                    String.format("Length must be between %d and %d, got: %d", min, max, length));
            }
            return ValidationResult.success(item.getKey());
        };
    }
    
    /**
     * 创建枚举值验证规则
     */
    public static ValidationRule enumRule(String... allowedValues) {
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedValues));
        return item -> {
            String value = item.getValueAsString();
            if (value == null || allowed.contains(value)) {
                return ValidationResult.success(item.getKey());
            }
            return ValidationResult.error(item.getKey(),
                String.format("Value must be one of %s, got: %s", allowed, value));
        };
    }
}
