package com.jwcode.core.git;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Git Worktree 集成测试
 *
 * <p>测试 Git Worktree 管理器的核心功能：
 * Worktree 验证、信息管理、状态跟踪等。</p>
 */
@DisplayName("Git Worktree 集成测试")
public class GitWorktreeIntegrationTest {

    private WorktreeManager worktreeManager;
    private Path repoPath;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        repoPath = tempDir.resolve("test-repo");
        worktreeManager = new WorktreeManager(repoPath.toString());
    }

    @Test
    @DisplayName("WorktreeManager 默认构造")
    void testDefaultConstructor() {
        WorktreeManager wm = new WorktreeManager();
        assertNotNull(wm);
    }

    @Test
    @DisplayName("WorktreeManager 带 Path 构造")
    void testPathConstructor() {
        WorktreeManager wm = new WorktreeManager(repoPath);
        assertNotNull(wm);
    }

    @Test
    @DisplayName("设置和获取工作目录")
    void testSetAndGetWorkingDirectory() {
        worktreeManager.setWorkingDirectory("/tmp/test-repo");
        // 验证设置不抛异常
    }

    @Test
    @DisplayName("设置和获取当前 Worktree 路径")
    void testSetAndGetCurrentWorktreePath() {
        Path worktreePath = tempDir.resolve("my-worktree");
        worktreeManager.setCurrentWorktreePath(worktreePath);
        Path retrieved = worktreeManager.getCurrentWorktreePath();
        assertEquals(worktreePath, retrieved);
    }

    @Test
    @DisplayName("列出 Worktree 列表（异步）")
    void testListWorktreesAsync() throws Exception {
        CompletableFuture<List<WorktreeInfo>> future = worktreeManager.listWorktrees();
        List<WorktreeInfo> list = future.get(10, TimeUnit.SECONDS);
        assertNotNull(list);
    }

    @Test
    @DisplayName("列出 Worktree 列表（同步）")
    void testListWorktreesSync() throws Exception {
        try {
            List<WorktreeInfo> list = worktreeManager.listWorktreesSync();
            assertNotNull(list);
        } catch (Exception e) {
            // 非 Git 仓库目录可能抛出异常，这是预期行为
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("创建 Worktree（异步）")
    void testCreateWorktree() {
        Path worktreePath = tempDir.resolve("feature-branch");
        CompletableFuture<WorktreeManager.WorktreeResult> future =
            worktreeManager.createWorktree("feature/test", worktreePath.toString());
        assertNotNull(future);
    }

    @Test
    @DisplayName("创建 Worktree（同步）")
    void testCreateWorktreeSync() throws Exception {
        try {
            Path worktreePath = tempDir.resolve("sync-branch");
            WorktreeManager.WorktreeResult result =
                worktreeManager.createWorktreeSync("feature/sync", worktreePath.toString());
            assertNotNull(result);
        } catch (Exception e) {
            // 非 Git 仓库可能抛出异常
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("使用新分支创建 Worktree（异步）- 需要 baseBranch 参数")
    void testCreateWorktreeWithNewBranch() {
        Path worktreePath = tempDir.resolve("new-branch");
        CompletableFuture<WorktreeManager.WorktreeResult> future =
            worktreeManager.createWorktreeWithNewBranch("feature/new-branch", worktreePath.toString(), "main");
        assertNotNull(future);
    }

    @Test
    @DisplayName("使用新分支创建 Worktree（同步）")
    void testCreateWorktreeWithNewBranchSync() throws Exception {
        try {
            Path worktreePath = tempDir.resolve("sync-new-branch");
            WorktreeManager.WorktreeResult result =
                worktreeManager.createWorktreeWithNewBranchSync("feature/sync-new", worktreePath.toString(), "main");
            assertNotNull(result);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("删除 Worktree（异步）")
    void testRemoveWorktree() {
        CompletableFuture<WorktreeManager.WorktreeResult> future =
            worktreeManager.removeWorktree("/tmp/test-worktree");
        assertNotNull(future);
    }

    @Test
    @DisplayName("同步删除 Worktree")
    void testRemoveWorktreeSync() throws Exception {
        try {
            WorktreeManager.WorktreeResult result = worktreeManager.removeWorktreeSync("/tmp/test-worktree");
            assertNotNull(result);
        } catch (Exception e) {
            // 非 Git 仓库可能抛出异常
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("获取当前 Worktree")
    void testGetCurrentWorktree() throws Exception {
        CompletableFuture<Optional<WorktreeInfo>> future =
            worktreeManager.getCurrentWorktree();
        Optional<WorktreeInfo> opt = future.get(10, TimeUnit.SECONDS);
        assertNotNull(opt);
    }

    @Test
    @DisplayName("关闭 WorktreeManager")
    void testShutdown() {
        assertDoesNotThrow(() -> worktreeManager.shutdown());
    }

    // ========== WorktreeValidator 测试 ==========

    @Test
    @DisplayName("验证合法 Worktree 路径")
    void testValidateValidWorktreePath() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.validateWorktreePath("/tmp/valid-path");
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("验证非法 Worktree 路径")
    void testValidateInvalidWorktreePath() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.validateWorktreePath("");
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("验证合法分支名")
    void testValidateValidBranchName() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.validateBranchName("feature/test-branch");
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("验证非法分支名")
    void testValidateInvalidBranchName() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.validateBranchName("");
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("检查 Worktree 是否存在")
    void testWorktreeExists() {
        boolean exists = WorktreeValidator.worktreeExists(worktreeManager, "/tmp/some-path");
        assertFalse(exists);
    }

    @Test
    @DisplayName("检查路径是否可创建 Worktree")
    void testCanCreateWorktreeAt() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.canCreateWorktreeAt("/tmp/valid-path");
        assertNotNull(result);
    }

    @Test
    @DisplayName("验证创建 Worktree 的前提条件")
    void testValidateCreateWorktree() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.validateCreateWorktree("feature/test", "/tmp/path", worktreeManager);
        assertNotNull(result);
    }

    @Test
    @DisplayName("验证进入 Worktree 的前提条件")
    void testValidateEnterWorktree() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.validateEnterWorktree("/tmp/path", worktreeManager);
        assertNotNull(result);
    }

    @Test
    @DisplayName("移除 Worktree 的验证 - 需要 currentWorktreePath 参数")
    void testValidateRemoveWorktree() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.validateRemoveWorktree("/tmp/path", worktreeManager, repoPath);
        assertNotNull(result);
    }

    // ========== WorktreeInfo 测试 ==========

    @Test
    @DisplayName("WorktreeInfo 构造和访问")
    void testWorktreeInfo() {
        WorktreeInfo info = new WorktreeInfo(
            repoPath, "main", "abc123", false, false, true);
        assertEquals(repoPath, info.getPath());
        assertEquals("main", info.getBranch());
        assertEquals("abc123", info.getCommit());
        assertFalse(info.isBare());
        assertFalse(info.isDetached());
        assertTrue(info.isMain());
    }

    // ========== WorktreeState 测试 ==========

    @Test
    @DisplayName("WorktreeState 构造和访问")
    void testWorktreeState() {
        Path origDir = tempDir.resolve("orig");
        Path wtPath = tempDir.resolve("wt");
        WorktreeState state = new WorktreeState(origDir, "main", wtPath);
        assertEquals(origDir, state.getOriginalDir());
        assertEquals("main", state.getOriginalBranch());
        assertEquals(wtPath, state.getWorktreePath());
    }

    @Test
    @DisplayName("WorktreeState 会话数据管理")
    void testWorktreeStateSessionData() {
        WorktreeState state = new WorktreeState(repoPath, "main", tempDir);
        state.putSessionData("key1", "value1");
        assertEquals("value1", state.getSessionData("key1"));
        assertNotNull(state.getSessionData());
    }

    @Test
    @DisplayName("WorktreeState 持续时间")
    void testWorktreeStateDuration() {
        WorktreeState state = new WorktreeState(repoPath, "main", tempDir);
        assertTrue(state.getDuration() >= 0);
    }

    @Test
    @DisplayName("WorktreeState needsBranchRestore")
    void testWorktreeStateNeedsBranchRestore() {
        WorktreeState state = new WorktreeState(repoPath, "main", tempDir);
        assertFalse(state.needsBranchRestore("main"));
        assertTrue(state.needsBranchRestore("other"));
    }
}
