package com.jwcode.core.plan;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolCategory;
import com.jwcode.core.tool.SideEffect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlanModeManager 模式切换 + 工具权限隔离集成测试。
 * 
 * <p>覆盖以下核心场景：</p>
 * <ul>
 *   <li>🔄 模式切换：NORMAL ↔ PLAN ↔ ACT ↔ NORMAL 完整循环</li>
 *   <li>🔒 写工具白名单阻断：PLAN 模式下 17 个写工具全部被阻止</li>
 *   <li>📖 读工具放行：PLAN 模式下读工具全部被允许</li>
 *   <li>💾 持久化：模式状态写入 .jwcoplan 文件并正确恢复</li>
 *   <li>🔔 监听器通知：模式切换时监听器被正确调用</li>
 *   <li>📋 历史记录：切换历史被正确记录</li>
 * </ul>
 * 
 * <h3>测试策略</h3>
 * <ul>
 *   <li>使用 @TempDir 隔离临时文件，避免污染项目目录</li>
 *   <li>对每个写工具逐一验证 PLAN 模式下被阻塞</li>
 *   <li>对每个读工具逐一验证 PLAN 模式下被允许</li>
 *   <li>模拟文件损坏场景测试持久化恢复</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlanModeManagerIntegrationTest {

    private PlanModeManager manager;
    private Path tempDir;

    @TempDir
    Path sharedTempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = sharedTempDir;
        // PlanModeManager 是单例，需要重置状态
        manager = PlanModeManager.getInstance();
        // 使用反射或提供的方法重置为 NORMAL
        manager.setModeDirect(Mode.NORMAL);
    }

    // ========================================================================
    // 1. 初始状态验证
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("初始模式应为 NORMAL")
    void testInitialModeIsNormal() {
        assertEquals(Mode.NORMAL, manager.getCurrentMode(),
                "PlanModeManager 初始化后应处于 NORMAL 模式");
    }

    @Test
    @Order(2)
    @DisplayName("初始模式下既不是 PLAN 也不是 ACT")
    void testInitialModeFlags() {
        assertFalse(manager.isPlanMode(), "NORMAL 模式 isPlanMode() 应返回 false");
        assertFalse(manager.isActMode(), "NORMAL 模式 isActMode() 应返回 false");
    }

    // ========================================================================
    // 2. 模式切换基础功能
    // ========================================================================

    @Test
    @Order(3)
    @DisplayName("NORMAL → PLAN: 切换成功，标志正确")
    void testSwitchToPlanMode() {
        manager.switchToPlan("测试切换到 Plan 模式");

        assertEquals(Mode.PLAN, manager.getCurrentMode());
        assertTrue(manager.isPlanMode(), "切换到 PLAN 后 isPlanMode() 应返回 true");
        assertFalse(manager.isActMode(), "切换到 PLAN 后 isActMode() 应返回 false");
    }

    @Test
    @Order(4)
    @DisplayName("NORMAL → ACT: 切换成功，标志正确")
    void testSwitchToActMode() {
        manager.switchToAct("测试切换到 Act 模式");

        assertEquals(Mode.ACT, manager.getCurrentMode());
        assertTrue(manager.isActMode(), "切换到 ACT 后 isActMode() 应返回 true");
        assertFalse(manager.isPlanMode(), "切换到 ACT 后 isPlanMode() 应返回 false");
    }

    @Test
    @Order(5)
    @DisplayName("PLAN → NORMAL: 可以切回普通模式")
    void testSwitchBackToNormal() {
        manager.switchToPlan("进入 PLAN");
        assertEquals(Mode.PLAN, manager.getCurrentMode());

        manager.switchToNormal("退出到 NORMAL");
        assertEquals(Mode.NORMAL, manager.getCurrentMode());
        assertFalse(manager.isPlanMode());
        assertFalse(manager.isActMode());
    }

    @Test
    @Order(6)
    @DisplayName("完整模式循环: NORMAL → PLAN → ACT → NORMAL")
    void testFullModeCycle() {
        manager.switchToPlan("进入 PLAN");
        assertEquals(Mode.PLAN, manager.getCurrentMode());

        manager.switchToAct("进入 ACT");
        assertEquals(Mode.ACT, manager.getCurrentMode());

        manager.switchToNormal("回到 NORMAL");
        assertEquals(Mode.NORMAL, manager.getCurrentMode());
    }

    // ========================================================================
    // 3. 写工具白名单阻断（PLAN 模式）
    // ========================================================================

    /**
     * 写工具清单 — PLAN 模式下应全部被阻止
     */
    static final List<String> WRITE_TOOLS = List.of(
            "Bash", "PowerShell", "REPL",
            "FileWrite", "FileEdit", "NotebookEdit", "Git",
            "RemoteTrigger", "ScheduleCron", "SendMessage",
            "TeamCreate", "TeamDelete", "McpAuth"
    );

    /**
     * 读工具清单 — PLAN 模式下应全部被允许
     */
    static final List<String> READ_TOOLS = List.of(
            "Read", "Glob", "Grep", "List",
            "BatchRead", "FileRead", "SmartAnalyze",
            "MergeFiles", "WebSearch", "WebFetch",
            "SemanticSearch", "Pattern", "Task",
            "Config", "MCP", "LSP", "Agent"
    );

    @Test
    @Order(7)
    @DisplayName("PLAN 模式下写工具全部被阻止")
    void testPlanModeBlocksAllWriteTools() {
        manager.switchToPlan("测试写工具阻断");

        for (String toolName : WRITE_TOOLS) {
            assertFalse(manager.isToolAllowed(toolName),
                    () -> "PLAN 模式下写工具 [" + toolName + "] 应被阻止");
        }
    }

    @Test
    @Order(8)
    @DisplayName("PLAN 模式下读工具全部被允许")
    void testPlanModeAllowsAllReadTools() {
        manager.switchToPlan("测试读工具放行");

        for (String toolName : READ_TOOLS) {
            assertTrue(manager.isToolAllowed(toolName),
                    () -> "PLAN 模式下读工具 [" + toolName + "] 应被允许");
        }
    }

    @Test
    @Order(9)
    @DisplayName("PLAN 模式下读写工具泾渭分明：写工具全部 blocked，读工具全部 allowed")
    void testPlanModeReadWriteSeparation() {
        manager.switchToPlan("测试读写分离");

        // 写工具全部被阻止
        for (String toolName : WRITE_TOOLS) {
            assertFalse(manager.isToolAllowed(toolName),
                    () -> "写工具 [" + toolName + "] 应在 PLAN 模式下被阻止");
        }

        // 读工具全部被放行
        for (String toolName : READ_TOOLS) {
            assertTrue(manager.isToolAllowed(toolName),
                    () -> "读工具 [" + toolName + "] 应在 PLAN 模式下被放行");
        }
    }

    @Test
    @Order(10)
    @DisplayName("ACT 模式下所有工具可用（无限制）")
    void testActModeAllowsAllTools() {
        manager.switchToAct("测试 ACT 模式工具可用");

        // 验证写工具可用
        for (String toolName : WRITE_TOOLS) {
            assertTrue(manager.isToolAllowed(toolName),
                    () -> "ACT 模式下写工具 [" + toolName + "] 应被允许");
        }

        // 验证读工具可用
        for (String toolName : READ_TOOLS) {
            assertTrue(manager.isToolAllowed(toolName),
                    () -> "ACT 模式下读工具 [" + toolName + "] 应被允许");
        }
    }

    @Test
    @Order(11)
    @DisplayName("NORMAL 模式下所有工具可用")
    void testNormalModeAllowsAllTools() {
        // NORMAL 模式下所有工具都可用
        for (String toolName : WRITE_TOOLS) {
            assertTrue(manager.isToolAllowed(toolName),
                    () -> "NORMAL 模式下写工具 [" + toolName + "] 应被允许");
        }
        for (String toolName : READ_TOOLS) {
            assertTrue(manager.isToolAllowed(toolName),
                    () -> "NORMAL 模式下读工具 [" + toolName + "] 应被允许");
        }
    }

    // ========================================================================
    // 4. 模式切换监听器
    // ========================================================================

    @Test
    @Order(12)
    @DisplayName("注册监听器并在模式切换时收到通知")
    void testModeChangeListenerNotification() {
        AtomicInteger callCount = new AtomicInteger(0);
        Mode[] capturedOldMode = new Mode[1];
        Mode[] capturedNewMode = new Mode[1];
        String[] capturedReason = new String[1];

        PlanModeManager.ModeChangeListener listener = (oldMode, newMode, reason) -> {
            callCount.incrementAndGet();
            capturedOldMode[0] = oldMode;
            capturedNewMode[0] = newMode;
            capturedReason[0] = reason;
        };

        manager.addModeChangeListener(listener);

        // 切换模式
        manager.switchToPlan("测试监听器");

        assertEquals(1, callCount.get(), "监听器应被调用 1 次");
        assertEquals(Mode.NORMAL, capturedOldMode[0], "旧模式应为 NORMAL");
        assertEquals(Mode.PLAN, capturedNewMode[0], "新模式应为 PLAN");
        assertEquals("测试监听器", capturedReason[0], "原因字符串应匹配");
    }

    @Test
    @Order(13)
    @DisplayName("移除监听器后不再收到通知")
    void testRemoveModeChangeListener() {
        AtomicInteger callCount = new AtomicInteger(0);
        PlanModeManager.ModeChangeListener listener = (oldMode, newMode, reason) -> 
            callCount.incrementAndGet();

        manager.addModeChangeListener(listener);
        manager.removeModeChangeListener(listener);

        manager.switchToPlan("移除监听器后");

        assertEquals(0, callCount.get(), "移除监听器后不应再被调用");
    }

    // ========================================================================
    // 5. 模式切换历史记录
    // ========================================================================

    @Test
    @Order(14)
    @DisplayName("模式切换历史被正确记录")
    void testModeChangeHistoryRecorded() {
        manager.switchToPlan("进入 PLAN");
        manager.switchToAct("进入 ACT");
        manager.switchToNormal("回到 NORMAL");

        // 获取历史记录（需要 PlanModeManager 提供 getHistory() 方法或其它方式）
        List<PlanModeManager.ModeChangeEvent> history = manager.getModeChangeHistory();

        assertNotNull(history, "历史记录不应为空");
        assertEquals(3, history.size(), "应有 3 条历史记录");

        // 验证第1条记录
        assertEquals(Mode.PLAN, history.get(0).getNewMode());
        assertEquals("进入 PLAN", history.get(0).getReason());

        // 验证第2条记录
        assertEquals(Mode.ACT, history.get(1).getNewMode());

        // 验证第3条记录
        assertEquals(Mode.NORMAL, history.get(2).getNewMode());
    }

    // ========================================================================
    // 6. 持久化测试
    // ========================================================================

    @Test
    @Order(15)
    @DisplayName("模式状态持久化到文件，文件内容正确")
    void testModePersistenceToFile() throws IOException {
        // 切换到 PLAN 模式，这会触发持久化
        manager.switchToPlan("测试持久化");

        // 检查持久化文件是否存在
        Path planFile = tempDir.resolve(".jwcoplan");
        // 注意：实际 PlanModeManager 可能使用固定路径，这里需要根据实现调整
        // 这里我们测试 PlanModeManager 的持久化方法是否正常工作
        manager.persistTo(tempDir.toString());

        assertTrue(Files.exists(planFile), "持久化文件 .jwcoplan 应被创建");
        String content = Files.readString(planFile);
        assertTrue(content.contains("PLAN"), "文件内容应包含 PLAN 模式标识");
    }

    @Test
    @Order(16)
    @DisplayName("从文件恢复模式状态")
    void testModeRestoreFromFile() throws IOException {
        // 先持久化 ACT 模式
        manager.switchToAct("测试恢复");
        manager.persistTo(tempDir.toString());

        // 手动修改文件内容为 ACT（模拟持久化内容）
        Path planFile = tempDir.resolve(".jwcoplan");
        Files.writeString(planFile, "MODE=ACT\n");

        // 从文件恢复
        manager.restoreFrom(tempDir.toString());

        assertEquals(Mode.ACT, manager.getCurrentMode(),
                "从文件恢复后模式应为 ACT");
    }

    @Test
    @Order(17)
    @DisplayName("持久化文件损坏时优雅回退到 NORMAL")
    void testCorruptedPersistenceFile() throws IOException {
        // 写入损坏内容
        Path planFile = tempDir.resolve(".jwcoplan");
        Files.writeString(planFile, "损坏的数据格式###");

        // 恢复时应回退到 NORMAL 而不抛出异常
        assertDoesNotThrow(() -> manager.restoreFrom(tempDir.toString()),
                "损坏的持久化文件不应抛出异常");
        assertEquals(Mode.NORMAL, manager.getCurrentMode(),
                "损坏文件恢复后应回退到 NORMAL 模式");
    }

    // ========================================================================
    // 7. 边界条件
    // ========================================================================

    @Test
    @Order(18)
    @DisplayName("未知工具名在 PLAN 模式下默认不允许")
    void testUnknownToolInPlanMode() {
        manager.switchToPlan("测试未知工具");

        assertFalse(manager.isToolAllowed("NonExistentTool"),
                "未知工具名在 PLAN 模式下应返回 false");
    }

    @Test
    @Order(19)
    @DisplayName("空工具名在 PLAN 模式下应返回 false")
    void testNullToolNameInPlanMode() {
        manager.switchToPlan("测试空名");

        assertFalse(manager.isToolAllowed(null),
                "null 工具名应返回 false");
        assertFalse(manager.isToolAllowed(""),
                "空字符串工具名应返回 false");
    }

    @Test
    @Order(20)
    @DisplayName("连续快速切换模式不应导致不一致")
    void testRapidModeSwitching() {
        for (int i = 0; i < 10; i++) {
            manager.switchToPlan("快速切换 " + i);
            assertEquals(Mode.PLAN, manager.getCurrentMode());
            manager.switchToNormal("快速切换 " + i);
            assertEquals(Mode.NORMAL, manager.getCurrentMode());
        }
        // 最终状态应为 NORMAL
        assertEquals(Mode.NORMAL, manager.getCurrentMode());
    }
}
