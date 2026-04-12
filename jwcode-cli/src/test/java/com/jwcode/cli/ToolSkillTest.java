package com.jwcode.cli;

import com.jwcode.core.skill.*;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.context.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

/**
 * 工具和技能测试
 */
public class ToolSkillTest {
    
    @Test
    public void testToolRegistryCreation() {
        System.out.println("【测试 ToolRegistry 创建】");
        ToolRegistry registry = ToolRegistry.createDefault();
        assertNotNull(registry, "ToolRegistry 不应为 null");
        assertTrue(registry.size() > 0, "ToolRegistry 应包含工具");
        System.out.println("✅ ToolRegistry 创建成功，包含 " + registry.size() + " 个工具");
    }
    
    @Test
    public void testToolListing() {
        System.out.println("【测试工具列表】");
        ToolRegistry registry = ToolRegistry.createDefault();
        List<Tool<?, ?, ?>> tools = registry.getAllTools();
        
        assertNotNull(tools, "工具列表不应为 null");
        assertTrue(tools.size() > 0, "工具列表不应为空");
        
        System.out.println("📋 工具列表 (前5个):");
        for (int i = 0; i < Math.min(5, tools.size()); i++) {
            Tool<?, ?, ?> tool = tools.get(i);
            System.out.println("   " + (i + 1) + ". " + tool.getName() + " - " + tool.getDescription());
        }
        System.out.println("✅ 共找到 " + tools.size() + " 个工具");
    }
    
    @Test
    public void testToolLookup() {
        System.out.println("【测试工具查找】");
        ToolRegistry registry = ToolRegistry.createDefault();
        
        String[] testTools = {"BashTool", "FileReadTool", "FileWriteTool", "GlobTool", "WebSearch"};
        for (String toolName : testTools) {
            Tool<?, ?, ?> tool = registry.findByName(toolName).orElse(null);
            assertNotNull(tool, "应找到工具: " + toolName);
            System.out.println("   ✅ " + toolName + " - 找到");
        }
    }
    
    @Test
    public void testToolExecutor() {
        System.out.println("【测试 ToolExecutor】");
        ToolRegistry registry = ToolRegistry.createDefault();
        ToolExecutor executor = new ToolExecutor(registry);
        
        assertNotNull(executor, "ToolExecutor 不应为 null");
        assertEquals(registry.size(), executor.getEnabledTools().size(), 
            "启用的工具数量应等于注册表中的工具数量");
        
        System.out.println("✅ ToolExecutor 创建成功，启用工具: " + executor.getEnabledTools().size());
    }
    
    @Test
    public void testToolExecutionContext() {
        System.out.println("【测试 ToolExecutionContext】");
        Path workingDir = Path.of(System.getProperty("user.dir"));
        ToolExecutionContext context = new ToolExecutionContext(
            null,
            workingDir,
            null
        );
        
        assertNotNull(context, "ToolExecutionContext 不应为 null");
        assertEquals(workingDir, context.getWorkingDirectory(), "工作目录应匹配");
        
        System.out.println("✅ ToolExecutionContext 创建成功");
        System.out.println("   工作目录: " + context.getWorkingDirectory());
    }
    
    @Test
    public void testSkillRegistryCreation() {
        System.out.println("【测试 SkillRegistry 创建】");
        com.jwcode.core.skill.SkillRegistry registry = new com.jwcode.core.skill.SkillRegistry();
        
        assertNotNull(registry, "SkillRegistry 不应为 null");
        assertTrue(registry.size() > 0, "SkillRegistry 应包含内置技能");
        
        System.out.println("✅ SkillRegistry 创建成功，包含 " + registry.size() + " 个技能");
    }
    
    @Test
    public void testSkillListing() {
        System.out.println("【测试技能列表】");
        com.jwcode.core.skill.SkillRegistry registry = new com.jwcode.core.skill.SkillRegistry();
        List<Skill> skills = registry.getAll();
        
        assertNotNull(skills, "技能列表不应为 null");
        assertTrue(skills.size() > 0, "技能列表不应为空");
        
        System.out.println("📋 技能列表:");
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            System.out.println("   " + (i + 1) + ". " + skill.getId() + " - " + skill.getName());
        }
        System.out.println("✅ 共找到 " + skills.size() + " 个技能");
    }
    
    @Test
    public void testSkillLookup() {
        System.out.println("【测试技能查找】");
        com.jwcode.core.skill.SkillRegistry registry = new com.jwcode.core.skill.SkillRegistry();
        
        String[] testSkills = {"explain-code", "refactor-code", "generate-tests", 
                              "debug-code", "generate-docs", "code-review"};
        for (String skillId : testSkills) {
            Skill skill = registry.get(skillId).orElse(null);
            assertNotNull(skill, "应找到技能: " + skillId);
            System.out.println("   ✅ " + skillId + " (" + skill.getName() + ") - 找到");
        }
    }
    
    @Test
    public void testSkillLoader() {
        System.out.println("【测试 SkillLoader】");
        com.jwcode.core.skill.SkillRegistry registry = new com.jwcode.core.skill.SkillRegistry();
        String skillsDir = System.getProperty("user.home") + "\\.jwcode\\skills";
        SkillLoader loader = new SkillLoader(registry, skillsDir);
        
        assertNotNull(loader, "SkillLoader 不应为 null");
        System.out.println("✅ SkillLoader 创建成功");
        System.out.println("   技能目录: " + skillsDir);
        
        // 尝试加载（目录可能不存在，但不会抛出异常）
        try {
            List<Skill> loadedSkills = loader.loadAll();
            System.out.println("   从目录加载的技能: " + loadedSkills.size() + " 个");
        } catch (Exception e) {
            System.out.println("   加载目录技能失败: " + e.getMessage());
        }
    }
}
