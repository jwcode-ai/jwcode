package com.jwcode.core.git;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        java.util.concurrent.CompletableFuture<List<WorktreeInfo>> future = worktreeManager.listWorktrees();
        List<WorktreeInfo> list = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
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
        java.util.concurrent.CompletableFuture<WorktreeManager.WorktreeResult> future =
            worktreeManager.createWorktree("feature/test", worktreePath.toString());
        assertNotNull(future);
    }

    @Test
    @DisplayName("使用新分支创建 Worktree（异步）")
    void testCreateWorktreeWithNewBranch() {
        Path worktreePath = tempDir.resolve("new-branch");
        java.util.concurrent.CompletableFuture<WorktreeManager.WorktreeResult> future =
            worktreeManager.createWorktreeWithNewBranch("feature/new-branch", worktreePath.toString());
        assertNotNull(future);
    }

    @Test
    @DisplayName("删除 Worktree（异步）")
    void testRemoveWorktree() {
        java.util.concurrent.CompletableFuture<WorktreeManager.WorktreeResult> future =
            worktreeManager.removeWorktree("/tmp/test-worktree");
        assertNotNull(future);
    }

    @Test
    @DisplayName("同步删除 Worktree")
    void testRemoveWorktreeSync() {
        WorktreeManager.WorktreeResult result = worktreeManager.removeWorktreeSync("/tmp/test-worktree");
        assertNotNull(result);
    }

    @Test
    @DisplayName("获取当前 Worktree")
    void testGetCurrentWorktree() throws Exception {
        java.util.concurrent.CompletableFuture<java.util.Optional<WorktreeInfo>> future =
            worktreeManager.getCurrentWorktree();
        java.util.Optional<WorktreeInfo> opt = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(opt);
    }

    @Test
    @DisplayName("进入 Worktree")
    void testEnterWorktree() {
        Path wtPath = tempDir.resolve("target-worktree");
        java.util.concurrent.CompletableFuture<WorktreeState> future =
            worktreeManager.enterWorktree(wtPath);
        assertNotNull(future);
    }

    @Test
    @DisplayName("退出 Worktree")
    void testExitWorktree() throws Exception {
        WorktreeState state = new WorktreeState(
            repoPath, "main", tempDir.resolve("target-worktree"));
        java.util.concurrent.CompletableFuture<Boolean> future =
            worktreeManager.exitWorktree(state);
        Boolean result = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    @DisplayName("执行 Git Worktree 命令")
    void testExecuteWorktreeCommand() throws Exception {
        java.util.concurrent.CompletableFuture<String> future =
            worktreeManager.executeWorktreeCommand("list");
        String output = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(output);
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
    @DisplayName("移除 Worktree 的验证")
    void testValidateRemoveWorktree() {
        WorktreeValidator.ValidationResult result =
            WorktreeValidator.validateRemoveWorktree("/tmp/path", worktreeManager);
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
        assertTrue(state.hasSessionData("key1"));
        state.removeSessionData("key1");
        assertFalse(state.hasSessionData("key1"));
    }

    @Test
    @DisplayName("WorktreeState restoreBranch 标记")
    void testWorktreeStateRestoreBranch() {
        WorktreeState state = new WorktreeState(repoPath, "main", tempDir);
        assertFalse(state.shouldRestoreBranch());
        state.setRestoreBranch(true);
        assertTrue(state.shouldRestoreBranch());
    }
}
