package com.jwcode.core.permission;

import com.jwcode.core.tool.permission.PermissionChecker;

/**
 * PermissionChecker 实现 — 将 PermissionManager 桥接到工具执行管线。
 *
 * <p>ToolExecutor 通过此实现调用 PermissionManager 进行实际的权限判断。
 * 支持 autoMode（自动分类器）和标准模式（启发式规则）。</p>
 */
public class PermissionManagerChecker implements PermissionChecker {

    private final PermissionManager permissionManager;

    public PermissionManagerChecker() {
        this(PermissionManager.getInstance());
    }

    public PermissionManagerChecker(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    @Override
    public boolean hasPermission(String operation, String resource) {
        if (resource == null) return true;

        PermissionManager.PermissionCheckResult result = switch (operation) {
            case "read" -> permissionManager.canRead(resource);
            case "write" -> permissionManager.canWrite(resource);
            case "delete" -> permissionManager.canDelete(resource);
            case "execute" -> permissionManager.canExecuteCommand(resource);
            default -> PermissionManager.PermissionCheckResult.allowed();
        };

        return result.isAllowed();
    }

    @Override
    public boolean canReadFile(String filePath) {
        return permissionManager.canRead(filePath).isAllowed();
    }

    @Override
    public boolean canWriteFile(String filePath) {
        return permissionManager.canWrite(filePath).isAllowed();
    }

    @Override
    public boolean canExecuteCommand(String command) {
        return permissionManager.canExecuteCommand(command).isAllowed();
    }

    @Override
    public boolean requiresApproval(String operation, String resource) {
        if (resource == null) return false;

        PermissionManager.PermissionCheckResult result = switch (operation) {
            case "write" -> permissionManager.canWrite(resource);
            case "delete" -> permissionManager.canDelete(resource);
            case "execute" -> permissionManager.canExecuteCommand(resource);
            default -> PermissionManager.PermissionCheckResult.allowed();
        };

        return result.needsConfirmation();
    }

    /** 获取底层 PermissionManager，供外部配置 autoMode 等 */
    public PermissionManager getPermissionManager() {
        return permissionManager;
    }
}
