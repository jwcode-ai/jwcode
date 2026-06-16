package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 用户技能提供者 — 从 ~/.jwcode/skills/ 加载用户自定义 .skill.md 文件。
 *
 * <p>支持额外技能目录（通过外部配置注入），自带 mtime 缓存避免重复扫描。
 */
public class UserSkillProvider implements SkillProvider {
    private static final Logger logger = Logger.getLogger(UserSkillProvider.class.getName());

    private final Path skillsDir;
    private final List<Path> externalDirs;
    private final ConcurrentHashMap<Path, Long> dirMtimes = new ConcurrentHashMap<>();

    public UserSkillProvider() {
        this(Path.of(System.getProperty("user.home"), ".jwcode", "skills"));
    }

    public UserSkillProvider(Path skillsDir) {
        this(skillsDir, List.of());
    }

    public UserSkillProvider(Path skillsDir, List<Path> externalDirs) {
        this.skillsDir = skillsDir;
        this.externalDirs = externalDirs != null ? new ArrayList<>(externalDirs) : List.of();
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
        scanDirectory(skillsDir, definitions);
        for (Path extDir : externalDirs) {
            scanDirectory(extDir, definitions);
        }
        logger.info("[UserSkillProvider] 发现 " + definitions.size() + " 个用户技能"
            + (externalDirs.isEmpty() ? "" : "（含 " + externalDirs.size() + " 个外部目录）"));
        return definitions;
    }

    private void scanDirectory(Path dir, List<SkillDefinition> definitions) {
        if (!Files.isDirectory(dir)) return;

        // mtime 检测：目录未变更则跳过
        try {
            long currentMtime = Files.getLastModifiedTime(dir).toMillis();
            Long cached = dirMtimes.get(dir);
            if (cached != null && currentMtime <= cached) {
                logger.fine("[UserSkillProvider] 目录未变更，跳过: " + dir);
                return;
            }
            dirMtimes.put(dir, currentMtime);
        } catch (IOException e) {
            logger.fine("[UserSkillProvider] mtime 检测失败: " + e.getMessage());
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".skill.md"))
                .forEach(f -> {
                    try {
                        SkillDefinition def = SkillMarkdownParser.parse(f, Skill.Provenance.USER_MANUAL);
                        if (def != null) {
                            definitions.add(def);
                            logger.fine("[UserSkillProvider] 加载用户技能: " + def.id() + " <- " + f);
                        }
                    } catch (IOException e) {
                        logger.warning("[UserSkillProvider] 解析失败: " + f + " — " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.warning("[UserSkillProvider] 扫描失败: " + dir + " — " + e.getMessage());
        }
    }

    /**
     * 获取已缓存的目录 mtime 表（用于调试/监控）。
     */
    public ConcurrentHashMap<Path, Long> getDirMtimes() {
        return dirMtimes;
    }
}
