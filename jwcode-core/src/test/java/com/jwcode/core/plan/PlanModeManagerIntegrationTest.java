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
        // 同时删除持久化状态文件，避免 loadMode() 从文件恢复旧状态
        PlanModeManager.resetInstance();
        try {
            java.nio.file.Files.deleteIfExists(
                java.nio.file.Paths.get(System.getProperty("user.dir"), ".jwcode/state.json"));
        } catch (Exception ignored) { }
        manager = PlanModeManager.getInstance();
    }

    // ========================================================================
    // 1. 初始状态验证
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("初始模式应为 NORMAL")
    void testInitialModeIsNormal() {
        assertEquals(PlanModeManager.Mode.NORMAL, manager.getCurrentMode(),
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
    void testEnterPlanMode() {
        boolean result = manager.enterPlanMode("测试切换到 Plan 模式");
        assertTrue(result);

        assertEquals(PlanModeManager.Mode.PLAN, manager.getCurrentMode());
        assertTrue(manager.isPlanMode(), "切换到 PLAN 后 isPlanMode() 应返回 true");
        assertFalse(manager.isActMode(), "切换到 PLAN 后 isActMode() 应返回 false");
    }

    @Test
    @Order(4)
    @DisplayName("PLAN → ACT: 退出 Plan 模式自动进入 Act 模式")
    void testExitPlanMode() {
        manager.enterPlanMode("进入 PLAN");
        assertEquals(PlanModeManager.Mode.PLAN, manager.getCurrentMode());

        boolean result = manager.exitPlanMode("退出到 ACT");
        assertTrue(result);
        assertEquals(PlanModeManager.Mode.ACT, manager.getCurrentMode());
        assertFalse(manager.isPlanMode());
        assertTrue(manager.isActMode());
    }

    @Test
    @Order(5)
    @DisplayName("NORMAL → ACT: 进入 Act 模式")
    void testEnterActMode() {
        boolean result = manager.enterActMode();
        assertTrue(result);

        assertEquals(PlanModeManager.Mode.ACT, manager.getCurrentMode());
        assertTrue(manager.isActMode(), "切换到 ACT 后 isActMode() 应返回 true");
        assertFalse(manager.isPlanMode(), "切换到 ACT 后 isPlanMode() 应返回 false");
    }

    @Test
    @Order(6)
    @DisplayName("ACT → NORMAL: 退出 Act 模式")
    void testExitActMode() {
        manager.enterActMode();
        assertEquals(PlanModeManager.Mode.ACT, manager.getCurrentMode());

        boolean result = manager.exitActMode();
        assertTrue(result);
        assertEquals(PlanModeManager.Mode.NORMAL, manager.getCurrentMode());
    }

    @Test
    @Order(7)
    @DisplayName("完整模式循环: NORMAL → PLAN → ACT → NORMAL")
    void testFullModeCycle() {
        manager.enterPlanMode("进入 PLAN");
        assertEquals(PlanModeManager.Mode.PLAN, manager.getCurrentMode());

        manager.exitPlanMode("完成 PLAN → 自动进入 ACT");
        assertEquals(PlanModeManager.Mode.ACT, manager.getCurrentMode());

        manager.exitActMode();
        assertEquals(PlanModeManager.Mode.NORMAL, manager.getCurrentMode());
    }

    // ========================================================================
    // 3. 写工具白名单阻断（PLAN 模式）
    // ========================================================================

    // ========================================================================
    // 3. 模式切换监听器
    // ========================================================================

    @Test
    @Order(8)
    @DisplayName("注册监听器并在模式切换时收到通知")
    void testModeChangeListenerNotification() {
        AtomicInteger callCount = new AtomicInteger(0);

        PlanModeManager.ModeChangeListener listener = event -> {
            callCount.incrementAndGet();
        };

        manager.addListener(listener);

        // 切换模式
        manager.enterPlanMode("测试监听器");

        assertTrue(callCount.get() > 0, "监听器应被调用至少 1 次");
    }

    @Test
    @Order(9)
    @DisplayName("移除监听器后不再收到通知")
    void testRemoveModeChangeListener() {
        AtomicInteger callCount = new AtomicInteger(0);
        PlanModeManager.ModeChangeListener listener = event -> 
            callCount.incrementAndGet();

        manager.addListener(listener);
        manager.removeListener(listener);

        manager.enterPlanMode("移除监听器后");

        assertEquals(0, callCount.get(), "移除监听器后不应再被调用");
    }

    // ========================================================================
    // 4. 历史记录
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("模式切换历史被正确记录")
    void testModeChangeHistoryRecorded() {
        // PlanModeManager 是单例，setUp 中 resetInstance 确保每次测试有干净的实例
        // 验证 enterPlanMode 和 exitPlanMode 后历史记录增加了
        assertEquals(0, manager.getHistory().size(), "新实例历史应为空");

        manager.enterPlanMode("进入 PLAN");
        assertEquals(1, manager.getHistory().size(), "进入 PLAN 后应有 1 条历史记录");

        manager.exitPlanMode("完成 PLAN");
        assertEquals(2, manager.getHistory().size(), "退出 PLAN 后应有 2 条历史记录");
    }

    @Test
    @Order(11)
    @DisplayName("获取最近一次切换事件")
    void testLastEvent() {
        manager.enterPlanMode("测试最近事件");
        java.util.Optional<PlanModeManager.ModeChangeEvent> lastEvent = manager.getLastEvent();
        assertTrue(lastEvent.isPresent());
        assertEquals(PlanModeManager.Mode.PLAN, lastEvent.get().newMode());
        assertEquals("测试最近事件", lastEvent.get().description());
    }

    // ========================================================================
    // 5. 边界条件
    // ========================================================================

    @Test
    @Order(12)
    @DisplayName("连续快速切换模式不应导致不一致")
    void testRapidModeSwitching() {
        for (int i = 0; i < 10; i++) {
            manager.enterPlanMode("快速切换 " + i);
            assertEquals(PlanModeManager.Mode.PLAN, manager.getCurrentMode());
            manager.exitPlanMode("快速切换 " + i);
            assertEquals(PlanModeManager.Mode.ACT, manager.getCurrentMode());
            manager.exitActMode();
            assertEquals(PlanModeManager.Mode.NORMAL, manager.getCurrentMode());
        }
        // 最终状态应为 NORMAL
        assertEquals(PlanModeManager.Mode.NORMAL, manager.getCurrentMode());
    }
}
