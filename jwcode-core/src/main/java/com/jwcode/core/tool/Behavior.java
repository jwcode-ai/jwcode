package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Behavior - 行为定义
 * 
 * 功能说明：
 * 定义一个可执行的行为，包含行为类型和执行逻辑。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Behavior {
    
    private String id;
    private String name;
    private String description;
    private String type;
    private Map<String, Object> parameters;
    private boolean enabled;
    
    public Behavior() {
        this.enabled = true;
    }
    
    public Behavior(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.enabled = true;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * 执行行为
     * 
     * @param context 执行上下文
     * @return 执行结果
     */
    public Object execute(Map<String, Object> context) {
        // 默认实现，子类可以重写
        return null;
    }
}
