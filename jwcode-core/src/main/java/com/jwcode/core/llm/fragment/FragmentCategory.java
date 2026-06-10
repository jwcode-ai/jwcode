package com.jwcode.core.llm.fragment;

/**
 * 片段类别 — 决定注入顺序和分组。
 *
 * <p>注入顺序按 ordinal 升序排列。
 */
public enum FragmentCategory {
    /** 系统身份 — 最早注入，定义 AI 是谁 */
    SYSTEM_IDENTITY,
    /** 行为规则 — 文件编辑指南、FINISH 协议等 */
    BEHAVIORAL_RULES,
    /** 环境信息 — OS、工作目录、时间等 */
    ENVIRONMENT,
    /** 能力声明 — 可用工具列表、权限上下文 */
    CAPABILITIES,
    /** 技能上下文 — 从 SKILL.md 加载的技能提示 */
    SKILLS,
    /** 策略上下文 — ExecPolicyEngine 规则摘要 */
    POLICY,
    /** 任务上下文 — 当前任务、计划、Swarm 分解结果 */
    TASK_CONTEXT
}
