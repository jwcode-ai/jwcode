package com.jwcode.core.skill;

import java.util.List;

/**
 * 技能提供者接口 — 定义技能发现和加载的抽象。
 *
 * <p>实现链：SystemSkillProvider → UserSkillProvider → PluginSkillProvider
 */
public interface SkillProvider {

    /** 提供者名称 */
    String getName();

    /** 发现并返回所有技能定义 */
    List<SkillDefinition> discover();

    /** 重新加载技能 */
    default List<SkillDefinition> reload() {
        return discover();
    }

    /** 优先级（数字越小越先加载，用于合并时去重） */
    default int priority() {
        return 100;
    }
}
