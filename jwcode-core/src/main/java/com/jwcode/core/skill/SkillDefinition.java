package com.jwcode.core.skill;

import java.util.List;

/**
 * 技能定义 — 对标 Codex 的 SKILL.md 格式。
 *
 * <p>从 .skill.md 文件解析得到，包含 YAML front matter 元数据和 Markdown body。
 */
public record SkillDefinition(
    String id,
    String name,
    String description,
    String triggerPrompt,
    String systemPrompt,
    List<String> requiredTools,
    List<String> tags,
    InjectionStrategy injectionStrategy,
    String source
) {
    public enum InjectionStrategy {
        /** 按需注入 — 仅当 AI 请求该技能时 */
        LAZY,
        /** 始终注入 — 每次对话都包含 */
        EAGER,
        /** 混合 — 高频技能预加载，低频按需 */
        HYBRID
    }

    /** 转换为 Skill 实体 */
    public Skill toSkill() {
        return Skill.builder()
            .id(id)
            .name(name)
            .description(description)
            .category(inferCategory())
            .tags(tags != null ? tags : List.of())
            .systemPrompt(systemPrompt)
            .requiredTools(requiredTools != null ? requiredTools : List.of())
            .source(source)
            .build();
    }

    private Skill.Category inferCategory() {
        if (tags == null) return Skill.Category.CUSTOM;
        for (String tag : tags) {
            String t = tag.toLowerCase();
            if (t.contains("code") || t.contains("refactor")) return Skill.Category.CODE;
            if (t.contains("review") || t.contains("analysis") || t.contains("security"))
                return Skill.Category.ANALYSIS;
            if (t.contains("doc") || t.contains("readme")) return Skill.Category.DOCUMENT;
            if (t.contains("test")) return Skill.Category.TEST;
            if (t.contains("devops") || t.contains("deploy")) return Skill.Category.DEVOPS;
        }
        return Skill.Category.CUSTOM;
    }
}
