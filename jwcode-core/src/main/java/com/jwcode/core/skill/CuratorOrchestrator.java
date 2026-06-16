package com.jwcode.core.skill;

import com.jwcode.core.llm.LLMService;
import com.jwcode.core.llm.LLMMessage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * 策展编排器 — 协调自动流转和 LLM 审查的技能生命周期管理。
 *
 * <p>每隔约 7 天运行一次（由 {@link BackgroundReviewScheduler} 或定时触发），
 * 执行以下阶段：
 * <ol>
 *   <li>备份 — 全量快照</li>
 *   <li>自动流转 — ACTIVE → STALE → ARCHIVED</li>
 *   <li>LLM 审查 — 评估 AGENT_CREATED 技能是否需要合并/归档</li>
 *   <li>报告 — 写入策展报告</li>
 * </ol>
 *
 * <p>7 天间隔通过 {@link CuratorStateStore#getLastCuratorRun()} 控制。
 */
public class CuratorOrchestrator {

    private static final Logger logger = Logger.getLogger(CuratorOrchestrator.class.getName());

    /** 两次策展之间的最小间隔（7 天） */
    public static final long MIN_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000;

    /** LLM 审查最大迭代次数 */
    public static final int MAX_ITERATIONS = 8;

    private final CuratorStateStore stateStore;
    private final AutoTransitionEngine autoTransition;
    private final SkillArchiver archiver;
    private final SkillRegistry skillRegistry;
    private final CuratorReportWriter reportWriter;
    private final Path userSkillsDir;

    public CuratorOrchestrator(CuratorStateStore stateStore,
                                AutoTransitionEngine autoTransition,
                                SkillArchiver archiver,
                                SkillRegistry skillRegistry,
                                CuratorReportWriter reportWriter) {
        this.stateStore = stateStore;
        this.autoTransition = autoTransition;
        this.archiver = archiver;
        this.skillRegistry = skillRegistry;
        this.reportWriter = reportWriter;
        this.userSkillsDir = Path.of(System.getProperty("user.home"), ".jwcode", "skills");
    }

    /**
     * 检查是否可以运行策展（距上次运行超过 7 天）。
     */
    public boolean shouldRun() {
        long now = Instant.now().toEpochMilli();
        return (now - stateStore.getLastCuratorRun()) >= MIN_INTERVAL_MS;
    }

    /**
     * 执行完整策展流程。
     *
     * @param llmService LLM 服务（用于审查阶段，可为 null 跳过审查）
     * @return CompletableFuture 包含运行摘要
     */
    public CompletableFuture<String> runCurator(LLMService llmService) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            int transitions = 0;
            int archived = 0;
            int reviewed = 0;

            try {
                // Phase 1: 备份
                logger.info("[Curator] Phase 1: 备份");
                Path backupPath = archiver.backup();

                // Phase 2: 自动流转
                logger.info("[Curator] Phase 2: 自动流转");
                transitions = autoTransition.runTransitions();

                // Phase 3: LLM 审查（仅 AGENT_CREATED 技能）
                if (llmService != null) {
                    logger.info("[Curator] Phase 3: LLM 审查");
                    var agentSkills = skillRegistry.getByProvenance(Skill.Provenance.AGENT_CREATED);
                    if (!agentSkills.isEmpty()) {
                        String reviewResult = runLlmReview(llmService, agentSkills);
                        reviewed = agentSkills.size();
                        reportWriter.addEntry("__review__", "llm_review",
                            "审查了 " + reviewed + " 个 Agent 创建技能", true);
                    }
                }

                // Phase 4: 报告
                logger.info("[Curator] Phase 4: 报告");
                stateStore.setLastCuratorRun(Instant.now().toEpochMilli());
                stateStore.incrementRuns();
                stateStore.save();
                reportWriter.flush();

                long elapsed = System.currentTimeMillis() - start;
                String summary = String.format(
                    "策展完成 (%dms): %d 个流转, %d 个归档, %d 个审查",
                    elapsed, transitions, archived, reviewed);
                logger.info("[Curator] " + summary);
                return summary;
            } catch (Exception e) {
                logger.warning("[Curator] 策展失败: " + e.getMessage());
                return "策展失败: " + e.getMessage();
            }
        });
    }

    private String runLlmReview(LLMService llmService, List<Skill> agentSkills) {
        try {
            StringBuilder skillsList = new StringBuilder();
            for (Skill s : agentSkills) {
                skillsList.append("- ").append(s.getId()).append(": ")
                    .append(s.getName()).append(" — ")
                    .append(s.getDescription()).append("\n");
            }

            String userPrompt = CuratorPromptBuilder.CURATOR_REVIEW_PROMPT
                .replace("{skills_list}", skillsList.toString());

            var messages = List.of(
                LLMMessage.system(CuratorPromptBuilder.CURATOR_SYSTEM_PROMPT),
                LLMMessage.user(userPrompt)
            );

            var response = llmService.chat(messages).get();
            return response != null ? response.getContent() : "";
        } catch (Exception e) {
            logger.warning("[Curator] LLM 审查失败: " + e.getMessage());
            return "审查失败: " + e.getMessage();
        }
    }
}
