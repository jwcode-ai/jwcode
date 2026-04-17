package com.jwcode.core.checker;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 依赖状态枚举
 */
public enum DependencyStatus {
    AVAILABLE("可用"),
    UNAVAILABLE("不可用"),
    CHECKING("检测中"),
    SKIPPED("已跳过");

    private final String description;

    DependencyStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
