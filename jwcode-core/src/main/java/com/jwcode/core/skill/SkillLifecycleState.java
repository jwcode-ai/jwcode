package com.jwcode.core.skill;

/**
 * 技能生命周期状态 — 控制技能从活跃到归档的自动流转。
 */
public enum SkillLifecycleState {
    /** 活跃使用中 */
    ACTIVE,
    /** 30 天未使用 — 候选归档 */
    STALE,
    /** 90 天未使用 — 已归档到 .archive/ */
    ARCHIVED,
    /** 固定状态 — 跳过所有自动流转 */
    PINNED
}
