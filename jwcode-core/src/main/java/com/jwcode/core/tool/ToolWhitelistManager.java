package com.jwcode.core.tool;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具白名单管理器 — 用于限制子 Agent（如后台回顾 Agent）可使用的工具集。
 *
 * <p>使用 ThreadLocal 实现线程隔离，每个线程可设置独立的白名单。
 * 空集合 = 不限制；非空集合 = 仅允许集合内的工具。
 */
public class ToolWhitelistManager {

    private static final ToolWhitelistManager INSTANCE = new ToolWhitelistManager();

    private final ThreadLocal<Set<String>> whitelist = ThreadLocal.withInitial(() -> null);

    private ToolWhitelistManager() {}

    public static ToolWhitelistManager getInstance() {
        return INSTANCE;
    }

    /**
     * 为当前线程设置工具白名单。
     *
     * @param allowedTools 允许的工具名称集合，null 或空表示不限制
     */
    public void setWhitelist(Set<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            whitelist.set(null);
        } else {
            Set<String> restricted = ConcurrentHashMap.<String>newKeySet();
            restricted.addAll(allowedTools);
            whitelist.set(Collections.unmodifiableSet(restricted));
        }
    }

    /**
     * 清除当前线程的白名单（不限制）。
     */
    public void clearWhitelist() {
        whitelist.remove();
    }

    /**
     * 检查工具是否被白名单允许。
     *
     * @param toolName 工具名称
     * @return true 如果允许（无白名单或工具在白名单中）
     */
    public boolean isAllowed(String toolName) {
        Set<String> allowed = whitelist.get();
        if (allowed == null) {
            return true; // 无白名单限制
        }
        return allowed.contains(toolName);
    }

    /**
     * 当前线程是否有白名单限制。
     */
    public boolean hasRestriction() {
        return whitelist.get() != null;
    }
}
