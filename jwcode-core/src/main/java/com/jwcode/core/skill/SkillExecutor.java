package com.jwcode.core.skill;

import java.util.concurrent.CompletableFuture;

/**
 * 技能执行器接口
 */
public interface SkillExecutor {
    
    /**
     * 异步执行技能
     * 
     * @param skill 技能
     * @param context 执行上下文
     * @return CompletableFuture 包含执行结果
     */
    CompletableFuture<SkillResult> execute(Skill skill, SkillContext context);
}
