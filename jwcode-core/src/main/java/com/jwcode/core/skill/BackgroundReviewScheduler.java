package com.jwcode.core.skill;

import com.jwcode.core.llm.LLMService;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 背景回顾调度器 — 在对话轮次间触发记忆和技能回顾。
 *
 * <p>每轮对话结束后调用 {@link #onTurnComplete(LLMService, String)}，
 * 当轮次计数达到配置的间隔时，在后台线程中启动回顾 Agent。
 */
public class BackgroundReviewScheduler {
    private static final Logger logger = Logger.getLogger(BackgroundReviewScheduler.class.getName());

    private final BackgroundReviewConfig config;
    private final SkillRegistry skillRegistry;

    private final AtomicInteger turnsSinceMemory = new AtomicInteger(0);
    private final AtomicInteger turnsSinceSkill = new AtomicInteger(0);

    public BackgroundReviewScheduler(BackgroundReviewConfig config, SkillRegistry skillRegistry) {
        this.config = config;
        this.skillRegistry = skillRegistry;
    }

    /**
     * 每轮对话完成后调用。
     *
     * @param llmService         用于回顾 Agent 的 LLM 服务
     * @param conversationSnapshot 本轮对话的快照文本
     */
    public void onTurnComplete(LLMService llmService, String conversationSnapshot) {
        if (!config.isEnabled()) return;

        turnsSinceMemory.incrementAndGet();
        turnsSinceSkill.incrementAndGet();

        // 检查是否需要触发记忆回顾
        if (turnsSinceMemory.get() >= config.getMemoryNudgeInterval()) {
            triggerReview(ReviewPromptBuilder.ReviewType.MEMORY, llmService, conversationSnapshot);
            turnsSinceMemory.set(0);
        }

        // 检查是否需要触发技能回顾
        if (turnsSinceSkill.get() >= config.getSkillNudgeInterval()) {
            triggerReview(ReviewPromptBuilder.ReviewType.SKILL, llmService, conversationSnapshot);
            turnsSinceSkill.set(0);
        }
    }

    private void triggerReview(ReviewPromptBuilder.ReviewType type,
                                LLMService llmService,
                                String conversationSnapshot) {
        logger.fine("[BackgroundReviewScheduler] 触发 " + type + " 回顾");

        BackgroundReviewAgent agent = new BackgroundReviewAgent(
            llmService, skillRegistry, type, conversationSnapshot);

        // 在后台异步执行，不阻塞主线程
        agent.runReview().thenAccept(result -> {
            if (result != null && !result.isBlank()
                && !result.startsWith("回顾失败")) {
                logger.info("[BackgroundReview] " + type + " 完成: "
                    + result.substring(0, Math.min(200, result.length())));
            }
        });
    }

    /** 重置所有计数器 */
    public void reset() {
        turnsSinceMemory.set(0);
        turnsSinceSkill.set(0);
    }
}
