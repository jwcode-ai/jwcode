package com.jwcode.core.tool;

import com.jwcode.core.a2a.model.ErrorSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkspaceGuard 单元测试。
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>正常路径通过校验</li>
 *   <li>路径穿越攻击（..）被拦截</li>
 *   <li>绝对路径越界被拦截</li>
 *   <li>符号链接逃逸被拦截</li>
 *   <li>相对路径解析 + 校验</li>
 *   <li>批量校验</li>
 *   <li>边界情况（null、不存在的工作区等）</li>
 * </ul>
 * </p>
 */
class WorkspaceGuardTest {

    @TempDir
    Path tempDir;

    private Path workspaceRoot;
    private WorkspaceGuard guard;

    @BeforeEach
    void setUp() throws IOException {
        workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot);
        // 创建一些子目录
        Files.createDirectories(workspaceRoot.resolve("src/main/java"));
        Files.createDirectories(workspaceRoot.resolve("target"));
        Files.createFile(workspaceRoot.resolve("pom.xml"));
        Files.createFile(workspaceRoot.resolve("src/main/java/Main.java"));

        guard = new WorkspaceGuard(workspaceRoot);
    }

    // ==================== 正常路径通过 ====================

    @Test
    void testValidPath_InsideWorkspace() {
        Path validPath = workspaceRoot.resolve("pom.xml");
        Optional<ErrorSummary> error = guard.validatePath(validPath);
        assertTrue(error.isEmpty(), "工作区内的路径应通过校验");
    }

    @Test
    void testValidPath_Subdirectory() {
        Path validPath = workspaceRoot.resolve("src/main/java/Main.java");
        Optional<ErrorSummary> error = guard.validatePath(validPath);
        assertTrue(error.isEmpty(), "子目录中的路径应通过校验");
    }

    @Test
    void testValidPath_WorkspaceRootItself() {
        Optional<ErrorSummary> error = guard.validatePath(workspaceRoot);
        assertTrue(error.isEmpty(), "工作区根路径本身应通过校验");
    }

    // ==================== 路径穿越攻击（..） ====================

    @Test
    void testPathTraversal_Blocked() {
        Path traversalPath = workspaceRoot.resolve("../outside.txt").normalize();
        Optional<ErrorSummary> error = guard.validatePath(traversalPath);
        assertTrue(error.isPresent(), "路径穿越攻击应被拦截");
        assertEquals("WORKSPACE_ACCESS_DENIED", error.get().getErrorType());
        assertFalse(error.get().isRetryable(), "工作区越界不应可重试");
    }

    @Test
    void testPathTraversal_DeepNested() {
        Path traversalPath = workspaceRoot
            .resolve("src/main/java/../../../../../etc/passwd")
            .normalize();
        Optional<ErrorSummary> error = guard.validatePath(traversalPath);
        assertTrue(error.isPresent(), "深层嵌套的路径穿越应被拦截");
    }

    @Test
    void testPathTraversal_WithNormalize() {
        // normalize 后 .. 被解析
        Path traversalPath = workspaceRoot.resolve("../../etc/hosts");
        Optional<ErrorSummary> error = guard.validatePath(traversalPath);
        assertTrue(error.isPresent(), "normalize 后的穿越路径应被拦截");
    }

    // ==================== 绝对路径越界 ====================

    @Test
    void testAbsolutePath_OutsideWorkspace() {
        Path outsidePath = Path.of("/etc/passwd");
        if (Files.exists(outsidePath) || System.getProperty("os.name").toLowerCase().contains("win")) {
            // 在 Windows 上使用不同路径
            outsidePath = Path.of("C:\\Windows\\System32\\drivers\\etc\\hosts");
        }
        Optional<ErrorSummary> error = guard.validatePath(outsidePath);
        assertTrue(error.isPresent(), "工作区外的绝对路径应被拦截");
    }

    @Test
    void testAbsolutePath_SystemRoot() {
        Path systemPath = Path.of(System.getProperty("user.home")).resolve(".ssh/id_rsa");
        Optional<ErrorSummary> error = guard.validatePath(systemPath);
        assertTrue(error.isPresent(), "用户目录下的私钥文件应被拦截");
    }

    // ==================== 相对路径解析 ====================

    @Test
    void testResolveAndValidate_ValidRelative() {
        Path resolved = guard.resolveAndValidate("src/main/java/Main.java",
            workspaceRoot, "FileReadTool");
        assertNotNull(resolved);
        assertTrue(resolved.isAbsolute());
        assertTrue(resolved.startsWith(workspaceRoot));
    }

    @Test
    void testResolveAndValidate_RelativeTraversal() {
        assertThrows(WorkspaceGuard.WorkspaceAccessException.class, () -> {
            guard.resolveAndValidate("../outside.txt", workspaceRoot, "FileReadTool");
        }, "相对路径穿越应抛出异常");
    }

    @Test
    void testResolveAndValidate_AbsoluteInsideWorkspace() {
        String absolutePathStr = workspaceRoot.resolve("pom.xml").toAbsolutePath().toString();
        Path resolved = guard.resolveAndValidate(absolutePathStr, workspaceRoot, "FileReadTool");
        assertNotNull(resolved);
    }

    // ==================== 批量校验 ====================

    @Test
    void testValidatePaths_AllValid() {
        List<Path> paths = List.of(
            workspaceRoot.resolve("pom.xml"),
            workspaceRoot.resolve("src/main/java/Main.java")
        );
        List<ErrorSummary> errors = guard.validatePaths(paths, "BatchReadTool");
        assertTrue(errors.isEmpty(), "所有路径在工作区内时应返回空错误列表");
    }

    @Test
    void testValidatePaths_Mixed() {
        List<Path> paths = List.of(
            workspaceRoot.resolve("pom.xml"),
            workspaceRoot.resolve("../outside.txt"),
            workspaceRoot.resolve("src/main/java/Main.java")
        );
        List<ErrorSummary> errors = guard.validatePaths(paths, "BatchReadTool");
        assertEquals(1, errors.size(), "应只有1个越界路径");
    }

    // ==================== validateOrThrow ====================

    @Test
    void testValidateOrThrow_Valid() {
        assertDoesNotThrow(() -> {
            guard.validateOrThrow(workspaceRoot.resolve("pom.xml"), "TestTool");
        });
    }

    @Test
    void testValidateOrThrow_Invalid() {
        Path outsidePath = workspaceRoot.resolve("../../../etc/passwd").normalize();
        assertThrows(WorkspaceGuard.WorkspaceAccessException.class, () -> {
            guard.validateOrThrow(outsidePath, "TestTool");
        });
    }

    // ==================== 错误信息质量 ====================

    @Test
    void testErrorMessage_ContainsToolName() {
        Path outsidePath = workspaceRoot.resolve("../secret.txt").normalize();
        Optional<ErrorSummary> error = guard.validatePath(outsidePath, "FileWriteTool");
        assertTrue(error.isPresent());
        assertTrue(error.get().getMessage().contains("FileWriteTool"),
            "错误信息应包含工具名称");
    }

    @Test
    void testErrorMessage_ContainsPathInfo() {
        Path outsidePath = Path.of("/tmp/hack.sh");
        Optional<ErrorSummary> error = guard.validatePath(outsidePath, "BashTool");
        assertTrue(error.isPresent());
        String msg = error.get().getMessage();
        assertTrue(msg.contains("工作目录外") || msg.contains("拒绝访问"),
            "错误信息应说明拒绝原因");
    }

    @Test
    void testErrorSummary_IsNotRetryable() {
        Path outsidePath = workspaceRoot.resolve("../secret.txt").normalize();
        Optional<ErrorSummary> error = guard.validatePath(outsidePath, "FileReadTool");
        assertTrue(error.isPresent());
        assertFalse(error.get().isRetryable(),
            "工作区越界不应可重试（非临时性错误）");
    }

    // ==================== 边界情况 ====================

    @Test
    void testNonExistentPath_InsideWorkspace() {
        Path nonExistent = workspaceRoot.resolve("does-not-exist.txt");
        Optional<ErrorSummary> error = guard.validatePath(nonExistent);
        // 路径不存在但仍在工作区内，应通过校验（文件存在性由调用方检查）
        assertTrue(error.isEmpty(),
            "工作区内不存在的文件应通过路径校验（存在性由上层检查）");
    }

    @Test
    void testNonExistentPath_OutsideWorkspace() {
        Path nonExistent = workspaceRoot.resolve("../does-not-exist.txt").normalize();
        Optional<ErrorSummary> error = guard.validatePath(nonExistent);
        assertTrue(error.isPresent(),
            "工作区外不存在的文件仍应被拦截");
    }

    @Test
    void testEmptyPath_ThrowsException() {
        assertThrows(Exception.class, () -> {
            guard.validatePath(null);
        }, "null 路径应抛出异常");
    }

    // ==================== 静态工厂 ====================

    @Test
    void testStrictFactory() {
        WorkspaceGuard strictGuard = WorkspaceGuard.strict(workspaceRoot);
        assertTrue(strictGuard.isResolveSymlinks());
        assertEquals(workspaceRoot.toAbsolutePath().normalize(), strictGuard.getWorkspaceRoot());
    }

    @Test
    void testLenientFactory() {
        WorkspaceGuard lenientGuard = WorkspaceGuard.lenient(workspaceRoot);
        assertFalse(lenientGuard.isResolveSymlinks());
    }

    @Test
    void testWorkspaceRootIsNormalized() {
        // 即使用 .. 等创建，也会被规范化
        Path messyRoot = workspaceRoot.resolve("src/../");
        WorkspaceGuard messyGuard = new WorkspaceGuard(messyRoot);
        Path normalized = workspaceRoot.normalize().toAbsolutePath();
        assertEquals(normalized, messyGuard.getWorkspaceRoot(),
            "工作区根路径应被规范化");
    }

    // ==================== 符号链接检测（严格模式） ====================

    @Test
    void testSymlinkDetection_StrictMode() throws IOException {
        // 创建工作区外的目录
        Path outsideDir = tempDir.resolve("outside");
        Files.createDirectories(outsideDir);
        Files.createFile(outsideDir.resolve("secret.txt"));

        // 在工作区内创建符号链接指向外部
        Path symlinkPath = workspaceRoot.resolve("link-to-outside");
        try {
            Files.createSymbolicLink(symlinkPath, outsideDir);

            // 严格模式应检测符号链接逃逸
            WorkspaceGuard strictGuard = WorkspaceGuard.strict(workspaceRoot);
            Optional<ErrorSummary> error = strictGuard.validatePath(
                symlinkPath.resolve("secret.txt"));
            assertTrue(error.isPresent(),
                "严格模式应拦截符号链接逃逸");
        } catch (UnsupportedOperationException | IOException e) {
            // 某些系统不支持符号链接，跳过测试
            System.out.println("跳过符号链接测试: " + e.getMessage());
        }
    }
}
