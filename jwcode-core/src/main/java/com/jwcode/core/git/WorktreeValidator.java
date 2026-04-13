package com.jwcode.core.git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * WorktreeValidator - Worktree 验证器
 * 
 * 功能说明：
 * 提供 Worktree 相关验证功能，包括名称合法性验证、
 * Worktree 存在性检查、分支存在性检查等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class WorktreeValidator {
    
    /** 无效的分支/路径字符 */
    private static final Pattern INVALID_CHARS = Pattern.compile("[<>:\"|?*\\x00-\\x1f]");
    
    /** 保留名称（Windows） */
    private static final List<String> RESERVED_NAMES = List.of(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );
    
    /** Worktree 路径最大长度 */
    private static final int MAX_PATH_LENGTH = 255;
    
    /** Worktree 名称最大长度 */
    private static final int MAX_NAME_LENGTH = 100;
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * 验证 Worktree 路径是否合法
     * 
     * @param path Worktree 路径
     * @return 验证结果
     */
    public static ValidationResult validateWorktreePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return ValidationResult.invalid("Worktree path cannot be empty");
        }
        
        String trimmedPath = path.trim();
        
        // 检查路径长度
        if (trimmedPath.length() > MAX_PATH_LENGTH) {
            return ValidationResult.invalid("Worktree path is too long (max " + MAX_PATH_LENGTH + " characters)");
        }
        
        // 检查无效字符
        if (INVALID_CHARS.matcher(trimmedPath).find()) {
            return ValidationResult.invalid("Worktree path contains invalid characters");
        }
        
        // 检查相对路径中的非法模式
        if (trimmedPath.contains("..") || trimmedPath.contains("./") || trimmedPath.contains(".\\")) {
            return ValidationResult.invalid("Worktree path cannot contain relative path components like '..'");
        }
        
        // 解析路径
        Path worktreePath;
        try {
            worktreePath = Paths.get(trimmedPath);
        } catch (Exception e) {
            return ValidationResult.invalid("Invalid path format: " + e.getMessage());
        }
        
        // 检查路径组件
        for (int i = 0; i < worktreePath.getNameCount(); i++) {
            String component = worktreePath.getName(i).toString();
            
            // 检查 Windows 保留名称
            String upperComponent = component.toUpperCase();
            if (RESERVED_NAMES.contains(upperComponent)) {
                return ValidationResult.invalid("Path component '" + component + "' is a reserved name");
            }
            
            // 检查是否以点或空格开头/结尾
            if (component.startsWith(" ") || component.endsWith(" ")) {
                return ValidationResult.invalid("Path component cannot start or end with space");
            }
            if (component.startsWith(".") && component.length() > 1) {
                // 允许 . 和 .. 以及隐藏目录，但提醒用户
                if (component.equals(".git")) {
                    return ValidationResult.invalid("Path cannot contain '.git' component");
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 验证分支名称是否合法
     * 
     * @param branchName 分支名称
     * @return 验证结果
     */
    public static ValidationResult validateBranchName(String branchName) {
        if (branchName == null || branchName.trim().isEmpty()) {
            return ValidationResult.invalid("Branch name cannot be empty");
        }
        
        String trimmedName = branchName.trim();
        
        // 检查长度
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            return ValidationResult.invalid("Branch name is too long (max " + MAX_NAME_LENGTH + " characters)");
        }
        
        // Git 分支名称规则
        // 不能以 . 开头
        if (trimmedName.startsWith(".")) {
            return ValidationResult.invalid("Branch name cannot start with '.'");
        }
        
        // 不能以 - 开头
        if (trimmedName.startsWith("-")) {
            return ValidationResult.invalid("Branch name cannot start with '-'");
        }
        
        // 不能以 / 结尾
        if (trimmedName.endsWith("/")) {
            return ValidationResult.invalid("Branch name cannot end with '/'");
        }
        
        // 不能包含 //
        if (trimmedName.contains("//")) {
            return ValidationResult.invalid("Branch name cannot contain '//'");
        }
        
        // 检查无效字符
        if (trimmedName.contains("..") || trimmedName.contains("@{") || trimmedName.contains("\\")) {
            return ValidationResult.invalid("Branch name contains invalid characters (.., @{, or backslash)");
        }
        
        // 检查控制字符
        for (char c : trimmedName.toCharArray()) {
            if (c < 32 || c == 127) {
                return ValidationResult.invalid("Branch name contains control characters");
            }
        }
        
        // 检查是否是 "HEAD"（保留名称）
        if (trimmedName.equalsIgnoreCase("HEAD")) {
            return ValidationResult.invalid("'HEAD' is a reserved name and cannot be used as branch name");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 检查 Worktree 是否存在
     * 
     * @param worktreeManager Worktree 管理器
     * @param path Worktree 路径
     * @return true 如果 Worktree 存在
     */
    public static boolean worktreeExists(WorktreeManager worktreeManager, String path) {
        try {
            List<WorktreeInfo> worktrees = worktreeManager.listWorktreesSync();
            Path checkPath = Paths.get(path).toAbsolutePath().normalize();
            return worktrees.stream()
                    .anyMatch(wt -> wt.getPath().toAbsolutePath().normalize().equals(checkPath));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查 Worktree 是否存在（异步）
     * 
     * @param worktreeManager Worktree 管理器
     * @param path Worktree 路径
     * @return 包含结果的 CompletableFuture
     */
    public static java.util.concurrent.CompletableFuture<Boolean> worktreeExistsAsync(
            WorktreeManager worktreeManager, String path) {
        return worktreeManager.listWorktrees().thenApply(worktrees -> {
            Path checkPath = Paths.get(path).toAbsolutePath().normalize();
            return worktrees.stream()
                    .anyMatch(wt -> wt.getPath().toAbsolutePath().normalize().equals(checkPath));
        });
    }
    
    /**
     * 检查路径是否可以创建 Worktree
     * 
     * @param path 路径
     * @return 验证结果
     */
    public static ValidationResult canCreateWorktreeAt(String path) {
        // 首先验证路径格式
        ValidationResult pathValidation = validateWorktreePath(path);
        if (!pathValidation.isValid()) {
            return pathValidation;
        }
        
        Path worktreePath = Paths.get(path);
        
        // 检查路径是否已存在
        if (Files.exists(worktreePath)) {
            return ValidationResult.invalid("Path already exists: " + path);
        }
        
        // 检查父目录是否存在且可写
        Path parent = worktreePath.getParent();
        if (parent != null) {
            if (!Files.exists(parent)) {
                // 父目录不存在，检查是否可以创建
                Path grandParent = parent.getParent();
                if (grandParent != null && Files.exists(grandParent)) {
                    if (!Files.isWritable(grandParent)) {
                        return ValidationResult.invalid("Cannot create parent directory: permission denied");
                    }
                }
            } else if (!Files.isDirectory(parent)) {
                return ValidationResult.invalid("Parent path is not a directory: " + parent);
            } else if (!Files.isWritable(parent)) {
                return ValidationResult.invalid("Parent directory is not writable: " + parent);
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 验证完整的 Worktree 创建参数
     * 
     * @param branch 分支名称
     * @param path Worktree 路径
     * @param worktreeManager Worktree 管理器（用于检查重复）
     * @return 验证结果
     */
    public static ValidationResult validateCreateWorktree(String branch, String path, 
                                                          WorktreeManager worktreeManager) {
        // 验证分支名称
        ValidationResult branchValidation = validateBranchName(branch);
        if (!branchValidation.isValid()) {
            return branchValidation;
        }
        
        // 验证路径并检查是否可以创建
        ValidationResult pathValidation = canCreateWorktreeAt(path);
        if (!pathValidation.isValid()) {
            return pathValidation;
        }
        
        // 检查 Worktree 是否已存在
        if (worktreeExists(worktreeManager, path)) {
            return ValidationResult.invalid("Worktree already exists at path: " + path);
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 验证是否可以进入 Worktree
     * 
     * @param path Worktree 路径
     * @param worktreeManager Worktree 管理器
     * @return 验证结果
     */
    public static ValidationResult validateEnterWorktree(String path, WorktreeManager worktreeManager) {
        if (path == null || path.trim().isEmpty()) {
            return ValidationResult.invalid("Worktree path cannot be empty");
        }
        
        // 检查 Worktree 是否存在
        if (!worktreeExists(worktreeManager, path)) {
            return ValidationResult.invalid("Worktree does not exist at path: " + path);
        }
        
        Path worktreePath = Paths.get(path);
        
        // 检查是否为有效目录
        if (!Files.exists(worktreePath)) {
            return ValidationResult.invalid("Worktree path does not exist on filesystem: " + path);
        }
        
        if (!Files.isDirectory(worktreePath)) {
            return ValidationResult.invalid("Worktree path is not a directory: " + path);
        }
        
        // 检查是否有读取权限
        if (!Files.isReadable(worktreePath)) {
            return ValidationResult.invalid("Cannot read worktree directory: " + path);
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 验证是否可以删除 Worktree
     * 
     * @param path Worktree 路径
     * @param worktreeManager Worktree 管理器
     * @param currentWorktreePath 当前 Worktree 路径
     * @return 验证结果
     */
    public static ValidationResult validateRemoveWorktree(String path, 
                                                           WorktreeManager worktreeManager,
                                                           Path currentWorktreePath) {
        if (path == null || path.trim().isEmpty()) {
            return ValidationResult.invalid("Worktree path cannot be empty");
        }
        
        Path worktreePath = Paths.get(path).toAbsolutePath().normalize();
        Path currentPath = currentWorktreePath.toAbsolutePath().normalize();
        
        // 不能删除当前所在的 worktree
        if (worktreePath.equals(currentPath)) {
            return ValidationResult.invalid("Cannot remove the current worktree. Please exit this worktree first.");
        }
        
        // 检查 Worktree 是否存在
        if (!worktreeExists(worktreeManager, path)) {
            return ValidationResult.invalid("Worktree does not exist at path: " + path);
        }
        
        return ValidationResult.valid();
    }
}
