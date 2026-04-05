package com.jwcode.core.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 技能加载器
 * 
 * 支持从本地文件系统加载技能
 */
@Slf4j
public class SkillLoader {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String SKILL_FILE_EXTENSION = ".skill.json";
    
    private final SkillRegistry registry;
    private final Path skillsDir;
    
    public SkillLoader(SkillRegistry registry, String skillsDir) {
        this.registry = registry;
        this.skillsDir = Paths.get(skillsDir);
    }
    
    /**
     * 加载所有技能
     */
    public List<Skill> loadAll() {
        List<Skill> loaded = new ArrayList<>();
        
        if (!Files.exists(skillsDir)) {
            try {
                Files.createDirectories(skillsDir);
                log.info("[SkillLoader] 创建技能目录: " + skillsDir);
            } catch (IOException e) {
                log.error("[SkillLoader] 创建技能目录失败: " + e.getMessage());
                return loaded;
            }
        }
        
        try {
            Files.walk(skillsDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(SKILL_FILE_EXTENSION))
                .forEach(path -> {
                    try {
                        Skill skill = loadFromFile(path.toFile());
                        if (skill != null) {
                            registry.register(skill);
                            loaded.add(skill);
                        }
                    } catch (Exception e) {
                        log.error("[SkillLoader] 加载技能失败 " + path + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.error("[SkillLoader] 遍历技能目录失败: " + e.getMessage());
        }
        
        log.info("[SkillLoader] 从 " + skillsDir + " 加载了 " + loaded.size() + " 个技能");
        return loaded;
    }
    
    /**
     * 从文件加载技能
     */
    public Skill loadFromFile(File file) throws IOException {
        log.debug("[SkillLoader] 加载技能文件: " + file.getAbsolutePath());
        
        Skill skill = mapper.readValue(file, Skill.class);
        skill.setSource(file.getAbsolutePath());
        skill.setStatus(Skill.LoadStatus.LOADED);
        skill.setLoadedAt(System.currentTimeMillis());
        
        return skill;
    }
    
    /**
     * 保存技能到文件
     */
    public void saveToFile(Skill skill) throws IOException {
        if (!Files.exists(skillsDir)) {
            Files.createDirectories(skillsDir);
        }
        
        String filename = skill.getId() + SKILL_FILE_EXTENSION;
        Path skillPath = skillsDir.resolve(filename);
        
        mapper.writerWithDefaultPrettyPrinter()
            .writeValue(skillPath.toFile(), skill);
        
        log.info("[SkillLoader] 保存技能到: " + skillPath);
    }
    
    /**
     * 创建示例技能文件
     */
    public void createExampleSkill() throws IOException {
        Skill example = Skill.builder()
            .id("example-custom-skill")
            .name("示例自定义技能")
            .description("这是一个示例技能，展示如何创建自定义技能")
            .category(Skill.Category.CUSTOM)
            .version("1.0.0")
            .author("user")
            .tags(List.of("example", "custom", "演示"))
            .systemPrompt("你是一个示例技能。当用户调用时，请友好地介绍自己。")
            .examples(List.of(
                Skill.Example.builder()
                    .input("使用示例技能")
                    .output("你好！我是示例自定义技能。")
                    .build()
            ))
            .build();
        
        saveToFile(example);
    }
    
    /**
     * 获取技能目录路径
     */
    public String getSkillsDirectory() {
        return skillsDir.toString();
    }
}
