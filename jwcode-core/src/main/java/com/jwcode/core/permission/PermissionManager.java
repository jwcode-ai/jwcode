package com.jwcode.core.permission;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限管理器 - 管理文件系统权限和命令执行权限
 * 
 * 功能：
 * - 文件系统权限控制（只读/读写/删除）
 * - 命令执行权限分级（安全命令/危险命令）
 * - 用户确认流程
 */
public class PermissionManager {
    
    private static PermissionManager instance;
    
    // 权限级别
    public enum PermissionLevel {
        READ_ONLY,      // 只读
        READ_WRITE,     // 读写
        DELETE,         // 删除
        DESTRUCTIVE,    // 破坏性操作
        DANGEROUS       // 危险命令
    }
    
    // 用户确认设置
    private boolean autoApproveRead = true;
    private boolean autoApproveWrite = false;
    private boolean autoApproveDelete = false;
    private boolean autoApproveDestructive = false;
    
    // 已批准的操作缓存（临时）
    private final Set<String> approvedOperations = ConcurrentHashMap.newKeySet();
    
    // 只读目录列表
    private final List<String> readOnlyPaths = new ArrayList<>();
    
    // 禁止访问的目录
    private final List<String> forbiddenPaths = Arrays.asList(
        "C:\\Windows\\System32",
        "/etc",
        "/usr/bin",
        "/bin"
    );
    
    // 自动权限分类器
    private final AutoPermissionClassifier autoClassifier = new AutoPermissionClassifier();
    private boolean autoMode = false;

    private PermissionManager() {
        // 添加默认的只读目录
        String home = System.getProperty("user.home");
        if (home != null) {
            readOnlyPaths.add(Paths.get(home, ".jwcode").toString());
        }
    }
    
    public static synchronized PermissionManager getInstance() {
        if (instance == null) {
            instance = new PermissionManager();
        }
        return instance;
    }
    
    /**
     * 检查文件读取权限
     */
    public PermissionCheckResult canRead(String path) {
        // 检查是否在禁止访问列表中
        if (isForbiddenPath(path)) {
            return PermissionCheckResult.denied("禁止访问系统目录: " + path);
        }
        
        return PermissionCheckResult.allowed();
    }
    
    /**
     * 检查文件写入权限
     */
    public PermissionCheckResult canWrite(String path) {
        // 检查是否在禁止访问列表中
        if (isForbiddenPath(path)) {
            return PermissionCheckResult.denied("禁止访问系统目录: " + path);
        }
        
        // 检查是否在只读目录中
        if (isReadOnlyPath(path)) {
            return PermissionCheckResult.denied("该路径为只读: " + path);
        }
        
        // 检查是否需要确认
        if (!autoApproveWrite) {
            String operationKey = "write:" + path;
            if (!approvedOperations.contains(operationKey)) {
                return PermissionCheckResult.needsConfirmation(
                    PermissionLevel.READ_WRITE, 
                    "写入文件: " + path
                );
            }
        }
        
        return PermissionCheckResult.allowed();
    }
    
    /**
     * 检查文件删除权限
     */
    public PermissionCheckResult canDelete(String path) {
        // 检查是否在禁止访问列表中
        if (isForbiddenPath(path)) {
            return PermissionCheckResult.denied("禁止访问系统目录: " + path);
        }
        
        // 检查是否在只读目录中
        if (isReadOnlyPath(path)) {
            return PermissionCheckResult.denied("该路径为只读: " + path);
        }
        
        // 删除操作始终需要确认
        if (!autoApproveDelete) {
            String operationKey = "delete:" + path;
            if (!approvedOperations.contains(operationKey)) {
                return PermissionCheckResult.needsConfirmation(
                    PermissionLevel.DELETE, 
                    "删除文件: " + path
                );
            }
        }
        
        return PermissionCheckResult.allowed();
    }
    
    /**
     * 检查命令执行权限（增强版 — 支持 Auto Mode）
     */
    public PermissionCheckResult canExecuteCommand(String command) {
        // Auto Mode: 使用分类器自动判断
        if (autoMode) {
            AutoPermissionClassifier.ClassificationResult cr =
                autoClassifier.classifyCommand(command, null);
            return switch (cr.decision()) {
                case AUTO_ALLOW -> PermissionCheckResult.allowed();
                case AUTO_DENY -> PermissionCheckResult.denied(cr.reason());
                case ASK_USER -> {
                    // ASK_USER in auto mode still requires confirmation unless explicitly approved
                    if (autoApproveDestructive) {
                        yield PermissionCheckResult.allowed();
                    }
                    String operationKey = "cmd:" + command;
                    if (approvedOperations.contains(operationKey)) {
                        yield PermissionCheckResult.allowed();
                    }
                    yield PermissionCheckResult.needsConfirmation(
                        PermissionLevel.DANGEROUS, cr.reason());
                }
            };
        }

        // 标准模式: 原有逻辑
        if (isDangerousCommand(command)) {
            if (!autoApproveDestructive) {
                String operationKey = "cmd:" + command;
                if (!approvedOperations.contains(operationKey)) {
                    return PermissionCheckResult.needsConfirmation(
                        PermissionLevel.DANGEROUS,
                        "执行危险命令: " + command
                    );
                }
            }
        }

        return PermissionCheckResult.allowed();
    }
    
    /**
     * 批准操作
     */
    public void approveOperation(String operationKey) {
        approvedOperations.add(operationKey);
    }
    
    /**
     * 清除所有临时批准
     */
    public void clearTemporaryApprovals() {
        approvedOperations.clear();
    }
    
    /**
     * 检查是否为禁止访问的路径
     */
    private boolean isForbiddenPath(String path) {
        Path targetPath = Paths.get(path).toAbsolutePath().normalize();
        for (String forbidden : forbiddenPaths) {
            Path forbiddenPath = Paths.get(forbidden).toAbsolutePath().normalize();
            if (targetPath.startsWith(forbiddenPath)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否为只读路径
     */
    private boolean isReadOnlyPath(String path) {
        Path targetPath = Paths.get(path).toAbsolutePath().normalize();
        for (String readOnly : readOnlyPaths) {
            Path readOnlyPath = Paths.get(readOnly).toAbsolutePath().normalize();
            if (targetPath.startsWith(readOnlyPath)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否为危险命令
     */
    private boolean isDangerousCommand(String command) {
        String lower = command.toLowerCase();

        // Literal substring patterns — safe for String.contains()
        String[] literalPatterns = {
            "rm -rf /", "rm -rf /*", "> /dev/sda", "mkfs",
            "dd if=/dev/zero", "chmod -r 777 /", "chown -r",
            "sudo", ":(){ :|:& };:"
        };
        for (String pattern : literalPatterns) {
            if (lower.contains(pattern)) return true;
        }

        // Regex patterns — pipe-to-shell (curl/wget | bash)
        if (lower.matches(".*curl.*\\|.*bash.*")) return true;
        if (lower.matches(".*wget.*\\|.*bash.*")) return true;

        return false;
    }
    
    // Getters and Setters
    public void setAutoApproveRead(boolean autoApproveRead) {
        this.autoApproveRead = autoApproveRead;
    }
    
    public void setAutoApproveWrite(boolean autoApproveWrite) {
        this.autoApproveWrite = autoApproveWrite;
    }
    
    public void setAutoApproveDelete(boolean autoApproveDelete) {
        this.autoApproveDelete = autoApproveDelete;
    }
    
    public void setAutoApproveDestructive(boolean autoApproveDestructive) {
        this.autoApproveDestructive = autoApproveDestructive;
    }
    
    public void addReadOnlyPath(String path) {
        readOnlyPaths.add(path);
    }

    // ==== Auto Mode ====

    /** 启用/禁用自动模式 */
    public void setAutoMode(boolean enabled) {
        this.autoMode = enabled;
    }

    /** 是否在自动模式下 */
    public boolean isAutoMode() {
        return autoMode;
    }

    /**
     * YOLO Mode — 全自动模式，所有操作无需确认。
     * 设置所有 autoApprove 标志为 enabled 状态。
     */
    public void setYoloMode(boolean enabled) {
        this.autoApproveWrite = enabled;
        this.autoApproveDelete = enabled;
        if (enabled) {
            this.autoApproveDestructive = enabled;
            this.autoMode = enabled;
        } else {
            this.autoApproveDestructive = enabled;
            this.autoMode = enabled;
        }
    }

    /** 获取自动分类器（用于记录学习反馈） */
    public AutoPermissionClassifier getAutoClassifier() {
        return autoClassifier;
    }
    
    /**
     * 权限检查结果
     */
    public static class PermissionCheckResult {
        private final boolean allowed;
        private final String reason;
        private final boolean needsConfirmation;
        private final PermissionLevel requiredLevel;
        
        private PermissionCheckResult(boolean allowed, String reason, boolean needsConfirmation, PermissionLevel requiredLevel) {
            this.allowed = allowed;
            this.reason = reason;
            this.needsConfirmation = needsConfirmation;
            this.requiredLevel = requiredLevel;
        }
        
        public static PermissionCheckResult allowed() {
            return new PermissionCheckResult(true, null, false, null);
        }
        
        public static PermissionCheckResult denied(String reason) {
            return new PermissionCheckResult(false, reason, false, null);
        }
        
        public static PermissionCheckResult needsConfirmation(PermissionLevel level, String reason) {
            return new PermissionCheckResult(false, reason, true, level);
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public boolean needsConfirmation() { return needsConfirmation; }
        public PermissionLevel getRequiredLevel() { return requiredLevel; }
    }
}
