package com.jwcode.core.skill;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 技能目录构建器 — 生成 {@code <available_skills>} 系统提示词块。
 *
 * <p>EAGER 注入技能排在顶部，LAZY/HYBRID 排在下方目录中。
 */
public class SkillsPromptBuilder {

    private SkillsPromptBuilder() {}

    /**
     * 从技能摘要列表构建目录字符串。
     *
     * @param summaries 技能摘要列表
     * @return 格式化的技能目录 Markdown 块
     */
    public static String build(List<SkillSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "";
        }

        var eager = summaries.stream()
            .filter(s -> s.injectionStrategy() == SkillDefinition.InjectionStrategy.EAGER)
            .toList();
        var lazy = summaries.stream()
            .filter(s -> s.injectionStrategy() != SkillDefinition.InjectionStrategy.EAGER)
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("<available_skills>\n\n");

        if (!eager.isEmpty()) {
            sb.append("【始终可用技能】\n");
            sb.append("| ID | 名称 | 描述 | 分类 |\n");
            sb.append("|---|---|---|---|\n");
            for (SkillSummary s : eager) {
                sb.append("| ").append(escapeMd(s.id())).append(" | ")
                  .append(escapeMd(s.name())).append(" | ")
                  .append(escapeMd(truncate(s.description(), 80))).append(" | ")
                  .append(s.category()).append(" |\n");
            }
            sb.append("\n");
        }

        if (!lazy.isEmpty()) {
            sb.append("【可用技能目录（按需加载）】\n");
            sb.append("| ID | 名称 | 描述 | 分类 | 触发词 |\n");
            sb.append("|---|---|---|---|---|\n");
            for (SkillSummary s : lazy) {
                sb.append("| ").append(escapeMd(s.id())).append(" | ")
                  .append(escapeMd(s.name())).append(" | ")
                  .append(escapeMd(truncate(s.description(), 80))).append(" | ")
                  .append(s.category()).append(" | ")
                  .append(formatTags(s.tags())).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("使用 /skill-<id> 激活技能。\n");
        sb.append("</available_skills>");
        return sb.toString();
    }

    private static String escapeMd(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, max) + "...";
    }

    private static String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return tags.stream()
            .map(t -> "`" + t + "`")
            .collect(Collectors.joining(" "));
    }
}
