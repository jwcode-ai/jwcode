package com.jwcode.core.skill;

import com.jwcode.core.llm.LLMService;
import com.jwcode.core.tool.ToolWhitelistManager;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * 背景回顾 Agent — 在后台线程中运行，审查对话历史并创建/更新技能或记忆。
 *
 * <p>此 Agent 作为 forked agent 运行：
 * <ul>
 *   <li>继承父 Agent 的 LLM 服务以复用 prompt cache</li>
 *   <li>使用受限的工具白名单（仅记忆和技能管理工具）</li>
 *   <li>作为守护线程运行，不阻塞主会话</li>
 * </ul>
 */
public class BackgroundReviewAgent {
    private static final Logger logger = Logger.getLogger(BackgroundReviewAgent.class.getName());

    private final LLMService llmService;
    private final SkillRegistry skillRegistry;
    private final ReviewPromptBuilder.ReviewType reviewType;
    private final String conversationSnapshot;

    private static final Set<String> ALLOWED_TOOLS = Set.of(
        "memory-read", "memory-write",
        "skill-view", "skill-manage",
        "file-read"
    );

    public BackgroundReviewAgent(LLMService llmService, SkillRegistry skillRegistry,
                                  ReviewPromptBuilder.ReviewType reviewType,
                                  String conversationSnapshot) {
        this.llmService = llmService;
        this.skillRegistry = skillRegistry;
        this.reviewType = reviewType;
        this.conversationSnapshot = conversationSnapshot;
    }

    /**
     * 在后台执行回顾。
     *
     * @return CompletableFuture 包含回顾摘要
     */
    public CompletableFuture<String> runReview() {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();

            // 设置工具白名单
            ToolWhitelistManager.getInstance().setWhitelist(ALLOWED_TOOLS);

            try {
                // 构建回顾提示
                String systemPrompt = ReviewPromptBuilder.buildPrompt(reviewType);
                String userPrompt = buildUserPrompt();

                // 调用 LLM 执行回顾
                var messages = java.util.List.of(
                    com.jwcode.core.llm.LLMMessage.system(systemPrompt),
                    com.jwcode.core.llm.LLMMessage.user(userPrompt)
                );

                var response = llmService.chat(messages).get();
                String result = response != null ? response.getContent() : "";

                long elapsed = System.currentTimeMillis() - start;
                logger.info("[BackgroundReviewAgent] " + reviewType
                    + " 回顾完成 (" + elapsed + "ms)");

                return result;
            } catch (Exception e) {
                logger.warning("[BackgroundReviewAgent] 回顾失败: " + e.getMessage());
                return "回顾失败: " + e.getMessage();
            } finally {
                // 清除白名单
                ToolWhitelistManager.getInstance().clearWhitelist();
            }
        });
    }

    private String buildUserPrompt() {
        return """
            请审查以下对话历史，识别值得记录的技巧、工作流或用户偏好。

            --- 对话历史开始 ---
            """ + conversationSnapshot + """
            --- 对话历史结束 ---

            请根据你的职责（记忆回顾/技能回顾/组合回顾）输出审查结果。
            """;
    }
}
