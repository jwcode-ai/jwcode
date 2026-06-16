package com.jwcode.core.skill;

import com.jwcode.core.command.CommandRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 扫描 SkillRegistry 并将每个技能注册为 CommandRegistry 中的斜杠命令。
 *
 * <p>命令名称格式：/skill-{skillId}
 * 冲突处理：若命令名已存在，追加数字后缀（-1, -2, ...）。
 */
public class SkillCommandScanner {
    private static final Logger logger = Logger.getLogger(SkillCommandScanner.class.getName());

    private final SkillRegistry skillRegistry;
    private final CommandRegistry commandRegistry;
    private final Set<String> registered = new HashSet<>();

    public SkillCommandScanner(SkillRegistry skillRegistry, CommandRegistry commandRegistry) {
        this.skillRegistry = skillRegistry;
        this.commandRegistry = commandRegistry;
    }

    /**
     * 扫描并注册所有当前技能为命令。
     */
    public void scanAndRegister() {
        int count = 0;
        for (Skill skill : skillRegistry.getAll()) {
            if (registerSkillCommand(skill)) {
                count++;
            }
        }
        logger.info("[SkillCommandScanner] 注册了 " + count + " 个技能命令");
    }

    /**
     * 注册单个技能为命令。
     */
    public boolean registerSkillCommand(Skill skill) {
        String baseName = "skill-" + skill.getId();
        String commandName = baseName;

        // 冲突检测：如果命令名已存在，追加数字后缀
        int suffix = 1;
        while (commandRegistry.hasCommand(commandName)) {
            suffix++;
            commandName = baseName + "-" + suffix;
        }

        var adapter = new SkillCommandAdapter(skill);
        commandRegistry.register(adapter);
        registered.add(skill.getId());

        logger.fine("[SkillCommandScanner] 注册技能命令: /" + commandName);
        return true;
    }

    /**
     * 注销所有已注册的技能命令。
     */
    public void unregisterAll() {
        // CommandRegistry 暂不支持反注册，仅清除内部追踪
        registered.clear();
    }
}
