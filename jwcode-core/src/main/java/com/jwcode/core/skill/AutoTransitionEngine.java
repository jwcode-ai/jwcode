package com.jwcode.core.skill;

import java.util.logging.Logger;

/**
 * 自动流转引擎 — 根据使用情况自动推进技能生命周期状态。
 *
 * <p>规则：
 * <ul>
 *   <li>ACTIVE → STALE: 30 天未使用</li>
 *   <li>STALE → ARCHIVED: 90 天未使用（总计 120 天）</li>
 *   <li>PINNED: 跳过所有自动流转</li>
 *   <li>ARCHIVED → ACTIVE: 如果重新使用</li>
 * </ul>
 */
public class AutoTransitionEngine {

    private static final Logger logger = Logger.getLogger(AutoTransitionEngine.class.getName());

    /** 30 天毫秒数 */
    public static final long STALE_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000;

    /** 90 天毫秒数（从 STALE 开始算） */
    public static final long ARCHIVE_THRESHOLD_MS = 90L * 24 * 60 * 60 * 1000;

    private final CuratorStateStore stateStore;
    private final SkillArchiver archiver;
    private final SkillRegistry skillRegistry;

    public AutoTransitionEngine(CuratorStateStore stateStore,
                                 SkillArchiver archiver,
                                 SkillRegistry skillRegistry) {
        this.stateStore = stateStore;
        this.archiver = archiver;
        this.skillRegistry = skillRegistry;
    }

    /**
     * 执行自动流转。
     *
     * @return 执行的流转操作数
     */
    public int runTransitions() {
        int count = 0;
        // ACTIVE → STALE
        var staleCandidates = stateStore.getStaleCandidates(STALE_THRESHOLD_MS, ARCHIVE_THRESHOLD_MS);
        for (String skillId : staleCandidates) {
            SkillLifecycleState current = stateStore.getState(skillId);
            if (current == SkillLifecycleState.PINNED) continue;
            if (current == SkillLifecycleState.ACTIVE) {
                stateStore.setState(skillId, SkillLifecycleState.STALE);
                logger.info("[AutoTransition] ACTIVE → STALE: " + skillId);
                count++;
            }
        }

        // STALE → ARCHIVED
        var archiveCandidates = stateStore.getStaleCandidates(0, ARCHIVE_THRESHOLD_MS);
        for (String skillId : archiveCandidates) {
            SkillLifecycleState current = stateStore.getState(skillId);
            if (current != SkillLifecycleState.STALE) continue;
            boolean archived = archiver.archive(skillId);
            if (archived) {
                stateStore.setState(skillId, SkillLifecycleState.ARCHIVED);
                skillRegistry.unregister(skillId);
                logger.info("[AutoTransition] STALE → ARCHIVED: " + skillId);
                count++;
            }
        }

        stateStore.save();
        return count;
    }

    /**
     * 标记技能为已使用（恢复 ARCHIVED → ACTIVE）。
     */
    public void markUsed(String skillId) {
        SkillLifecycleState current = stateStore.getState(skillId);
        stateStore.recordUse(skillId);
        if (current == SkillLifecycleState.ARCHIVED) {
            stateStore.setState(skillId, SkillLifecycleState.ACTIVE);
            archiver.restore(skillId);
            logger.info("[AutoTransition] ARCHIVED → ACTIVE（重新使用）: " + skillId);
        } else if (current == SkillLifecycleState.STALE) {
            stateStore.setState(skillId, SkillLifecycleState.ACTIVE);
            logger.info("[AutoTransition] STALE → ACTIVE（重新使用）: " + skillId);
        }
        stateStore.save();
    }
}
