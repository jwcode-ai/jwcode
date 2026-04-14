package com.jwcode.core.advanced.analyzer;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 命令错误恢复顾问 - 分析 Shell/Bash/Glob 命令失败原因，给出恢复策略
 * 
 * 核心能力：
 * 1. 识别常见的命令错误模式（正则转义、路径不存在、权限不足、命令未找到）
 * 2. 给出明确的下一步建议（替换命令、修改参数、切换工具）
 * 3. 防止 Agent 在同一错误模式上死循环
 */
public class CommandRecoveryAdvisor {
    
    private final List<FailurePattern> patterns;
    
    public CommandRecoveryAdvisor() {
        this.patterns = initPatterns();
    }
    
    /**
     * 分析失败输出，给出恢复建议
     * 
     * @param command 原始命令
     * @param stdout 标准输出
     * @param stderr 标准错误
     * @param exitCode 退出码
     * @return 恢复建议
     */
    public RecoveryAdvice analyze(String command, String stdout, String stderr, int exitCode) {
        String combined = (command + " " + stdout + " " + stderr).toLowerCase();
        
        for (FailurePattern pattern : patterns) {
            if (pattern.matches(combined, exitCode)) {
                return pattern.generateAdvice(command, stdout, stderr);
            }
        }
        
        // 默认建议
        return new RecoveryAdvice(
            RecoveryAction.RETRY_WITH_ALTERNATIVE,
            "未知错误，建议使用更简单的命令替代",
            suggestAlternative(command),
            50
        );
    }
    
    /**
     * 判断当前错误是否属于"环境问题"（如目录不存在）而非"命令写法问题"
     */
    public boolean isEnvironmentIssue(String command, String stdout, String stderr, int exitCode) {
        String combined = (stdout + " " + stderr).toLowerCase();
        return combined.contains("no such file or directory") ||
               combined.contains("cannot find the path") ||
               combined.contains("path does not exist") ||
               combined.contains("系统找不到指定的路径") ||
               combined.contains("找不到文件");
    }
    
    /**
     * 判断当前错误是否属于"命令写法问题"（如正则转义、语法错误）
     */
    public boolean isSyntaxIssue(String command, String stdout, String stderr, int exitCode) {
        String combined = (stdout + " " + stderr).toLowerCase();
        return combined.contains("正则表达式") ||
               combined.contains("regular expression") ||
               combined.contains("unrecognized escape") ||
               combined.contains("unmatched") ||
               combined.contains("invalid pattern") ||
               combined.contains("argumentexception") ||
               combined.contains("systax error") ||
               combined.contains("parse error") ||
               combined.contains("无法将") ||
               combined.contains("is not recognized");
    }
    
    private List<FailurePattern> initPatterns() {
        List<FailurePattern> list = new ArrayList<>();
        
        // 模式 1：正则表达式转义错误（PowerShell / Java / grep 常见）
        list.add(new FailurePattern(
            "regex_escape_error",
            Pattern.compile("(正则表达式|regular expression|unrecognized escape|argumentexception|invalid pattern|无法将.*识别为)"),
            (cmd, out, err) -> new RecoveryAdvice(
                RecoveryAction.FIX_SYNTAX,
                "检测到正则表达式或转义字符错误",
                List.of(
                    "将反斜杠 '\\' 替换为双反斜杠 '\\\\' 或正斜杠 '/'",
                    "如果不需要正则，移除 -match 或改为 -like 或字符串比较",
                    "在 grep 中尝试使用 -F 进行固定字符串匹配而非正则匹配"
                ),
                95
            )
        ));
        
        // 模式 2：命令未找到（head, grep, find, tree 在 Windows 上不存在）
        list.add(new FailurePattern(
            "command_not_found",
            Pattern.compile("(is not recognized|不是内部或外部命令|无法将.*项识别为|command not found|无法运行|objectnotfoundexception)"),
            (cmd, out, err) -> new RecoveryAdvice(
                RecoveryAction.SWITCH_TOOL,
                "当前平台不支持该命令，建议切换为内置文件 API 或 PowerShell 等价命令",
                suggestAlternative(cmd),
                95
            )
        ));
        
        // 模式 3：权限不足
        list.add(new FailurePattern(
            "permission_denied",
            Pattern.compile("(permission denied|access is denied|拒绝访问|unauthorizedaccess)"),
            (cmd, out, err) -> new RecoveryAdvice(
                RecoveryAction.RETRY_WITH_ELEVATION,
                "权限不足，建议更换到有权限的目录或使用只读命令",
                List.of(
                    "改用只读命令（ls, cat, Get-Content）",
                    "切换到用户有权限的目录",
                    "使用 Files.readAllLines() 等 Java API 替代 Shell 命令"
                ),
                90
            )
        ));
        
        // 模式 4：输出被截断（truncated）——这是信息密度问题，不是命令错误
        list.add(new FailurePattern(
            "output_truncated",
            Pattern.compile("(truncated|截断|output is truncated|too large)"),
            (cmd, out, err) -> new RecoveryAdvice(
                RecoveryAction.NARROW_SCOPE,
                "输出过大被截断，建议缩小搜索范围，直接读取关键文件",
                List.of(
                    "停止递归 listing，改为直接读取 application.yaml / pom.xml 等核心文件",
                    "使用 head / tail / Select-Object -First N 限制输出行数",
                    "增加过滤条件，排除 .git / target / node_modules"
                ),
                98
            )
        ));
        
        // 模式 5：路径不存在
        list.add(new FailurePattern(
            "path_not_found",
            Pattern.compile("(no such file or directory|cannot find the path|系统找不到指定的路径|path does not exist)"),
            (cmd, out, err) -> new RecoveryAdvice(
                RecoveryAction.VERIFY_PATH,
                "路径不存在，建议先确认目录是否存在",
                List.of(
                    "使用 Test-Path / Files.exists() 验证路径",
                    "检查路径拼写（如 jwcode vs jwclaw）",
                    "使用 pwd / Get-Location 确认当前工作目录"
                ),
                95
            )
        ));
        
        // 模式 6：超时
        list.add(new FailurePattern(
            "timeout",
            Pattern.compile("(timed out|timeout|进程超时|超过超时)"),
            (cmd, out, err) -> new RecoveryAdvice(
                RecoveryAction.INCREASE_TIMEOUT,
                "命令执行超时，建议缩短命令复杂度或增加超时时间",
                List.of(
                    "增加 timeout 参数",
                    "缩小搜索范围（减少递归深度或目录）",
                    "使用更高效的命令替代（如 find 代替 raw glob）"
                ),
                90
            )
        ));
        
        return list;
    }
    
    private List<String> suggestAlternative(String originalCommand) {
        String lower = originalCommand.toLowerCase();
        List<String> alternatives = new ArrayList<>();
        
        if (lower.contains("head ") || lower.contains("| head")) {
            alternatives.add("使用 PowerShell: Select-Object -First N");
            alternatives.add("使用 Java API: Files.readAllLines().subList(0, N)");
        }
        if (lower.contains("grep ") || lower.contains("| grep")) {
            alternatives.add("使用 PowerShell: Select-String 或 findstr");
            alternatives.add("使用 Java API: 读取文件后用 String.contains() 过滤");
        }
        if (lower.contains("tree ")) {
            alternatives.add("使用 PowerShell: Get-ChildItem -Recurse | Select-Object FullName");
            alternatives.add("使用 Java API: Files.walk().limit(N)");
        }
        if (lower.contains("ls -la") || lower.contains("dir ")) {
            alternatives.add("使用 PowerShell: Get-ChildItem | Select-Object Name, Mode, Length");
        }
        if (lower.contains("cat ")) {
            alternatives.add("使用 PowerShell: Get-Content");
            alternatives.add("使用 Java API: Files.readString(path)");
        }
        if (lower.contains("find ") || lower.contains("glob")) {
            alternatives.add("使用 Java API: Files.find(root, depth, matcher)");
            alternatives.add("使用 PathMatcher: root.getFileSystem().getPathMatcher(\"glob:**/*.java\")");
        }
        
        if (alternatives.isEmpty()) {
            alternatives.add("改用 Java NIO API（Files.walk / Files.readString）避免平台差异");
            alternatives.add("改用 PowerShell 命令（如果在 Windows 上运行）");
        }
        
        return alternatives;
    }
    
    private record FailurePattern(
        String id,
        Pattern pattern,
        AdviceGenerator generator
    ) {
        boolean matches(String combinedOutput, int exitCode) {
            return pattern.matcher(combinedOutput).find();
        }
        
        RecoveryAdvice generateAdvice(String cmd, String out, String err) {
            return generator.generate(cmd, out, err);
        }
    }
    
    @FunctionalInterface
    private interface AdviceGenerator {
        RecoveryAdvice generate(String cmd, String out, String err);
    }
    
    public enum RecoveryAction {
        FIX_SYNTAX,           // 修复语法/转义
        SWITCH_TOOL,          // 切换工具或平台命令
        RETRY_WITH_ELEVATION, // 提升权限后重试
        NARROW_SCOPE,         // 缩小范围，直接读取核心文件
        VERIFY_PATH,          // 先验证路径存在性
        INCREASE_TIMEOUT,     // 增加超时
        RETRY_WITH_ALTERNATIVE // 用替代命令重试
    }
    
    public record RecoveryAdvice(
        RecoveryAction action,
        String diagnosis,
        List<String> suggestions,
        int confidence
    ) {
        public RecoveryAdvice(RecoveryAction action, String diagnosis, String singleSuggestion, int confidence) {
            this(action, diagnosis, List.of(singleSuggestion), confidence);
        }
    }
}
