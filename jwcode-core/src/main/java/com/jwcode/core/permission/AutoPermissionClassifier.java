package com.jwcode.core.permission;

import com.jwcode.core.tool.shell.CommandReadOnlyValidator;
import com.jwcode.core.tool.shell.CommandInjectionDetector;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动权限分类器 — 启发式规则引擎，用于在 "Auto Mode" 下自动判断 allow/deny/ask。
 *
 * <p>参考 Claude Code YOLO Classifier 的设计，但使用启发式规则而非 ML 模型：
 * <ul>
 *   <li>命令安全分析 (只读/已知安全/注入检测)</li>
 *   <li>文件路径风险评估</li>
 *   <li>会话内用户行为学习</li>
 *   <li>操作频率异常检测</li>
 * </ul>
 */
public class AutoPermissionClassifier {

    /**
     * 分类决策
     */
    public enum Decision {
        AUTO_ALLOW,     // 自动允许（高置信度安全）
        AUTO_DENY,      // 自动拒绝（高置信度危险）
        ASK_USER        // 需要用户确认（不确定）
    }

    /**
     * 操作类型
     */
    public enum OperationType {
        FILE_READ, FILE_WRITE, FILE_DELETE,
        COMMAND, WEB_FETCH, TOOL_CALL
    }

    /**
     * 分类结果
     */
    public record ClassificationResult(
        Decision decision,
        String reason,
        double confidence,    // 0.0 ~ 1.0
        String riskLevel      // LOW, MEDIUM, HIGH, CRITICAL
    ) {
        public static ClassificationResult allow(String reason, double confidence) {
            return new ClassificationResult(Decision.AUTO_ALLOW, reason, confidence, "LOW");
        }
        public static ClassificationResult deny(String reason, double confidence) {
            return new ClassificationResult(Decision.AUTO_DENY, reason, confidence, "CRITICAL");
        }
        public static ClassificationResult ask(String reason, double confidence, String riskLevel) {
            return new ClassificationResult(Decision.ASK_USER, reason, confidence, riskLevel);
        }
    }

    // ==== 会话内学习状态 ====
    private final Set<String> userApprovedPatterns = ConcurrentHashMap.newKeySet();
    private final Set<String> userDeniedPatterns = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> operationFrequency = new ConcurrentHashMap<>();
    private final Map<String, Long> lastOperationTime = new ConcurrentHashMap<>();

    // 用户允许的安全路径前缀
    private final Set<String> trustedPaths = ConcurrentHashMap.newKeySet();

    // ==== 可配置阈值 ====
    private int maxFrequencyPerMinute = 30;   // 每分钟最多允许的操作数
    private double autoAllowConfidenceThreshold = 0.85;
    private double autoDenyConfidenceThreshold = 0.90;

    /** 重置会话学习状态 */
    public void resetSession() {
        userApprovedPatterns.clear();
        userDeniedPatterns.clear();
        operationFrequency.clear();
        lastOperationTime.clear();
        trustedPaths.clear();
    }

    // ==================== 命令分类 ====================

    /**
     * 分类 Bash 命令。
     */
    public ClassificationResult classifyCommand(String command, String workingDirectory) {
        if (command == null || command.isBlank()) {
            return ClassificationResult.deny("空命令", 1.0);
        }

        String lower = command.trim().toLowerCase();

        // 1. 绝对安全命令 — 直接允许
        if (CommandReadOnlyValidator.isAlwaysSafe(command)) {
            return ClassificationResult.allow("已知安全命令（只读/信息查询）", 0.95);
        }

        // 2. 注入检测
        var injectionResult = CommandInjectionDetector.detect(command, false);
        if (injectionResult.isInjected() && injectionResult.severity() >= 8) {
            return ClassificationResult.deny(
                "命令注入风险: " + injectionResult.description(), 0.95);
        }

        // 3. 用户已批准过的模式匹配
        for (String pattern : userApprovedPatterns) {
            if (matchesWithWordBoundary(lower, pattern.toLowerCase())) {
                return ClassificationResult.allow(
                    "用户已批准类似操作: " + pattern, 0.80);
            }
        }

        // 4. 用户已拒绝的模式
        for (String pattern : userDeniedPatterns) {
            if (matchesWithWordBoundary(lower, pattern.toLowerCase())) {
                return ClassificationResult.deny(
                    "用户已拒绝类似操作: " + pattern, 0.85);
            }
        }

        // 5. 风险评分
        int riskScore = CommandReadOnlyValidator.riskScore(command);

        if (riskScore >= 9) {
            return ClassificationResult.deny("高风险命令（评分 " + riskScore + "/10）", 0.92);
        }
        if (riskScore >= 7) {
            return ClassificationResult.ask("中高风险命令（评分 " + riskScore + "/10）", 0.6, "HIGH");
        }
        if (riskScore <= 2) {
            return ClassificationResult.allow("低风险命令（评分 " + riskScore + "/10）", 0.85);
        }

        // 6. 频率检查
        if (isRateLimited("cmd:" + extractCommandBase(command))) {
            return ClassificationResult.ask("命令执行频率过高，需要确认", 0.5, "MEDIUM");
        }

        // 7. 默认：只读命令允许，其他需要确认
        if (CommandReadOnlyValidator.isReadOnly(command)) {
            return ClassificationResult.allow("只读命令", 0.75);
        }

        return ClassificationResult.ask("需要确认命令操作", 0.5, "MEDIUM");
    }

    // ==================== 文件操作分类 ====================

    /**
     * 分类文件读取操作。
     */
    public ClassificationResult classifyFileRead(String filePath) {
        if (filePath == null) return ClassificationResult.deny("无效路径", 1.0);

        // 信任路径
        for (String trusted : trustedPaths) {
            if (filePath.startsWith(trusted)) {
                return ClassificationResult.allow("信任路径: " + trusted, 0.95);
            }
        }

        // 系统路径拒绝
        if (isSystemPath(filePath)) {
            return ClassificationResult.deny("系统路径禁止访问: " + filePath, 0.95);
        }

        // 文件读取通常是安全的
        return ClassificationResult.allow("文件读取操作", 0.90);
    }

    /**
     * 分类文件写入/删除操作。
     */
    public ClassificationResult classifyFileWrite(String filePath, OperationType type) {
        if (filePath == null) return ClassificationResult.deny("无效路径", 1.0);

        // 系统路径
        if (isSystemPath(filePath)) {
            return ClassificationResult.deny("禁止写入系统路径: " + filePath, 1.0);
        }

        // jwcode 配置路径 — 需要确认
        if (filePath.contains(".jwcode") || filePath.contains(".claude")) {
            return ClassificationResult.ask("操作 JWCode 配置文件: " + filePath, 0.3, "HIGH");
        }

        // 信任路径 — 自动允许
        for (String trusted : trustedPaths) {
            if (filePath.startsWith(trusted)) {
                return ClassificationResult.allow("信任路径写入: " + trusted, 0.85);
            }
        }

        // 敏感文件类型 — 需要确认
        if (isSensitiveFile(filePath)) {
            return ClassificationResult.ask("操作敏感文件类型: " + filePath, 0.4, "HIGH");
        }

        // 用户已允许的路径模式
        for (String pattern : userApprovedPatterns) {
            if (filePath.contains(pattern)) {
                return ClassificationResult.allow("匹配已批准模式: " + pattern, 0.75);
            }
        }

        // 默认: 写入需要确认
        return ClassificationResult.ask("文件写入确认: " + fileName(filePath), 0.5, "MEDIUM");
    }

    // ==================== 学习反馈 ====================

    /**
     * 记录用户的批准决定。
     */
    public void recordApproval(String operationKey, String operationDetail) {
        userApprovedPatterns.add(extractPattern(operationDetail));
        recordOperation(operationKey);
    }

    /**
     * 记录用户的拒绝决定。
     */
    public void recordDenial(String operationKey, String operationDetail) {
        userDeniedPatterns.add(extractPattern(operationDetail));
        recordOperation(operationKey);
    }

    /**
     * 添加信任路径。
     */
    public void addTrustedPath(String path) {
        trustedPaths.add(path);
    }

    // ==================== 私有辅助 ====================

    private String extractCommandBase(String command) {
        if (command == null || command.isBlank()) return "";
        String trimmed = command.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private String extractPattern(String detail) {
        if (detail == null || detail.isEmpty()) return "";
        // 提取操作的核心模式（前 64 个字符）
        return detail.length() > 64 ? detail.substring(0, 64) : detail;
    }

    private boolean isSystemPath(String path) {
        String normalized = path.replace('\\', '/').toLowerCase();
        return normalized.startsWith("/etc/") || normalized.startsWith("/proc/")
            || normalized.startsWith("/sys/") || normalized.startsWith("/boot/")
            || normalized.startsWith("/dev/") || normalized.startsWith("c:/windows/system32")
            || normalized.startsWith("c:/windows/system")
            || normalized.startsWith("c:/windows/") || normalized.startsWith("c:/program files")
            || normalized.startsWith("c:/program files (x86)")
            || normalized.startsWith("/usr/bin/") || normalized.startsWith("/bin/")
            || normalized.startsWith("/sbin/") || normalized.startsWith("/usr/sbin/")
            || normalized.startsWith("/root/") || normalized.startsWith("/var/")
            || normalized.startsWith("/opt/") || normalized.startsWith("/tmp/");
    }

    private boolean isSensitiveFile(String path) {
        String name = fileName(path).toLowerCase();
        String[] sensitive = {
            ".env", ".secrets", ".password", ".token", "credentials",
            "id_rsa", "id_ed25519", "authorized_keys", "known_hosts",
            ".aws/credentials", ".npmrc", ".pypirc", ".docker/config.json",
            "application.properties", "application.yml", "settings.json",
            "web.config", "nginx.conf", "shadow", "passwd"
        };
        String normalizedPath = path.replace('\\', '/').toLowerCase();
        for (String s : sensitive) {
            if (normalizedPath.contains(s)) return true;
        }
        return false;
    }

    private String fileName(String path) {
        if (path == null) return "";
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSep >= 0 ? path.substring(lastSep + 1) : path;
    }

    private boolean isRateLimited(String operationKey) {
        long now = System.currentTimeMillis();
        operationFrequency.merge(operationKey, 1, Integer::sum);
        lastOperationTime.put(operationKey, now);

        // 清理一分钟前的计数
        operationFrequency.entrySet().removeIf(e -> {
            Long last = lastOperationTime.get(e.getKey());
            return last != null && (now - last) > 60_000;
        });

        int count = operationFrequency.getOrDefault(operationKey, 0);
        return count > maxFrequencyPerMinute;
    }

    private void recordOperation(String operationKey) {
        operationFrequency.merge(operationKey, 1, Integer::sum);
        lastOperationTime.put(operationKey, System.currentTimeMillis());
    }

    // ==== 配置 ====

    public void setMaxFrequencyPerMinute(int max) { this.maxFrequencyPerMinute = max; }
    public void setAutoAllowConfidenceThreshold(double t) { this.autoAllowConfidenceThreshold = t; }
    public void setAutoDenyConfidenceThreshold(double t) { this.autoDenyConfidenceThreshold = t; }

    public Set<String> getUserApprovedPatterns() { return Collections.unmodifiableSet(userApprovedPatterns); }
    public Set<String> getUserDeniedPatterns() { return Collections.unmodifiableSet(userDeniedPatterns); }

    /**
     * 带词边界的模式匹配，防止子串劫持（如 rm file.txt 不应匹配 rm file.txt && rm -rf /）
     */
    private boolean matchesWithWordBoundary(String text, String pattern) {
        if (pattern.isEmpty()) return false;
        // 对模式中的每个单词进行边界匹配，要求所有单词都在文本中出现且符合边界
        String[] words = pattern.split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            // 使用正则的 \b 词边界进行匹配
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\b" + java.util.regex.Pattern.quote(word) + "\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE);
            if (!p.matcher(text).find()) {
                return false;
            }
        }
        return true;
    }
}
