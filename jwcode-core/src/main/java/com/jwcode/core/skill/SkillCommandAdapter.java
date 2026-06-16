package com.jwcode.core.skill;

import com.jwcode.core.command.Command;
import com.jwcode.core.command.CommandResult;
import com.jwcode.core.session.Session;

import java.util.List;

/**
 * 将 Skill 适配为 Command，使其可以通过 /skill-<id> 斜杠命令调用。
 */
public class SkillCommandAdapter implements Command {

    private final Skill skill;

    public SkillCommandAdapter(Skill skill) {
        this.skill = skill;
    }

    @Override
    public String getName() {
        return "skill-" + skill.getId();
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public String getDescription() {
        return skill.getDescription();
    }

    @Override
    public String getUsage() {
        return getName() + " <输入文本>";
    }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String input = args.length > 0 ? String.join(" ", args) : "";
        SkillContext context = SkillContext.builder()
            .input(input)
            .build();
        try {
            SkillResult result = skill.execute(context);
            return CommandResult.success(result.getOutput());
        } catch (Exception e) {
            return CommandResult.error("技能执行失败: " + e.getMessage());
        }
    }
}
