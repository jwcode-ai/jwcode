package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 用户技能提供者 — 从 ~/.jwcode/skills/ 加载用户自定义 .skill.md 文件。
 */
public class UserSkillProvider implements SkillProvider {
    private static final Logger logger = Logger.getLogger(UserSkillProvider.class.getName());

    private final Path skillsDir;

    public UserSkillProvider() {
        this.skillsDir = Path.of(System.getProperty("user.home"), ".jwcode", "skills");
    }

    public UserSkillProvider(Path skillsDir) {
        this.skillsDir = skillsDir;
    }

    @Override
    public String getName() {
        return "user";
    }

    @Override
    public int priority() {
        return 10; // 低于 system，高于 plugin
    }

    @Override
    public List<SkillDefinition> discover() {
        List<SkillDefinition> definitions = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) {
            return definitions;
        }

        try (Stream<Path> files = Files.list(skillsDir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".skill.md"))
                .forEach(f -> {
                    try {
                        SkillDefinition def = SkillMarkdownParser.parse(f);
                        if (def != null) {
                            definitions.add(def);
                            logger.fine("[UserSkillProvider] 加载用户技能: " + def.id());
                        }
                    } catch (IOException e) {
                        logger.warning("[UserSkillProvider] 解析失败: " + f + " — " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.warning("[UserSkillProvider] 扫描失败: " + skillsDir + " — " + e.getMessage());
        }

        logger.info("[UserSkillProvider] 发现 " + definitions.size() + " 个用户技能");
        return definitions;
    }
}
