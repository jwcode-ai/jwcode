package com.jwcode.core.skill;

import java.util.Set;

/**
 * 背景回顾 Agent 的配置。
 */
public class BackgroundReviewConfig {

    /** 默认记忆提醒间隔（对话轮次） */
    public static final int DEFAULT_MEMORY_NUDGE_INTERVAL = 3;

    /** 默认技能提醒间隔（对话轮次） */
    public static final int DEFAULT_SKILL_NUDGE_INTERVAL = 5;

    /** 回顾 Agent 最大迭代次数 */
    public static final int MAX_REVIEW_ITERATIONS = 16;

    /** 回顾 Agent 允许使用的工具 */
    public static final Set<String> REVIEW_TOOLS = Set.of(
        "memory-read", "memory-write",
        "skill-view", "skill-manage",
        "file-read"
    );

    private int memoryNudgeInterval = DEFAULT_MEMORY_NUDGE_INTERVAL;
    private int skillNudgeInterval = DEFAULT_SKILL_NUDGE_INTERVAL;
    private boolean enabled = true;

    public BackgroundReviewConfig() {}

    public BackgroundReviewConfig(int memoryNudgeInterval, int skillNudgeInterval) {
        this.memoryNudgeInterval = memoryNudgeInterval;
        this.skillNudgeInterval = skillNudgeInterval;
    }

    public int getMemoryNudgeInterval() { return memoryNudgeInterval; }
    public void setMemoryNudgeInterval(int v) { this.memoryNudgeInterval = v; }

    public int getSkillNudgeInterval() { return skillNudgeInterval; }
    public void setSkillNudgeInterval(int v) { this.skillNudgeInterval = v; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
