package com.jwcode.core.skill;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 系统技能提供者 — 从 classpath 动态扫描所有内置 .skill.md 文件。
 *
 * <p>同时支持目录扫描和类路径回退：
 * <ol>
 *   <li>通过 {@code ClassLoader.getResources("skills/")} 扫描所有 classpath 上的技能目录</li>
 *   <li>如果未扫描到任何技能，通过 {@code .skill-list} 清单逐个加载类路径资源</li>
 * </ol>
 */
public class SystemSkillProvider implements SkillProvider {
    private static final Logger logger = Logger.getLogger(SystemSkillProvider.class.getName());

    private static final String SKILLS_DIR = "skills/";
    private static final String SKILL_LIST = "skills/.skill-list";

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
        List<SkillDefinition> definitions = scanClasspathDirectories();
        if (definitions.isEmpty()) {
            definitions = scanClasspathFallback();
        }
        logger.info("[SystemSkillProvider] 发现 " + definitions.size() + " 个系统技能");
        return definitions;
    }

    /**
     * 从 classpath 目录扫描 .skill.md 文件。
     */
    private List<SkillDefinition> scanClasspathDirectories() {
        List<SkillDefinition> definitions = new ArrayList<>();
        try {
            var resources = getClass().getClassLoader().getResources(SKILLS_DIR);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                try {
                    if ("file".equals(url.getProtocol())) {
                        Path dir = Path.of(url.toURI());
                        if (Files.isDirectory(dir)) {
                            try (var files = Files.newDirectoryStream(dir, "*.skill.md")) {
                                for (var file : files) {
                                    loadFromPath(file, definitions);
                                }
                            }
                        }
                    } else if ("jar".equals(url.getProtocol())) {
                        String path = url.getPath();
                        int separator = path.indexOf("!/");
                        if (separator > 0) {
                            String jarFile = path.substring(0, separator);
                            try (var fs = FileSystems.newFileSystem(
                                    URI.create(jarFile), Collections.emptyMap())) {
                                Path jarDir = fs.getPath("/" + SKILLS_DIR);
                                if (Files.isDirectory(jarDir)) {
                                    try (var files = Files.newDirectoryStream(jarDir, "*.skill.md")) {
                                        for (var file : files) {
                                            loadFromPath(file, definitions);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.fine("[SystemSkillProvider] 扫描路径失败: " + url + " — " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("[SystemSkillProvider] 类路径扫描失败: " + e.getMessage());
        }
        return definitions;
    }

    /**
     * 通过 .skill-list 清单文件回退加载（当目录扫描不可用时）。
     */
    private List<SkillDefinition> scanClasspathFallback() {
        List<SkillDefinition> definitions = new ArrayList<>();
        try {
            var listStream = getClass().getClassLoader().getResourceAsStream(SKILL_LIST);
            if (listStream == null) {
                logger.fine("[SystemSkillProvider] 未找到 .skill-list 清单");
                return definitions;
            }
            List<String> skillFiles;
            try (var reader = new BufferedReader(new InputStreamReader(listStream, StandardCharsets.UTF_8))) {
                skillFiles = reader.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .collect(Collectors.toList());
            }
            for (String fileName : skillFiles) {
                String resourcePath = SKILLS_DIR + fileName;
                try (var in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (in == null) continue;
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    SkillDefinition def = SkillMarkdownParser.parseContent(content, resourcePath);
                    if (def != null) {
                        definitions.add(def);
                        logger.fine("[SystemSkillProvider] 从类路径加载技能: " + def.id());
                    }
                } catch (Exception e) {
                    logger.fine("[SystemSkillProvider] 加载 " + fileName + " 失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.fine("[SystemSkillProvider] 类路径回退加载失败: " + e.getMessage());
        }
        return definitions;
    }

    private void loadFromPath(Path file, List<SkillDefinition> definitions) {
        try {
            String content = Files.readString(file);
            SkillDefinition def = SkillMarkdownParser.parseContent(content, file.toString());
            if (def != null) {
                definitions.add(def);
                logger.fine("[SystemSkillProvider] 加载内置技能: " + def.id());
            }
        } catch (Exception e) {
            logger.warning("[SystemSkillProvider] 加载失败: " + file + " — " + e.getMessage());
        }
    }
}
