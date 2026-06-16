package com.jwcode.core.skill;

import java.util.List;
import java.util.Map;

/**
 * 技能兼容性检查器 — 检查技能文件格式与 agentskills.io 标准的兼容性。
 */
public class SkillCompatibilityChecker {

    private SkillCompatibilityChecker() {}

    /**
     * 检查技能定义是否与 agentskills.io 标准兼容。
     *
     * @return 兼容性报告
     */
    public static CompatibilityReport check(SkillDefinition def, Map<String, List<String>> extraFields) {
        CompatibilityReport report = new CompatibilityReport();
        if (def == null) {
            report.addWarning("技能定义为空");
            return report;
        }

        // 检查必填字段
        if (def.id() == null || def.id().isBlank()) {
            report.addError("缺少必填字段: id");
        }
        if (def.name() == null || def.name().isBlank()) {
            report.addWarning("缺少建议字段: name");
        }

        // 检查扩展字段
        if (extraFields != null && !extraFields.isEmpty()) {
            List<String> platforms = extraFields.get("platforms");
            if (platforms != null && !platforms.isEmpty()) {
                for (String p : platforms) {
                    if (!List.of("windows", "linux", "macos").contains(p.toLowerCase())) {
                        report.addWarning("未知平台: " + p);
                    }
                }
            }
        }

        return report;
    }

    public static class CompatibilityReport {
        private final StringBuilder report = new StringBuilder();
        private int errors = 0;
        private int warnings = 0;

        void addError(String msg) { errors++; report.append("❌ [错误] ").append(msg).append("\n"); }
        void addWarning(String msg) { warnings++; report.append("⚠️ [警告] ").append(msg).append("\n"); }

        public boolean hasErrors() { return errors > 0; }
        public boolean hasWarnings() { return warnings > 0; }
        public int getErrorCount() { return errors; }
        public int getWarningCount() { return warnings; }
        public String getReport() { return report.toString(); }
    }
}
