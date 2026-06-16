package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 策展报告写入器 — 将策展运行结果写入报告文件。
 *
 * <p>报告存储到 {@code ~/.jwcode/skills/.curator-reports/} 目录。
 */
public class CuratorReportWriter {

    private static final Logger logger = Logger.getLogger(CuratorReportWriter.class.getName());

    private final Path reportsDir;
    private final List<CuratorEntry> entries = new ArrayList<>();

    public record CuratorEntry(
        String skillId,
        String action,
        String reason,
        boolean success
    ) {}

    public CuratorReportWriter() {
        this(Path.of(System.getProperty("user.home"), ".jwcode", "skills", ".curator-reports"));
    }

    public CuratorReportWriter(Path reportsDir) {
        this.reportsDir = reportsDir;
    }

    /**
     * 添加一条策展记录。
     */
    public void addEntry(String skillId, String action, String reason, boolean success) {
        entries.add(new CuratorEntry(skillId, action, reason, success));
    }

    /**
     * 将所有记录写入报告文件并清空缓冲区。
     *
     * @return 报告文件路径
     */
    public Path flush() {
        if (entries.isEmpty()) return null;
        try {
            Files.createDirectories(reportsDir);
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
            Path reportFile = reportsDir.resolve("curator-" + timestamp + ".md");

            StringBuilder sb = new StringBuilder();
            sb.append("# 策展报告 ").append(timestamp).append("\n\n");
            sb.append("| 技能 ID | 操作 | 理由 | 状态 |\n");
            sb.append("|---|---|---|---|\n");
            for (CuratorEntry e : entries) {
                sb.append("| ").append(e.skillId()).append(" | ")
                  .append(e.action()).append(" | ")
                  .append(e.reason()).append(" | ")
                  .append(e.success() ? "✓" : "✗").append(" |\n");
            }
            sb.append("\n---\n\n");
            sb.append("共 ").append(entries.size()).append(" 条记录。\n");

            Path tmp = reportFile.resolveSibling(reportFile.getFileName() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, reportFile, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);

            entries.clear();
            logger.info("[CuratorReportWriter] 报告已写入: " + reportFile);
            return reportFile;
        } catch (IOException e) {
            logger.warning("[CuratorReportWriter] 写入报告失败: " + e.getMessage());
            return null;
        }
    }
}
