package com.jwcode.core.command;

import com.jwcode.core.session.Session;
import com.jwcode.core.skill.SkillRegistry;

/**
 * /skills 命令 — 列出所有可用技能。
 */
public class SkillsCommand implements Command {

    private final SkillRegistry skillRegistry;

    public SkillsCommand(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String getName() {
        return "skills";
    }

    @Override
    public String getDescription() {
        return "列出所有可用技能";
    }

    @Override
    public String getUsage() {
        return "skills [search]";
    }

    @Override
    public CommandResult execute(String[] args, Session session) {
        var summaries = skillRegistry.listSummaries();

        if (summaries.isEmpty()) {
            return CommandResult.success("没有可用的技能。");
        }

        // 过滤
        String filter = args.length > 0 ? args[0].toLowerCase() : null;
        var filtered = summaries.stream()
            .filter(s -> filter == null
                || s.name().toLowerCase().contains(filter)
                || s.id().toLowerCase().contains(filter)
                || s.tags().stream().anyMatch(t -> t.toLowerCase().contains(filter)))
            .toList();

        if (filtered.isEmpty()) {
            return CommandResult.success("没有匹配的技能。");
        }

        var sb = new StringBuilder();
        sb.append("可用技能 (").append(filtered.size()).append("/").append(summaries.size()).append("):\n\n");

        int maxNameLen = filtered.stream().mapToInt(s -> s.name().length()).max().orElse(0);

        for (var summary : filtered) {
            String cmdName = "/skill-" + summary.id();
            sb.append(String.format("  %-24s%-" + (maxNameLen + 2) + "s%s",
                cmdName, summary.name(), summary.description()));
            sb.append("\n");
        }

        sb.append("\n使用 /help 查看所有命令。");
        return CommandResult.success(sb.toString());
    }
}
