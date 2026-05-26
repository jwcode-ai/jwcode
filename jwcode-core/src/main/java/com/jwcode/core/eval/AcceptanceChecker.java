package com.jwcode.core.eval;

import com.jwcode.core.eval.EvalTask.CheckResult;
import com.jwcode.core.eval.EvalTask.CheckType;
import com.jwcode.core.eval.EvalTask.AcceptanceCheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AcceptanceChecker — 客观验收检查器。
 *
 * <p>执行 EvalTask 中定义的验收检查，无需 LLM 参与。
 * 支持文件检查、内容匹配、命令执行、编译验证等检查类型。</p>
 *
 * <p>这是混合评分体系的第一层（客观层）：</p>
 * <ul>
 *   <li>零成本 — 纯本地执行，不调用 LLM</li>
 *   <li>100% 确定 — 结果可重复，无 AI 幻觉</li>
 *   <li>硬门槛 — 客观检查 FAIL 则任务直接 FAIL，不进入 AI 评审</li>
 * </ul>
 */
public class AcceptanceChecker {

    private final Path workspaceRoot;

    public AcceptanceChecker(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * 执行单个验收检查。
     *
     * @param check 检查定义
     * @return 检查结果
     */
    public CheckResult execute(AcceptanceCheck check) {
        CheckType type = check.getType();
        try {
            return switch (type) {
                case FILE_EXISTS -> checkFileExists(check);
                case FILE_CONTENT_MATCHES -> checkFileContentMatches(check);
                case FILE_CONTENT_CONTAINS -> checkFileContentContains(check);
                case FILE_CONTENT_MATCHES_REGEX -> checkFileContentMatchesRegex(check);
                case DIR_EXISTS -> checkDirExists(check);
                case COMMAND_EXIT_CODE_ZERO -> checkCommandExitCode(check);
                case COMMAND_OUTPUT_CONTAINS -> checkCommandOutputContains(check);
                case COMPILE_SUCCESS -> checkCompileSuccess(check);
                case TEST_SUCCESS -> checkTestSuccess(check);
                case FILE_LINE_COUNT_BETWEEN -> checkFileLineCountBetween(check);
                case CUSTOM -> throw new UnsupportedOperationException("CUSTOM check not implemented");
            };
        } catch (Exception e) {
            return new CheckResult(type, "检查执行异常", false, e.getMessage());
        }
    }

    /**
     * 批量执行验收检查。
     *
     * @param checks 检查列表
     * @return 所有检查结果
     */
    public List<CheckResult> executeAll(List<AcceptanceCheck> checks) {
        List<CheckResult> results = new ArrayList<>();
        for (AcceptanceCheck check : checks) {
            results.add(execute(check));
        }
        return results;
    }

    /**
     * 判断是否全部通过。
     */
    public static boolean allPassed(List<CheckResult> results) {
        return results.stream().allMatch(CheckResult::isPassed);
    }

    /**
     * 计算客观得分 (0-100)。
     * 通过率 × 100，每个检查等权。
     */
    public static double calculateScore(List<CheckResult> results) {
        if (results.isEmpty()) return 100.0;
        long passed = results.stream().filter(CheckResult::isPassed).count();
        return (double) passed / results.size() * 100.0;
    }

    // ==================== 具体检查实现 ====================

    private CheckResult checkFileExists(AcceptanceCheck check) {
        String path = check.getParam("path", "");
        Path resolved = resolve(path);
        boolean exists = Files.exists(resolved) && !Files.isDirectory(resolved);
        String detail = exists ? "文件存在: " + resolved : "文件不存在: " + resolved;
        CheckResult cr = new CheckResult(CheckType.FILE_EXISTS, "文件存在检查: " + path, exists, detail);
        cr.setExpected("文件存在");
        cr.setActual(exists ? "存在" : "不存在");
        return cr;
    }

    private CheckResult checkFileContentMatches(AcceptanceCheck check) throws IOException {
        String path = check.getParam("path", "");
        String expectedContent = check.getParam("content", "");
        Path resolved = resolve(path);
        if (!Files.exists(resolved)) {
            return new CheckResult(CheckType.FILE_CONTENT_MATCHES,
                "文件内容匹配: " + path, false, "文件不存在: " + resolved);
        }
        String actualContent = Files.readString(resolved, StandardCharsets.UTF_8).trim();
        boolean matches = actualContent.equals(expectedContent.trim());
        String detail = matches ? "内容匹配" : "内容不匹配";
        CheckResult cr = new CheckResult(CheckType.FILE_CONTENT_MATCHES,
            "文件内容精确匹配: " + path, matches, detail);
        cr.setExpected(expectedContent);
        cr.setActual(actualContent);
        return cr;
    }

    private CheckResult checkFileContentContains(AcceptanceCheck check) throws IOException {
        String path = check.getParam("path", "");
        String substring = check.getParam("substring", "");
        Path resolved = resolve(path);
        if (!Files.exists(resolved)) {
            return new CheckResult(CheckType.FILE_CONTENT_CONTAINS,
                "文件内容包含: " + path, false, "文件不存在: " + resolved);
        }
        String content = Files.readString(resolved, StandardCharsets.UTF_8);
        boolean contains = content.contains(substring);
        String detail = contains ? "找到包含内容" : "未找到: " + substring;
        CheckResult cr = new CheckResult(CheckType.FILE_CONTENT_CONTAINS,
            "文件内容包含: " + path, contains, detail);
        cr.setExpected("包含: " + substring);
        cr.setActual(contains ? "已包含" : "未包含");
        return cr;
    }

    private CheckResult checkFileContentMatchesRegex(AcceptanceCheck check) throws IOException {
        String path = check.getParam("path", "");
        String regex = check.getParam("regex", "");
        Path resolved = resolve(path);
        if (!Files.exists(resolved)) {
            return new CheckResult(CheckType.FILE_CONTENT_MATCHES_REGEX,
                "文件内容正则匹配: " + path, false, "文件不存在: " + resolved);
        }
        String content = Files.readString(resolved, StandardCharsets.UTF_8);
        boolean matches = Pattern.compile(regex, Pattern.DOTALL).matcher(content).find();
        String detail = matches ? "正则匹配成功" : "正则未匹配: " + regex;
        CheckResult cr = new CheckResult(CheckType.FILE_CONTENT_MATCHES_REGEX,
            "文件内容正则匹配: " + path, matches, detail);
        cr.setExpected("匹配: " + regex);
        cr.setActual(matches ? "匹配成功" : "匹配失败");
        return cr;
    }

    private CheckResult checkDirExists(AcceptanceCheck check) {
        String path = check.getParam("path", "");
        Path resolved = resolve(path);
        boolean exists = Files.exists(resolved) && Files.isDirectory(resolved);
        String detail = exists ? "目录存在: " + resolved : "目录不存在: " + resolved;
        return new CheckResult(CheckType.DIR_EXISTS, "目录存在检查: " + path, exists, detail);
    }

    private CheckResult checkCommandExitCode(AcceptanceCheck check) throws Exception {
        String command = check.getParam("command", "");
        Process process = Runtime.getRuntime().exec(command, null, workspaceRoot.toFile());
        int exitCode = process.waitFor();
        boolean passed = exitCode == 0;
        String detail = passed ? "命令执行成功" : "退出码: " + exitCode;
        CheckResult cr = new CheckResult(CheckType.COMMAND_EXIT_CODE_ZERO,
            "命令退出码检查: " + command, passed, detail);
        cr.setExpected("退出码=0");
        cr.setActual("退出码=" + exitCode);
        return cr;
    }

    private CheckResult checkCommandOutputContains(AcceptanceCheck check) throws Exception {
        String command = check.getParam("command", "");
        String substring = check.getParam("substring", "");
        Process process = Runtime.getRuntime().exec(command, null, workspaceRoot.toFile());
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        boolean contains = output.contains(substring);
        String detail = contains ? "输出包含目标文本" : "输出不包含: " + substring
            + "\n实际输出:\n" + output;
        CheckResult cr = new CheckResult(CheckType.COMMAND_OUTPUT_CONTAINS,
            "命令输出检查: " + command, contains && exitCode == 0, detail);
        cr.setExpected("包含: " + substring);
        cr.setActual(contains ? "已包含" : "未包含");
        return cr;
    }

    private CheckResult checkCompileSuccess(AcceptanceCheck check) throws Exception {
        String module = check.getParam("module", ".");
        String command = "mvn compile -pl " + module + " -am -q";
        if (!module.equals(".")) {
            command = "mvn compile -pl " + module + " -am -q";
        } else {
            command = "mvn compile -q";
        }
        Process process = Runtime.getRuntime().exec(command, null, workspaceRoot.toFile());
        int exitCode = process.waitFor();
        boolean passed = exitCode == 0;
        String detail = passed ? "编译成功" : "编译失败，退出码: " + exitCode;
        CheckResult cr = new CheckResult(CheckType.COMPILE_SUCCESS, "编译检查", passed, detail);
        cr.setExpected("编译通过");
        cr.setActual(passed ? "通过" : "失败");
        return cr;
    }

    private CheckResult checkTestSuccess(AcceptanceCheck check) throws Exception {
        String module = check.getParam("module", ".");
        String command = "mvn test -pl " + module + " -am -q";
        if (!module.equals(".")) {
            command = "mvn test -pl " + module + " -am -q";
        } else {
            command = "mvn test -q";
        }
        Process process = Runtime.getRuntime().exec(command, null, workspaceRoot.toFile());
        int exitCode = process.waitFor();
        boolean passed = exitCode == 0;
        String detail = passed ? "测试全部通过" : "测试有失败，退出码: " + exitCode;
        CheckResult cr = new CheckResult(CheckType.TEST_SUCCESS, "测试检查", passed, detail);
        cr.setExpected("测试通过");
        cr.setActual(passed ? "通过" : "失败");
        return cr;
    }

    private CheckResult checkFileLineCountBetween(AcceptanceCheck check) throws IOException {
        String path = check.getParam("path", "");
        int min = check.getParam("min", 0);
        int max = check.getParam("max", Integer.MAX_VALUE);
        Path resolved = resolve(path);
        if (!Files.exists(resolved)) {
            return new CheckResult(CheckType.FILE_LINE_COUNT_BETWEEN,
                "文件行数检查: " + path, false, "文件不存在: " + resolved);
        }
        long lineCount = Files.lines(resolved).count();
        boolean passed = lineCount >= min && lineCount <= max;
        String detail = passed ? "行数 " + lineCount + " 在 [" + min + ", " + max + "] 范围内"
            : "行数 " + lineCount + " 不在 [" + min + ", " + max + "] 范围内";
        CheckResult cr = new CheckResult(CheckType.FILE_LINE_COUNT_BETWEEN,
            "文件行数检查: " + path, passed, detail);
        cr.setExpected("行数在 " + min + "-" + max + " 之间");
        cr.setActual("实际行数: " + lineCount);
        return cr;
    }

    private Path resolve(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p;
        }
        return workspaceRoot.resolve(p).normalize();
    }
}
