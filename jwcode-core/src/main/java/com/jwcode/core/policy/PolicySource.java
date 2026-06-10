package com.jwcode.core.policy;

/**
 * 策略来源 — 区分不同来源的策略以便合并和清理。
 */
public enum PolicySource {
    BUILTIN,   // 代码内嵌的安全底线
    SYSTEM,    // 系统级策略 (/etc/jwcode/policy/)
    USER,      // 用户级策略 (~/.jwcode/policy/)
    PROJECT    // 项目级策略 (./.jwcode/policy/)
}
