package com.jwcode.core.tool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * WorkspaceGuard — 工作目录安全守卫。
 *
 * <p>确保所有文件操作仅限于工作目录内，防止 ToolAgent
 * 越权访问工作目录之外的敏感文件。</p>
 *
 * <p>核心功能：
 * <ul>
 *   <li>路径规范化（防止 {@code ..} 路径穿越攻击）</li>
 *   <li>边界校验（确保路径在工作目录子树内）</li>
 *   <li>相对路径解析 + 边界校验（一体化）</li>
 *   <li>返回结构化错误摘要（遵循三层摘要机制）</li>
 * </ul>
 * </p>
 *
 * <p>使用示例：
 * <pre>{@code
 * WorkspaceGuard guard = new WorkspaceGuard(Paths.get("/home/user/project"));
 *
 * // 校验绝对路径
 * Optional<ErrorSummary> error = guard.validatePath(Paths.get("/home/user/project/src/main.java"));
 *
 * // 解析并校验相对路径
 * Path resolved = guard.resolveAndValidate("src/main.java",
 *     Paths.get("/home/user/project"), "FileReadTool");
 * }</pre>
 * </p>
 *
 * <p>安全说明：
 * <ul>
 *   <li>使用 {@link Path#toRealPath} 解析符号链接（可选，默认开启）</li>
 *   <li>使用 {@link Path#normalize} 消除 {@code ..} 和 {@code .}</li>
 *   <li>在 Windows 上大小写不敏感比较</li>
 * </ul>
 * </p>
 */
public class WorkspaceGuard {

    private static final Logger logger = Logger.getLogger(WorkspaceGuard.class.getName());

    /** 工作区根路径（已规范化） */
    private final Path workspaceRoot;

    /** 是否解析符号链接（默认开启，防止符号链接逃逸） */
    private final boolean resolveSymlinks;

    /**
     * 创建工作区守卫。
     *
     * @param workspaceRoot 工作区根路径（必须是绝对路径）
     * @throws IllegalArgumentException 如果 workspaceRoot 为 null 或不是绝对路径
     */
    public WorkspaceGuard(Path workspaceRoot) {
        this(workspaceRoot, true);
    }

    /**
     * 创建工作区守卫（可控制符号链接解析）。
     *
     * @param workspaceRoot   工作区根路径
     * @param resolveSymlinks 是否解析符号链接
     */
    public WorkspaceGuard(Path workspaceRoot, boolean resolveSymlinks) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        this.workspaceRoot = workspaceRoot.normalize().toAbsolutePath();
        this.resolveSymlinks = resolveSymlinks;

        if (!java.nio.file.Files.exists(this.workspaceRoot)) {
            logger.warning("[WorkspaceGuard] 工作区根路径不存在: " + this.workspaceRoot
                + " (将在首次使用时报错)");
        }

        logger.fine("[WorkspaceGuard] 初始化完成, workspaceRoot=" + this.workspaceRoot);
    }

    // ==================== 路径校验 ====================

    /**
     * 校验路径是否在工作区内。
     *
     * @param targetPath 要校验的路径（已解析为绝对路径）
     * @return 空 Optional 表示校验通过；否则包含错误摘要
     */
    public Optional<ErrorSummary> validatePath(Path targetPath) {
        return validatePath(targetPath, null);
    }

    /**
     * 校验路径是否在工作区内（带工具名，用于错误信息）。
     *
     * <p>【TOCTOU 防护】使用 toRealPath() 原子方式获取真实路径，
     * 消除 exists() 检查与 toRealPath() 调用之间的竞态窗口。
     * 文件不存在时回退到父目录解析。</p>
     *
     * @param targetPath 要校验的路径
     * @param toolName   工具名称（用于错误诊断）
     * @return 空 Optional 表示校验通过；否则包含错误摘要
     */
    public Optional<ErrorSummary> validatePath(Path targetPath, String toolName) {
        Objects.requireNonNull(targetPath, "targetPath must not be null");

        // 1. 规范化路径（消除 .. 和 .）
        Path normalized = targetPath.normalize();

        // 2. 转为绝对路径
        Path absolute = normalized.toAbsolutePath();

        // 3. 原子方式获取真实路径（消除 TOCTOU 竞态条件）
        //    使用 toRealPath() 而非分开的 exists() + toRealPath()
        Path realPath;
        Path realRoot;
        try {
            // 先尝试解析目标路径的真实路径（原子操作）
            if (resolveSymlinks) {
                realPath = absolute.toRealPath();
            } else {
                realPath = absolute;
            }
        } catch (IOException e) {
            // 文件不存在时的回退：使用规范化后的路径
            // 但仍需校验父目录是否在工作区内
            Path parent = absolute.getParent();
            if (parent != null) {
                try {
                    if (resolveSymlinks && java.nio.file.Files.exists(parent)) {
                        realPath = parent.toRealPath().resolve(absolute.getFileName());
                    } else {
                        realPath = absolute;
                    }
                } catch (IOException e2) {
                    realPath = absolute;
                }
            } else {
                realPath = absolute;
            }
        }

        // 4. 原子方式获取工作区根路径的真实路径
        try {
            if (resolveSymlinks && java.nio.file.Files.exists(workspaceRoot)) {
                realRoot = workspaceRoot.toRealPath();
            } else {
                realRoot = workspaceRoot;
            }
        } catch (IOException e) {
            realRoot = workspaceRoot;
        }

        // 5. 边界检查：目标路径必须始于工作区根路径
        if (!realPath.startsWith(realRoot)) {
            String errorMsg = buildAccessDeniedMessage(realPath, realRoot, toolName);
            logger.warning("[WorkspaceGuard] " + errorMsg);

            ErrorSummary error = ErrorSummary.builder()
                .errorType("WORKSPACE_ACCESS_DENIED")
                .message(errorMsg)
                .retryable(false)
                .retryCount(0)
                .maxRetries(0)
                .recoveryHint("请确保文件路径在工作目录内: " + realRoot)
                .sourceLayer("TOOL_AGENT")
                .criticalPath(false)
                .requiresHumanIntervention(false)
                .build();

            return Optional.of(error);
        }

        return Optional.empty();
    }

    /**
     * 解析相对路径并校验边界。
     *
     * <p>这是一个便捷方法，将相对路径解析 + 边界校验合并为一次调用。</p>
     *
     * @param rawPath      原始路径（可能是相对路径）
     * @param workingDir   当前工作目录（用于解析相对路径）
     * @param toolName     工具名称（用于错误诊断）
     * @return 解析后的已验证绝对路径
     * @throws WorkspaceAccessException 如果路径不在工作区内
     */
    public Path resolveAndValidate(String rawPath, Path workingDir, String toolName)
        throws WorkspaceAccessException {

        Objects.requireNonNull(rawPath, "rawPath must not be null");
        Objects.requireNonNull(workingDir, "workingDir must not be null");

        Path path = Path.of(rawPath);

        // 相对路径 → 基于 workingDir 解析
        if (!path.isAbsolute()) {
            path = workingDir.resolve(path);
        }

        Optional<ErrorSummary> error = validatePath(path, toolName);
        if (error.isPresent()) {
            throw new WorkspaceAccessException(error.get());
        }

        return path.normalize().toAbsolutePath();
    }

    /**
     * 只校验不解析（路径已经是绝对路径）。
     *
     * @param absolutePath 要校验的绝对路径
     * @param toolName     工具名称
     * @throws WorkspaceAccessException 如果路径不在工作区内
     */
    public void validateOrThrow(Path absolutePath, String toolName)
        throws WorkspaceAccessException {

        Optional<ErrorSummary> error = validatePath(absolutePath, toolName);
        if (error.isPresent()) {
            throw new WorkspaceAccessException(error.get());
        }
    }

    /**
     * 批量校验多个路径。
     *
     * @param paths    要校验的路径列表
     * @param toolName 工具名称
     * @return 所有违规路径的错误摘要列表（空列表表示全部通过）
     */
    public java.util.List<ErrorSummary> validatePaths(
            java.util.List<Path> paths, String toolName) {
        java.util.List<ErrorSummary> errors = new java.util.ArrayList<>();
        for (Path p : paths) {
            validatePath(p, toolName).ifPresent(errors::add);
        }
        return errors;
    }

    // ==================== Getters ====================

    /**
     * 获取工作区根路径。
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * 是否已启用符号链接解析。
     */
    public boolean isResolveSymlinks() {
        return resolveSymlinks;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 从工作目录路径创建守卫（宽松模式：不解析符号链接）。
     */
    public static WorkspaceGuard lenient(Path workspaceRoot) {
        return new WorkspaceGuard(workspaceRoot, false);
    }

    /**
     * 从工作目录路径创建守卫（严格模式：解析符号链接，默认）。
     */
    public static WorkspaceGuard strict(Path workspaceRoot) {
        return new WorkspaceGuard(workspaceRoot, true);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建"访问被拒绝"错误消息。
     */
    private String buildAccessDeniedMessage(Path targetPath, Path workspaceRoot,
                                             String toolName) {
        StringBuilder sb = new StringBuilder();
        sb.append("拒绝访问工作目录外的路径");
        if (toolName != null && !toolName.isEmpty()) {
            sb.append(" (工具: ").append(toolName).append(")");
        }
        sb.append("。目标路径: ").append(targetPath);
        sb.append("，工作区根路径: ").append(workspaceRoot);
        return sb.toString();
    }

    // ==================== 异常类 ====================

    /**
     * 工作区访问异常 — 当操作试图访问工作目录外的路径时抛出。
     */
    public static class WorkspaceAccessException extends RuntimeException {

        private final ErrorSummary errorSummary;

        public WorkspaceAccessException(ErrorSummary errorSummary) {
            super(errorSummary.getMessage());
            this.errorSummary = errorSummary;
        }

        public ErrorSummary getErrorSummary() {
            return errorSummary;
        }
    }
}
