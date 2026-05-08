package com.jwcode.core.a2a.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Skill — Agent 技能声明。
 *
 * <p>描述 Agent 能够执行的某项具体能力，包括输入输出格式。
 * Orchestrator 根据任务类型匹配 Agent 的 Skill 来进行动态调度。</p>
 */
public class Skill {

    /** 技能唯一标识 */
    private final String id;

    /** 技能可读名称 */
    private final String name;

    /** 技能描述 */
    private final String description;

    /** 输入参数 schema（JSON Schema 格式的字段定义） */
    private final Map<String, Object> inputSchema;

    /** 输出参数 schema（JSON Schema 格式的字段定义） */
    private final Map<String, Object> outputSchema;

    public Skill(String id, String name, String description,
                 Map<String, Object> inputSchema,
                 Map<String, Object> outputSchema) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = name != null ? name : id;
        this.description = description;
        this.inputSchema = inputSchema != null ? Collections.unmodifiableMap(inputSchema) : Collections.emptyMap();
        this.outputSchema = outputSchema != null ? Collections.unmodifiableMap(outputSchema) : Collections.emptyMap();
    }

    // ==================== Getters ====================

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getInputSchema() { return inputSchema; }
    public Map<String, Object> getOutputSchema() { return outputSchema; }

    @Override
    public String toString() {
        return "Skill{id='" + id + "', name='" + name + "'}";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
        private Map<String, Object> outputSchema;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder inputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; return this; }
        public Builder outputSchema(Map<String, Object> outputSchema) { this.outputSchema = outputSchema; return this; }

        public Skill build() {
            return new Skill(id, name, description, inputSchema, outputSchema);
        }
    }
}
