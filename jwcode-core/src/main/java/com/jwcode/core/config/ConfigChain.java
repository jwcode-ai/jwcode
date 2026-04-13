package com.jwcode.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配置继承链
 * 表示一个配置键在所有作用域中的查找路径
 */
public class ConfigChain {
    
    private final String key;
    private final List<ChainEntry> entries;
    
    /**
     * 链条目
     */
    public static class ChainEntry {
        private final ConfigScope scope;
        private final String value;
        private final String source;
        private final boolean present;
        
        public ChainEntry(ConfigScope scope, String value, String source, boolean present) {
            this.scope = scope;
            this.value = value;
            this.source = source;
            this.present = present;
        }
        
        public ConfigScope getScope() {
            return scope;
        }
        
        public String getValue() {
            return value;
        }
        
        public String getSource() {
            return source;
        }
        
        public boolean isPresent() {
            return present;
        }
        
        @Override
        public String toString() {
            if (present) {
                return String.format("%s [%s] = %s", scope, source, value != null ? value : "null");
            } else {
                return String.format("%s [%s] = (not set)", scope, source);
            }
        }
    }
    
    public ConfigChain(String key) {
        this.key = key;
        this.entries = new ArrayList<>();
    }
    
    /**
     * 添加链条目
     */
    public void addEntry(ConfigScope scope, String value, String source, boolean present) {
        entries.add(new ChainEntry(scope, value, source, present));
    }
    
    /**
     * 获取配置键
     */
    public String getKey() {
        return key;
    }
    
    /**
     * 获取所有条目
     */
    public List<ChainEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
    
    /**
     * 获取实际生效的条目（第一个存在的）
     */
    public ChainEntry getEffectiveEntry() {
        return entries.stream()
            .filter(ChainEntry::isPresent)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 获取实际生效的值
     */
    public String getEffectiveValue() {
        ChainEntry entry = getEffectiveEntry();
        return entry != null ? entry.getValue() : null;
    }
    
    /**
     * 获取实际生效的作用域
     */
    public ConfigScope getEffectiveScope() {
        ChainEntry entry = getEffectiveEntry();
        return entry != null ? entry.getScope() : null;
    }
    
    /**
     * 检查配置是否存在
     */
    public boolean exists() {
        return getEffectiveEntry() != null;
    }
    
    /**
     * 获取条目数量
     */
    public int size() {
        return entries.size();
    }
    
    /**
     * 格式化显示继承链
     */
    public String formatChain() {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration Chain for '").append(key).append("':\n");
        
        ChainEntry effective = getEffectiveEntry();
        
        for (int i = 0; i < entries.size(); i++) {
            ChainEntry entry = entries.get(i);
            boolean isEffective = entry == effective;
            
            if (i == 0) {
                sb.append(isEffective ? "● " : "○ ");
            } else {
                sb.append(isEffective ? "├─● " : "├─○ ");
            }
            
            sb.append(entry.getScope())
              .append(" [").append(entry.getSource()).append("]");
            
            if (entry.isPresent()) {
                sb.append(" = ").append(entry.getValue());
            } else {
                sb.append(" (not set)");
            }
            
            if (isEffective) {
                sb.append(" ← ACTIVE");
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 获取 JSON 格式的链
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"key\": \"").append(key).append("\",\n");
        sb.append("  \"effectiveValue\": ").append(jsonValue(getEffectiveValue())).append(",\n");
        sb.append("  \"effectiveScope\": \"").append(getEffectiveScope()).append("\",\n");
        sb.append("  \"chain\": [\n");
        
        for (int i = 0; i < entries.size(); i++) {
            ChainEntry entry = entries.get(i);
            sb.append("    {\n");
            sb.append("      \"scope\": \"").append(entry.getScope()).append("\",\n");
            sb.append("      \"source\": \"").append(entry.getSource()).append("\",\n");
            sb.append("      \"present\": ").append(entry.isPresent()).append(",\n");
            sb.append("      \"value\": ").append(jsonValue(entry.getValue())).append("\n");
            sb.append("    }");
            if (i < entries.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }
    
    private String jsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
    
    @Override
    public String toString() {
        return formatChain();
    }
}
