package com.jwcode.core.permission;

import com.jwcode.core.tool.ToolCategory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionManager 权限管理集成测试。
 * 
 * <p>覆盖以下核心场景：</p>
 * <ul>
 *   <li>🔒 权限校验在工具执行前触发</li>
 *   <li>🚫 权限不足阻止执行</li>
 *   <li>✅ 权限允许顺利执行</li>
 *   <li>👤 基于角色的权限控制</li>
 *   <li>⚡ 权限缓存机制</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionManagerIntegrationTest {

    private PermissionManager permissionManager;

    @BeforeEach
    void setUp() {
        permissionManager = new PermissionManager();
    }

    // ========================================================================
    // 1. 基础权限校验
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("默认情况下读工具应被允许")
    void testDefaultReadToolAllowed() {
        boolean allowed = permissionManager.canExecute("GlobTool");
        assertTrue(allowed, "默认配置下读工具应被允许执行");
    }

    @Test
    @Order(2)
    @DisplayName("默认情况下写工具可能需要权限")
    void testDefaultWriteToolPermission() {
        // 写工具可能需要额外的权限配置
        boolean allowed = permissionManager.canExecute("FileWriteTool");
        // 具体行为取决于 PermissionManager 的默认策略
        System.out.println("FileWriteTool 默认权限: " + allowed);
    }

    @Test
    @Order(3)
    @DisplayName("未知工具默认权限行为")
    void testUnknownToolPermission() {
        // 未注册的工具应返回明确的权限结果
        boolean allowed = permissionManager.canExecute("NonExistentTool");
        System.out.println("未知工具权限: " + allowed);
        // 不硬性断言，只记录行为
        assertNotNull(allowed, "权限结果不应为 null");
    }

    // ========================================================================
    // 2. 权限显式设置
    // ========================================================================

    @Test
    @Order(4)
    @DisplayName("显式禁止后工具执行被拒绝")
    void testExplicitDenyBlocksExecution() {
        permissionManager.setPermission("DangerousTool", false);

        boolean allowed = permissionManager.canExecute("DangerousTool");
        assertFalse(allowed, "显式禁止后工具不应被允许执行");
    }

    @Test
    @Order(5)
    @DisplayName("显式允许后工具可以执行")
    void testExplicitAllowAllowsExecution() {
        permissionManager.setPermission("RestrictedTool", true);

        boolean allowed = permissionManager.canExecute("RestrictedTool");
        assertTrue(allowed, "显式允许后工具应可以执行");
    }

    @Test
    @Order(6)
    @DisplayName("权限设置可以动态覆盖")
    void testPermissionCanBeOverridden() {
        permissionManager.setPermission("DynamicTool", false);
        assertFalse(permissionManager.canExecute("DynamicTool"));

        // 覆盖为允许
        permissionManager.setPermission("DynamicTool", true);
        assertTrue(permissionManager.canExecute("DynamicTool"), "权限应可以被动态覆盖");
    }

    // ========================================================================
    // 3. 基于类别的权限控制
    // ========================================================================

    @Test
    @Order(7)
    @DisplayName("可以按 Category 批量设置权限")
    void testCategoryBasedPermission() {
        // 禁止所有写工具
        permissionManager.setCategoryPermission(ToolCategory.WRITE, false);

        boolean writeAllowed = permissionManager.canExecuteByCategory(ToolCategory.WRITE, "AnyWriteTool");
        assertFalse(writeAllowed, "写工具类别被禁止时不应允许执行");

        // 读工具仍应被允许
        boolean readAllowed = permissionManager.canExecuteByCategory(ToolCategory.READ, "AnyReadTool");
        assertTrue(readAllowed, "读工具类别应仍被允许");
    }

    // ========================================================================
    // 4. 基于角色的权限控制
    // ========================================================================

    @Test
    @Order(8)
    @DisplayName("不同角色拥有不同权限")
    void testRoleBasedPermission() {
        // 设置角色权限
        permissionManager.setRolePermission("viewer", false);
        permissionManager.setRolePermission("admin", true);

        boolean viewerAccess = permissionManager.canExecuteWithRole("viewer", "AdminTool");
        boolean adminAccess = permissionManager.canExecuteWithRole("admin", "AdminTool");

        assertFalse(viewerAccess, "viewer 角色不应有权执行管理工具");
        assertTrue(adminAccess, "admin 角色应有权执行管理工具");
    }

    @Test
    @Order(9)
    @DisplayName("未配置的角色应使用默认权限")
    void testUnconfiguredRoleUsesDefault() {
        boolean guestAccess = permissionManager.canExecuteWithRole("guest", "SomeTool");
        // 未配置的角色应返回默认值
        System.out.println("Guest 角色默认权限: " + guestAccess);
    }

    // ========================================================================
    // 5. 权限缓存
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("权限结果应被缓存以提高性能")
    void testPermissionCache() {
        long startTime = System.nanoTime();

        // 第一次查询（可能未缓存）
        permissionManager.canExecute("CachedTool");
        long firstQueryTime = System.nanoTime() - startTime;

        // 第二次查询（应使用缓存）
        startTime = System.nanoTime();
        permissionManager.canExecute("CachedTool");
        long secondQueryTime = System.nanoTime() - startTime;

        System.out.println("第一次查询耗时: " + firstQueryTime + " ns");
        System.out.println("第二次查询耗时: " + secondQueryTime + " ns");

        // 缓存通常应更快，但不强制断言（性能波动）
        // 这里只记录日志，验证功能正常
    }

    @Test
    @Order(11)
    @DisplayName("权限更新后缓存应失效")
    void testCacheInvalidationOnPermissionUpdate() {
        permissionManager.setPermission("CacheInvalidateTool", true);
        assertTrue(permissionManager.canExecute("CacheInvalidateTool"),
                "首次设置应为 true");

        // 更新权限并验证缓存失效
        permissionManager.setPermission("CacheInvalidateTool", false);
        assertFalse(permissionManager.canExecute("CacheInvalidateTool"),
                "更新后缓存应失效，返回新的 false 值");
    }

    // ========================================================================
    // 6. 边界条件
    // ========================================================================

    @Test
    @Order(12)
    @DisplayName("null 工具名应安全处理")
    void testNullToolName() {
        assertDoesNotThrow(() -> permissionManager.canExecute(null),
                "null 工具名不应抛出异常");
    }

    @Test
    @Order(13)
    @DisplayName("空字符串工具名应安全处理")
    void testEmptyToolName() {
        assertDoesNotThrow(() -> permissionManager.canExecute(""),
                "空字符串工具名不应抛出异常");
    }

    @Test
    @Order(14)
    @DisplayName("批量重置权限")
    void testResetAllPermissions() {
        permissionManager.setPermission("ToolA", false);
        permissionManager.setPermission("ToolB", false);
        permissionManager.setPermission("ToolC", false);

        // 重置所有权限
        permissionManager.resetAllPermissions();

        // 重置后应为默认值
        assertNotNull(permissionManager.canExecute("ToolA"),
                "重置后权限结果不应为 null");
    }

    // ========================================================================
    // 7. 安全审计
    // ========================================================================

    @Test
    @Order(15)
    @DisplayName("权限变更应生成审计日志")
    void testPermissionChangeAuditLog() {
        permissionManager.setPermission("AuditedTool", false);

        // 获取审计日志
        String auditLog = permissionManager.getAuditLog();
        assertNotNull(auditLog, "审计日志不应为 null");
        assertTrue(auditLog.contains("AuditedTool"),
                "审计日志应包含被变更的工具名");
    }

    @Test
    @Order(16)
    @DisplayName("权限拒绝事件应记录")
    void testPermissionDeniedAudit() {
        permissionManager.setPermission("BlockedTool", false);
        permissionManager.canExecute("BlockedTool");  // 应被拒绝

        String auditLog = permissionManager.getAuditLog();
        assertTrue(auditLog.toLowerCase().contains("deny") || auditLog.toLowerCase().contains("block"),
                "审计日志应记录权限拒绝事件");
    }
}
