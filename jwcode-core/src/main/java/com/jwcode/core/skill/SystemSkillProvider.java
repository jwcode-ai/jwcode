package com.jwcode.core.skill;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 系统技能提供者 — 从 classpath 加载内置 .skill.md 文件。
 */
public class SystemSkillProvider implements SkillProvider {
    private static final Logger logger = Logger.getLogger(SystemSkillProvider.class.getName());

    private static final String SKILLS_DIR = "skills/";
    private static final String[] BUILTIN_SKILLS = {
        "skill-creator",
        "code-review",
        "security-review"
    };

    @Override
    public String getName() {
        return "system";
    }

    @Override
    public int priority() {
        return 0; // 最高优先级
    }

    @Override
    public List<SkillDefinition> discover() {
        List<SkillDefinition> definitions = new ArrayList<>();
        for (String skillName : BUILTIN_SKILLS) {
            String resourcePath = SKILLS_DIR + skillName + ".skill.md";
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    logger.fine("[SystemSkillProvider] 技能资源未找到: " + resourcePath);
                    continue;
                }
                String content = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
                SkillDefinition def = SkillMarkdownParser.parseContent(content, "classpath:" + resourcePath);
                if (def != null) {
                    definitions.add(def);
                    logger.fine("[SystemSkillProvider] 加载内置技能: " + def.id());
                }
            } catch (Exception e) {
                logger.warning("[SystemSkillProvider] 加载失败: " + resourcePath + " — " + e.getMessage());
            }
        }
        logger.info("[SystemSkillProvider] 发现 " + definitions.size() + " 个系统技能");
        return definitions;
    }
}
