package com.jwcode.core.skill;

/**
 * 技能执行器接口
 */
public interface SkillExecutor {
    
    /**
     * 执行技能
     */
    SkillResult execute(Skill skill, SkillContext context);
}
