package com.jwcode.core.config;

/**
 * 配置导出格式枚举
 */
public enum ConfigExportFormat {
    JSON("json", "application/json"),
    YAML("yaml", "application/x-yaml"),
    PROPERTIES("properties", "text/plain"),
    ENV("env", "text/plain");
    
    private final String extension;
    private final String mimeType;
    
    ConfigExportFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }
    
    public String getExtension() {
        return extension;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    /**
     * 从文件名或扩展名解析格式
     */
    public static ConfigExportFormat fromString(String str) {
        if (str == null) {
            return null;
        }
        String lower = str.toLowerCase();
        for (ConfigFormat format : ConfigFormat.values()) {
            if (lower.endsWith("." + format.getExtension()) || lower.equals(format.getExtension())) {
                return ConfigExportFormat.valueOf(format.name());
            }
        }
        return null;
    }
    
    /**
     * 内部映射到 ConfigFormat
     */
    private enum ConfigFormat {
        JSON("json"),
        YAML("yaml"),
        PROPERTIES("properties"),
        ENV("env");
        
        private final String extension;
        
        ConfigFormat(String extension) {
            this.extension = extension;
        }
        
        public String getExtension() {
            return extension;
        }
    }
}
