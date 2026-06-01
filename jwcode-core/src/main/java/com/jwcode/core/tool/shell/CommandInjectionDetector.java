package com.jwcode.core.tool.shell;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 命令注入检测器 — 检测命令中潜在的注入/逃逸尝试。
 *
 * <p>参考 Claude Code bashSecurity 的注入检测逻辑：
 * <ul>
 *   <li>Shell 元字符注入（反引号、$()、; 链式命令等）</li>
 *   <li>环境变量注入 / 路径遍历</li>
 *   <li>编码混淆检测</li>
 *   <li>特殊字符转义逃逸</li>
 * </ul>
 */
public class CommandInjectionDetector {

    // Shell 注入特征
    private static final Set<String> INJECTION_METACHARS = new HashSet<>(Arrays.asList(
        "`", "$(", "${", "$((", "<(", ">("
    ));

    // 危险路径遍历模式
    private static final Pattern PATH_TRAVERSAL = Pattern.compile(
        "\\.\\./|\\.\\.\\\\|~/.+|/(etc|proc|sys|dev|boot|var/log)/.*"
    );

    // 编码混淆检测
    private static final Pattern ENCODED_PATTERNS = Pattern.compile(
        "\\\\x[0-9a-fA-F]{2}|\\\\u[0-9a-fA-F]{4}|\\\\[0-7]{3}|%[0-9a-fA-F]{2}"
    );

    // 危险的环境变量注入
    private static final Set<String> DANGEROUS_ENV_VARS = new HashSet<>(Arrays.asList(
        "LD_PRELOAD", "LD_LIBRARY_PATH", "DYLD_INSERT_LIBRARIES",
        "PYTHONPATH", "PERL5LIB", "RUBYLIB", "CLASSPATH",
        "PATH", "IFS", "SHELLOPTS"
    ));

    // 命令分隔符（在参数中不应出现）
    private static final char[] COMMAND_SEPARATORS = {';', '&', '|'};

    /**
     * 检测结果
     */
    public record InjectionResult(
        boolean isInjected,
        String riskType,
        String description,
        int severity // 1-10
    ) {
        public static InjectionResult safe() {
            return new InjectionResult(false, null, null, 0);
        }

        public static InjectionResult injected(String type, String desc, int severity) {
            return new InjectionResult(true, type, desc, severity);
        }
    }

    /**
     * 全面检测命令注入。
     *
     * @param command 原始命令字符串
     * @param isArgument 如果为 true，按参数检测（更严格）；否则按完整命令检测
     */
    public static InjectionResult detect(String command, boolean isArgument) {
        if (command == null || command.isEmpty()) return InjectionResult.safe();

        // 1. Shell 命令替换注入（反引号、$()）
        if (command.contains("`")) {
            return InjectionResult.injected(
                "COMMAND_SUBSTITUTION",
                "命令包含反引号 ` 可能用于命令替换注入",
                8
            );
        }
        if (command.contains("$(")) {
            return InjectionResult.injected(
                "COMMAND_SUBSTITUTION",
                "命令包含 $() 语法可能用于命令替换注入",
                8
            );
        }

        // 2. 环境变量 / Shell 变量展开注入
        if (command.contains("${") && !command.contains("${")) {
            // 包含 ${...} 展开
            if (command.matches(".*\\$\\{[^}]+\\}.*")) {
                for (String dangerousVar : DANGEROUS_ENV_VARS) {
                    if (command.toLowerCase().contains(dangerousVar.toLowerCase())) {
                        return InjectionResult.injected(
                            "ENV_INJECTION",
                            "尝试注入/修改危险环境变量: " + dangerousVar,
                            9
                        );
                    }
                }
            }
        }

        // 3. 参数中的命令分隔符（更严格的检测）
        if (isArgument) {
            for (char sep : COMMAND_SEPARATORS) {
                if (command.indexOf(sep) >= 0) {
                    return InjectionResult.injected(
                        "ARGUMENT_SEPARATOR",
                        "参数中包含命令分隔符 '"+ sep +"'，可能是注入尝试",
                        7
                    );
                }
            }
        }

        // 4. 路径遍历攻击（在参数中）
        if (isArgument) {
            if (PATH_TRAVERSAL.matcher(command).matches()) {
                return InjectionResult.injected(
                    "PATH_TRAVERSAL",
                    "参数包含路径遍历模式 ../ 或敏感系统路径",
                    7
                );
            }
        }

        // 5. 编码混淆检测
        if (ENCODED_PATTERNS.matcher(command).find()) {
            return InjectionResult.injected(
                "ENCODED_PAYLOAD",
                "命令包含编码混淆字符 (\\x, \\u, %xx)，可能是隐藏 payload",
                6
            );
        }

        // 6. sudo + 任意命令（风险提示，不阻止但标记）
        if (command.toLowerCase().contains("sudo") && isArgument) {
            return InjectionResult.injected(
                "SUDO_IN_ARGUMENT",
                "参数中包含 sudo 命令，可能尝试提权",
                5
            );
        }

        // 7. 管道到 shell 解析器
        if (command.matches(".*\\|\\s*(bash|sh|zsh|dash|ksh|python|perl|ruby|php|node).*")) {
            return InjectionResult.injected(
                "PIPE_TO_INTERPRETER",
                "输出通过管道传送到脚本解释器，可能执行任意代码",
                9
            );
        }

        // 8. /dev/tcp 反向 shell 模式
        if (command.contains("/dev/tcp/") || command.contains("/dev/udp/")) {
            return InjectionResult.injected(
                "REVERSE_SHELL",
                "命令包含 /dev/tcp 反向 shell 模式",
                10
            );
        }

        // 9. nc / ncat 反向 shell
        if (command.matches(".*\\bnc\\s+.*-e\\s+/bin/(bash|sh).*")
            || command.matches(".*\\bncat\\s+.*-e\\s+/bin/(bash|sh).*")) {
            return InjectionResult.injected(
                "REVERSE_SHELL",
                "nc -e 参数是经典反向 shell 模式",
                10
            );
        }

        return InjectionResult.safe();
    }

    /**
     * 快速检查：命令是否包含任何可疑注入特征。
     */
    public static boolean isSuspicious(String command) {
        InjectionResult result = detect(command, false);
        return result.isInjected && result.severity >= 6;
    }

    /**
     * 参数安全验证：检查参数是否适合嵌入命令字符串。
     */
    public static String sanitizeArgument(String arg) {
        if (arg == null) return "";
        // 移除/转义危险字符
        return arg.replace("`", "\\`")
                  .replace("$(", "\\$(")
                  .replace(";", "\\;")
                  .replace("&&", "\\&&")
                  .replace("||", "\\||");
    }
}
