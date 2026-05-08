package com.jwcode.core.a2a.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AgentCard — Agent 自我宣告模型（A2A Agent Card）。
 *
 * <p>每个 Agent 通过 AgentCard 宣告自己的能力、输入输出格式、通信能力等。
 * Orchestrator 通过查询 Agent Card 来动态发现和选择合适的子Agent。</p>
 *
 * <p>参考 A2A (Agent-to-Agent Protocol) 规范。</p>
 */
public class AgentCard {

    /** Agent 唯一名称 */
    private final String name;

    /** Agent 可读描述 */
    private final String description;

    /** Agent 类型/角色 */
    private final String agentType;

    /** Agent 的技能列表 */
    private final List<Skill> skills;

    /** Agent 的通信能力 */
    private final Capabilities capabilities;

    /** Agent 的 A2A 服务端点 URL（远程调用时使用） */
    private final String endpointUrl;

    /** Agent 的版本 */
    private final String version;

    public AgentCard(String name, String description, String agentType,
                     List<Skill> skills, Capabilities capabilities,
                     String endpointUrl, String version) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.agentType = Objects.requireNonNull(agentType, "agentType must not be null");
        this.skills = skills != null ? Collections.unmodifiableList(skills) : Collections.emptyList();
        this.capabilities = capabilities != null ? capabilities : Capabilities.defaultCapabilities();
        this.endpointUrl = endpointUrl;
        this.version = version != null ? version : "1.0.0";
    }

    // ==================== Getters ====================

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAgentType() { return agentType; }
    public List<Skill> getSkills() { return skills; }
    public Capabilities getCapabilities() { return capabilities; }
    public String getEndpointUrl() { return endpointUrl; }
    public String getVersion() { return version; }

    /**
     * 判断该 Agent 是否拥有指定 skill
     */
    public boolean hasSkill(String skillId) {
        return skills.stream().anyMatch(s -> s.getId().equals(skillId));
    }

    /**
     * 根据 skill ID 查找 Skill 定义
     */
    public Skill getSkill(String skillId) {
        return skills.stream()
            .filter(s -> s.getId().equals(skillId))
            .findFirst()
            .orElse(null);
    }

    /**
     * 判断是否支持远程 A2A 调用
     */
    public boolean isRemote() {
        return endpointUrl != null && !endpointUrl.isEmpty();
    }

    @Override
    public String toString() {
        return "AgentCard{name='" + name + "', type=" + agentType +
               ", skills=" + skills.size() + ", remote=" + isRemote() + "}";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private String agentType;
        private List<Skill> skills;
        private Capabilities capabilities;
        private String endpointUrl;
        private String version;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder agentType(String agentType) { this.agentType = agentType; return this; }
        public Builder skills(List<Skill> skills) { this.skills = skills; return this; }
        public Builder capabilities(Capabilities capabilities) { this.capabilities = capabilities; return this; }
        public Builder endpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; return this; }
        public Builder version(String version) { this.version = version; return this; }

        public AgentCard build() {
            return new AgentCard(name, description, agentType, skills, capabilities, endpointUrl, version);
        }
    }
}
