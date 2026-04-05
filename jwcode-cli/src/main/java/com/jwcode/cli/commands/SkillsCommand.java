package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkillsCommand - /skills 命令
 * 
 * 功能说明：
 * 技能管理，查看、启用、禁用自定义技能。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/skills", description = "技能管理")
public class SkillsCommand implements Runnable {
    
    @Parameters(index = "0", description = "操作类型 (list, enable, disable, add)", arity = "0..1")
    private String action;
    
    @Parameters(index = "1", description = "技能名称", arity = "0..1")
    private String skillName;
    
    @Option(names = {"-l", "--list"}, description = "列出所有技能")
    private boolean listOnly;
    
    @Option(names = {"-e", "--enabled"}, description = "只显示已启用的技能")
    private boolean enabledOnly;
    
    @Option(names = {"-d", "--disabled"}, description = "只显示已禁用的技能")
    private boolean disabledOnly;
    
    private static final Map<String, SkillInfo> skills = new ConcurrentHashMap<>();
    
    static {
        // 初始化默认技能
        skills.put("code-review", new SkillInfo("code-review", "代码审查", "自动审查代码质量", true));
        skills.put("test-gen", new SkillInfo("test-gen", "测试生成", "自动生成单元测试", true));
        skills.put("doc-writer", new SkillInfo("doc-writer", "文档编写", "自动生成文档", false));
        skills.put("refactor", new SkillInfo("refactor", "代码重构", "智能代码重构建议", false));
        skills.put("security-check", new SkillInfo("security-check", "安全检查", "安全漏洞检测", true));
    }
    
    @Override
    public void run() {
        if (listOnly || action == null) {
            listSkills();
            return;
        }
        
        switch (action.toLowerCase()) {
            case "list":
                listSkills();
                break;
            case "enable":
                enableSkill();
                break;
            case "disable":
                disableSkill();
                break;
            case "add":
                addSkill();
                break;
            default:
                showHelp();
        }
    }
    
    private void listSkills() {
        System.out.println("=== 技能列表 ===");
        System.out.println();
        
        boolean found = false;
        for (SkillInfo skill : skills.values()) {
            if (enabledOnly && !skill.enabled) continue;
            if (disabledOnly && skill.enabled) continue;
            
            String status = skill.enabled ? "[已启用]" : "[已禁用]";
            System.out.println(status + " " + skill.name + " - " + skill.description);
            found = true;
        }
        
        if (!found) {
            System.out.println("(无符合条件的技能)");
        }
        
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /skills enable <name>   启用技能");
        System.out.println("  /skills disable <name>  禁用技能");
        System.out.println("  /skills list -e         只显示已启用的技能");
        System.out.println("  /skills list -d         只显示已禁用的技能");
    }
    
    private void enableSkill() {
        if (skillName == null) {
            System.out.println("错误：需要指定技能名称");
            System.out.println("用法：/skills enable <name>");
            return;
        }
        
        SkillInfo skill = skills.get(skillName);
        if (skill == null) {
            System.out.println("技能不存在：" + skillName);
            return;
        }
        
        skill.enabled = true;
        System.out.println("已启用技能：" + skill.name);
    }
    
    private void disableSkill() {
        if (skillName == null) {
            System.out.println("错误：需要指定技能名称");
            System.out.println("用法：/skills disable <name>");
            return;
        }
        
        SkillInfo skill = skills.get(skillName);
        if (skill == null) {
            System.out.println("技能不存在：" + skillName);
            return;
        }
        
        skill.enabled = false;
        System.out.println("已禁用技能：" + skill.name);
    }
    
    private void addSkill() {
        System.out.println("添加技能功能需要 AI 后端支持");
    }
    
    private void showHelp() {
        System.out.println("技能管理命令");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /skills list              - 列出所有技能");
        System.out.println("  /skills enable <name>     - 启用技能");
        System.out.println("  /skills disable <name>    - 禁用技能");
        System.out.println();
        System.out.println("可用技能:");
        for (SkillInfo skill : skills.values()) {
            System.out.println("  " + skill.name + " - " + skill.description);
        }
    }
    
    /**
     * 技能信息
     */
    public static class SkillInfo {
        public final String id;
        public final String name;
        public final String description;
        public boolean enabled;
        
        public SkillInfo(String id, String name, String description, boolean enabled) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.enabled = enabled;
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "name", name,
                    "description", description,
                    "enabled", enabled
            );
        }
    }
    
    public static List<String> getEnabledSkills() {
        List<String> enabled = new ArrayList<>();
        for (SkillInfo skill : skills.values()) {
            if (skill.enabled) {
                enabled.add(skill.id);
            }
        }
        return enabled;
    }
}