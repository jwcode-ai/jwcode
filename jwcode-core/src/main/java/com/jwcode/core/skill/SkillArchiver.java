package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * 技能归档器 — 将技能文件移动到 .archive/ 目录，支持备份和恢复。
 */
public class SkillArchiver {

    private static final Logger logger = Logger.getLogger(SkillArchiver.class.getName());

    private final Path skillsDir;
    private final Path archiveDir;

    public SkillArchiver(Path skillsDir) {
        this.skillsDir = skillsDir;
        this.archiveDir = skillsDir.resolve(".archive");
    }

    /**
     * 归档技能文件到 .archive/ 目录。
     *
     * @param skillId    技能 ID
     * @return true 如果归档成功
     */
    public boolean archive(String skillId) {
        try {
            Files.createDirectories(archiveDir);
            Path source = skillsDir.resolve(skillId + ".skill.md");
            if (!Files.isRegularFile(source)) {
                logger.fine("[SkillArchiver] 技能文件不存在，跳过归档: " + skillId);
                return false;
            }
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
            Path target = archiveDir.resolve(skillId + "." + timestamp + ".skill.md");
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            logger.info("[SkillArchiver] 归档技能: " + skillId + " -> " + target.getFileName());
            return true;
        } catch (IOException e) {
            logger.warning("[SkillArchiver] 归档失败: " + skillId + " — " + e.getMessage());
            return false;
        }
    }

    /**
     * 从 .archive/ 恢复最新版本的技能文件。
     *
     * @param skillId    技能 ID
     * @return true 如果恢复成功
     */
    public boolean restore(String skillId) {
        try {
            if (!Files.isDirectory(archiveDir)) return false;
            try (var files = Files.list(archiveDir)) {
                var latest = files
                    .filter(f -> f.getFileName().toString().startsWith(skillId + "."))
                    .max((a, b) -> a.getFileName().compareTo(b.getFileName()));
                if (latest.isPresent()) {
                    Path target = skillsDir.resolve(skillId + ".skill.md");
                    Files.copy(latest.get(), target, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("[SkillArchiver] 恢复技能: " + skillId + " <- " + latest.get().getFileName());
                    return true;
                }
            }
        } catch (IOException e) {
            logger.warning("[SkillArchiver] 恢复失败: " + skillId + " — " + e.getMessage());
        }
        return false;
    }

    /**
     * 创建全量备份（拷贝 skills 目录快照）。
     *
     * @return 备份目录路径，失败返回 null
     */
    public Path backup() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
        Path backupDir = skillsDir.resolve(".backup-" + timestamp);
        try {
            Files.createDirectories(backupDir);
            try (var files = Files.list(skillsDir)) {
                files.filter(f -> f.getFileName().toString().endsWith(".skill.md"))
                    .forEach(f -> {
                        try {
                            Files.copy(f, backupDir.resolve(f.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            logger.warning("[SkillArchiver] 备份文件失败: " + f.getFileName());
                        }
                    });
            }
            logger.info("[SkillArchiver] 全量备份完成: " + backupDir);
            return backupDir;
        } catch (IOException e) {
            logger.warning("[SkillArchiver] 备份失败: " + e.getMessage());
            return null;
        }
    }
}
