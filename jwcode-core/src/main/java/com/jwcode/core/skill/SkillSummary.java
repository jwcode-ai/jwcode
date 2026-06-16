package com.jwcode.core.skill;

import java.util.List;

/**
 * 技能摘要 — 轻量级视图，不含 systemPrompt。
 *
 * <p>用于构建系统提示中的技能目录索引，以及技能选择器的初始匹配阶段。
 * 完整技能详情通过 {@link SkillRegistry#get(String)} 延迟加载。
 */
public record SkillSummary(
    String id,
    String name,
    String description,
    Skill.Category category,
    List<String> tags,
    Skill.Provenance provenance,
    SkillDefinition.InjectionStrategy injectionStrategy
) {
    public static SkillSummary from(Skill skill) {
        return new SkillSummary(
            skill.getId(),
            skill.getName(),
            skill.getDescription(),
            skill.getCategory(),
            skill.getTags(),
            skill.getProvenance(),
            skill.getInjectionStrategy()
        );
    }
}
