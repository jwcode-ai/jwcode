package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.core.skill.Skill;
import com.jwcode.core.skill.SkillLoader;
import com.jwcode.core.skill.SkillRegistry;

import java.util.List;

/**
 * Skill 命令 - 技能系统管理
 */
public class SkillCmd implements Command {
    
    private final SkillRegistry registry;
    private final SkillLoader loader;
    
    public SkillCmd() {
        this.registry = new SkillRegistry();
        String userSkillsDir = System.getProperty("user.home") + "/.jwcode/skills";
        this.loader = new SkillLoader(registry, userSkillsDir);
        // 加载用户自定义技能
        loader.loadAll();
    }
    
    @Override
    public String getName() {
        return "skill";
    }
    
    @Override
    public String getDescription() {
        return "技能管理 - 可复用的 AI 能力单元";
    }
    
    @Override
    public String getUsage() {
        return "skill list | skill search <keyword> | skill use <skill-id> | skill info <skill-id>";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            return CommandResult.error(getUsage());
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String subCommand = parts[0];
        String subArgs = parts.length > 1 ? parts[1] : "";
        
        switch (subCommand.toLowerCase()) {
            case "list":
                return handleList();
            case "search":
                return handleSearch(subArgs);
            case "use":
                return handleUse(subArgs);
            case "info":
                return handleInfo(subArgs);
            case "categories":
                return handleCategories();
            default:
                return CommandResult.error("未知子命令: " + subCommand);
        }
    }
    
    private CommandResult handleList() {
        List<Skill> skills = registry.getAll();
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║" + CliLogger.BOLD + "           可用技能 (" + skills.size() + ")              " + CliLogger.RESET + CliLogger.CYAN + "║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        
        for (Skill skill : skills) {
            output.append("  " + CliLogger.BOLD + skill.getName() + CliLogger.RESET).append("\n");
            output.append("    ID: " + skill.getId()).append("\n");
            output.append("    " + skill.getDescription()).append("\n");
            output.append("    分类: " + skill.getCategory()).append("\n");
            if (!skill.getTags().isEmpty()) {
                output.append("    标签: " + String.join(", ", skill.getTags())).append("\n");
            }
            output.append("\n");
        }
        
        output.append("提示:\n");
        output.append("  skill use <id>    - 使用技能\n");
        output.append("  skill info <id>   - 查看详情\n");
        output.append("  skill search <k>  - 搜索技能\n");
        
        return CommandResult.success(output.toString());
    }
    
    private CommandResult handleSearch(String keyword) {
        if (keyword.isEmpty()) {
            return CommandResult.error("请提供搜索关键词");
        }
        
        List<Skill> matches = registry.match(keyword);
        
        if (matches.isEmpty()) {
            return CommandResult.success("未找到匹配的技能: " + keyword);
        }
        
        StringBuilder output = new StringBuilder();
        output.append(CliLogger.GREEN + "找到 " + matches.size() + " 个匹配技能:" + CliLogger.RESET).append("\n\n");
        
        for (Skill skill : matches.subList(0, Math.min(5, matches.size()))) {
            output.append("  • " + skill.getName() + " (" + skill.getId() + ")\n");
            output.append("    " + skill.getDescription()).append("\n");
        }
        
        return CommandResult.success(output.toString());
    }
    
    private CommandResult handleUse(String skillId) {
        if (skillId.isEmpty()) {
            return CommandResult.error("请指定技能 ID");
        }
        
        return CommandResult.success("技能执行功能将在后续版本完善\n技能ID: " + skillId);
    }
    
    private CommandResult handleInfo(String skillId) {
        if (skillId.isEmpty()) {
            return CommandResult.error("请指定技能 ID");
        }
        
        Skill skill = registry.get(skillId).orElse(null);
        if (skill == null) {
            return CommandResult.error("技能不存在: " + skillId);
        }
        
        return CommandResult.success("\n" + skill.formatInfo());
    }
    
    private CommandResult handleCategories() {
        StringBuilder output = new StringBuilder();
        output.append(CliLogger.CYAN + "技能分类:" + CliLogger.RESET).append("\n\n");
        
        for (Skill.Category category : Skill.Category.values()) {
            List<Skill> skills = registry.getByCategory(category);
            output.append("  " + category + " (" + skills.size() + ")\n");
            for (Skill skill : skills) {
                output.append("    • " + skill.getName() + "\n");
            }
            output.append("\n");
        }
        
        return CommandResult.success(output.toString());
    }
}
