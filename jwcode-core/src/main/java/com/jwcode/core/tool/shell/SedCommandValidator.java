package com.jwcode.core.tool.shell;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Sed 命令安全验证器 — 检测 sed 命令中的危险操作。
 *
 * <p>参考 Claude Code sedValidation 的设计：
 * <ul>
 *   <li>检测 sed 就地编辑 (-i) 是否安全</li>
 *   <li>检测 sed 文件路径逃逸</li>
 *   <li>检测 sed 命令中的任意命令执行 (e 标志)</li>
 * </ul>
 */
public class SedCommandValidator {

    // sed 的所有 "安全" 只读标志
    private static final Set<String> SAFE_FLAGS = new HashSet<>(Arrays.asList(
        "-n", "--quiet", "--silent",
        "-r", "--regexp-extended",
        "-E",
        "-e", "--expression"
    ));

    // sed 危险标志
    private static final String[] DANGEROUS_SED_FLAGS = {
        "-i", "--in-place",
        "--follow-symlinks",
        "-z", "--null-data"
    };

    // sed 命令中的 "e" 标志（执行命令）
    private static final Pattern SED_E_FLAG = Pattern.compile(
        "s/[^/]*/[^/]*/e\\b|s/[^/]*/[^/]*/ge\\b|s/[^/]*/[^/]*/pe\\b"
    );

    // 危险 sed 命令模式
    private static final Set<String> DANGEROUS_WRITE_COMMANDS = new HashSet<>(Arrays.asList(
        "w ", "W ",  // 写入文件
        "r ", "R "   // 读取文件（可能泄露）
    ));

    /**
     * 验证结果
     */
    public record SedValidationResult(
        boolean safe,
        String issue,
        String recommendation
    ) {
        public static SedValidationResult ok() {
            return new SedValidationResult(true, null, null);
        }
        public static SedValidationResult warning(String issue, String rec) {
            return new SedValidationResult(false, issue, rec);
        }
    }

    /**
     * 验证 sed 命令是否安全。
     */
    public static SedValidationResult validate(String command) {
        if (command == null || command.isBlank()) return SedValidationResult.ok();

        String trimmed = command.trim();
        String lower = trimmed.toLowerCase();

        // 必须是以 sed 开头的命令
        if (!lower.startsWith("sed ") && !lower.startsWith("sed\t")) {
            return SedValidationResult.ok();
        }

        // 1. 检测就地编辑 (-i) — 这会修改文件
        if (hasFlag(trimmed, "-i") || hasFlag(trimmed, "--in-place")) {
            // 如果有 -i 但没有提供备份后缀，非常危险（直接覆盖无备份）
            boolean hasBackupSuffix = hasBackupSuffix(trimmed);
            if (!hasBackupSuffix) {
                return SedValidationResult.warning(
                    "sed -i 会直接覆盖原文件，无备份",
                    "使用 sed -i.bak 创建备份文件，或确认操作意图"
                );
            }
            return SedValidationResult.warning(
                "sed -i 将就地修改文件",
                "确认操作的文件路径在工作区内，建议先备份"
            );
        }

        // 2. 检测 sed e 标志（执行任意命令）
        if (SED_E_FLAG.matcher(command).find()) {
            return SedValidationResult.warning(
                "sed s///e 会执行替换结果作为 shell 命令",
                "避免使用 sed 命令的 e 标志，存在任意代码执行风险"
            );
        }

        // 3. 检测写文件命令 w/W
        for (String writeCmd : DANGEROUS_WRITE_COMMANDS) {
            // 检测独立的 w 命令（如 sed '2w output.txt'）
            if (command.matches(".*\\b" + writeCmd + "\\s+\\S+.*")) {
                return SedValidationResult.warning(
                    "sed " + writeCmd + " 会写入/读取文件",
                    "确认目标文件路径在工作区内"
                );
            }
        }

        // 4. 检测系统路径写入
        if (command.matches(".*\\s+(/etc/|/proc/|/sys/|/boot/).*")) {
            return SedValidationResult.warning(
                "sed 命令的目标路径在系统目录中",
                "系统文件不应被 sed 修改，请使用工作区内的路径"
            );
        }

        return SedValidationResult.ok();
    }

    private static boolean hasFlag(String cmd, String flag) {
        return (" " + cmd).contains(" " + flag + " ") || cmd.endsWith(" " + flag);
    }

    private static boolean hasBackupSuffix(String cmd) {
        // -i.bak, -i .bak, --in-place=.bak
        return cmd.matches(".*-i\\.[a-zA-Z0-9]+.*")
            || cmd.matches(".*-i\\s+\\S+.*")
            || cmd.matches(".*--in-place=[a-zA-Z0-9]+.*");
    }
}
