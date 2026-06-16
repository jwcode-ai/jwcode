package com.jwcode.core.skill;

import java.util.*;
import java.util.logging.Logger;

/**
 * AI 驱动的技能选择器 — 基于任务分析选择最匹配的技能。
 *
 * <p>使用启发式关键词匹配 + 评分机制，未来可接入轻量级 LLM 做语义选择。
 */
public class SkillSelector {
    private static final Logger logger = Logger.getLogger(SkillSelector.class.getName());

    private final SkillRegistry registry;

    public SkillSelector(SkillRegistry registry) {
        this.registry = registry;
    }

    /**
     * 根据用户输入选择最匹配的技能。
     *
     * @param userInput 用户输入的原始文本
     * @param maxSkills 最多返回的技能数
     * @return 按匹配度降序排列的技能列表
     */
    public List<Skill> select(String userInput, int maxSkills) {
        if (userInput == null || userInput.isBlank()) return List.of();

        List<Skill> allSkills = registry.getAll();
        if (allSkills.isEmpty()) return List.of();

        // 计算每个技能的匹配分数
        List<ScoredSkill> scored = new ArrayList<>();
        for (Skill skill : allSkills) {
            int score = computeScore(skill, userInput);
            if (score > 0) {
                scored.add(new ScoredSkill(skill, score));
            }
        }

        // 按分数降序排列
        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        List<Skill> result = new ArrayList<>();
        for (int i = 0; i < Math.min(maxSkills, scored.size()); i++) {
            result.add(scored.get(i).skill);
        }

        if (!result.isEmpty()) {
            logger.info("[SkillSelector] 为输入选择 " + result.size() + " 个技能: "
                + result.stream().map(Skill::getId).reduce((a, b) -> a + ", " + b).orElse(""));
        }

        return result;
    }

    /**
     * 使用摘要进行快速匹配 — 不涉及完整技能加载。
     *
     * @param userInput 用户输入
     * @param maxSkills 最多返回的技能数
     * @return 按匹配度降序排列的技能摘要列表
     */
    public List<SkillSummary> selectSummaries(String userInput, int maxSkills) {
        if (userInput == null || userInput.isBlank()) return List.of();

        List<SkillSummary> allSummaries = registry.listSummaries();
        if (allSummaries.isEmpty()) return List.of();

        List<ScoredSummary> scored = new ArrayList<>();
        for (SkillSummary summary : allSummaries) {
            int score = computeSummaryScore(summary, userInput);
            if (score > 0) {
                scored.add(new ScoredSummary(summary, score));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        List<SkillSummary> result = new ArrayList<>();
        for (int i = 0; i < Math.min(maxSkills, scored.size()); i++) {
            result.add(scored.get(i).summary);
        }
        return result;
    }

    /**
     * 选择单个最佳匹配技能。
     */
    public Optional<Skill> selectBest(String userInput) {
        List<Skill> selected = select(userInput, 1);
        return selected.isEmpty() ? Optional.empty() : Optional.of(selected.get(0));
    }

    /**
     * 获取所有应 EAGER 注入的技能。
     */
    public List<Skill> getEagerSkills() {
        return registry.getAll().stream()
            .filter(s -> s.getInjectionStrategy() != null
                && s.getInjectionStrategy() == SkillDefinition.InjectionStrategy.EAGER)
            .toList();
    }

    private int computeScore(Skill skill, String input) {
        String lowerInput = input.toLowerCase();
        int score = 0;

        // 触发模式匹配（精确匹配得分最高）
        String trigger = skill.getTriggerPattern();
        if (trigger != null && !trigger.isBlank()) {
            if (lowerInput.contains(trigger.toLowerCase())) {
                score += 80;
            }
        }

        // 名称匹配
        if (skill.getName() != null
            && lowerInput.contains(skill.getName().toLowerCase())) {
            score += 50;
        }

        // ID 匹配
        if (skill.getId() != null
            && lowerInput.contains(skill.getId().toLowerCase())) {
            score += 40;
        }

        // 标签匹配
        if (skill.getTags() != null) {
            for (String tag : skill.getTags()) {
                if (lowerInput.contains(tag.toLowerCase())) {
                    score += 25;
                }
            }
        }

        // 描述关键词匹配
        if (skill.getDescription() != null) {
            String[] words = skill.getDescription().toLowerCase().split("\\s+");
            for (String word : words) {
                if (word.length() > 2 && lowerInput.contains(word)) {
                    score += 10;
                }
            }
        }

        return score;
    }

    /**
     * 对技能摘要计算匹配分数（与 computeScore 逻辑一致，但操作 Summary）。
     */
    private int computeSummaryScore(SkillSummary summary, String input) {
        String lowerInput = input.toLowerCase();
        int score = 0;

        // 名称匹配
        if (summary.name() != null && lowerInput.contains(summary.name().toLowerCase())) {
            score += 50;
        }

        // ID 匹配
        if (summary.id() != null && lowerInput.contains(summary.id().toLowerCase())) {
            score += 40;
        }

        // 标签匹配
        if (summary.tags() != null) {
            for (String tag : summary.tags()) {
                if (lowerInput.contains(tag.toLowerCase())) {
                    score += 25;
                }
            }
        }

        // 描述关键词匹配
        if (summary.description() != null) {
            String[] words = summary.description().toLowerCase().split("\\s+");
            for (String word : words) {
                if (word.length() > 2 && lowerInput.contains(word)) {
                    score += 10;
                }
            }
        }

        return score;
    }

    private record ScoredSkill(Skill skill, int score) {}
    private record ScoredSummary(SkillSummary summary, int score) {}
}
