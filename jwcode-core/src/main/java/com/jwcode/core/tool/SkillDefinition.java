package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SkillDefinition - 技能定义
 * 
 * 功能说明：
 * 定义一个技能的基本信息，包括名称、描述、触发条件和执行动作。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SkillDefinition {
    
    private String id;
    private String name;
    private String description;
    private String triggerCondition;
    private List<String> actions;
    private Map<String, Object> metadata;
    private Map<String, Object> parameters;
    private boolean enabled;
    
    public SkillDefinition() {
        this.enabled = true;
    }
    
    public SkillDefinition(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
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
    
    public String getTriggerCondition() {
        return triggerCondition;
    }
    
    public void setTriggerCondition(String triggerCondition) {
        this.triggerCondition = triggerCondition;
    }
    
    public List<String> getActions() {
        return actions;
    }
    
    public void setActions(List<String> actions) {
        this.actions = actions;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * 获取参数定义
     * 
     * @return 参数定义
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    /**
     * 设置参数定义
     * 
     * @param parameters 参数定义
     */
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
     * 获取技能类别（从 metadata 中获取）
     * 
     * @return 技能类别
     */
    public String getCategory() {
        if (metadata != null && metadata.containsKey("category")) {
            return (String) metadata.get("category");
        }
        return null;
    }
    
    /**
     * 获取技能别名（从 metadata 中获取）
     * 
     * @return 技能别名列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getAliases() {
        if (metadata != null && metadata.containsKey("aliases")) {
            Object aliases = metadata.get("aliases");
            if (aliases instanceof List) {
                return (List<String>) aliases;
            }
        }
        return Collections.emptyList();
    }
    
    /**
     * 创建构建器
     * 
     * @return 新的构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String category;
        private String triggerCondition;
        private List<String> actions;
        private Map<String, Object> metadata;
        private Map<String, Object> parameters;
        private boolean enabled = true;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder triggerCondition(String triggerCondition) {
            this.triggerCondition = triggerCondition;
            return this;
        }
        
        public Builder actions(List<String> actions) {
            this.actions = actions;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public SkillDefinition build() {
            SkillDefinition definition = new SkillDefinition();
            definition.setId(this.id);
            definition.setName(this.name);
            definition.setDescription(this.description);
            definition.setTriggerCondition(this.triggerCondition);
            definition.setActions(this.actions);
            definition.setMetadata(this.metadata);
            definition.setParameters(this.parameters);
            definition.setEnabled(this.enabled);
            // 将 category 存储在 metadata 中
            if (this.category != null) {
                if (definition.metadata == null) {
                    definition.metadata = new java.util.HashMap<>();
                }
                definition.metadata.put("category", this.category);
            }
            return definition;
        }
    }
}
