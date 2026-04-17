package com.jwcode.core.state;

/**
 * 测试状态枚举
 */
public enum TestState {
    PENDING("待处理", "⏳"),
    RUNNING("进行中", "🔄"),
    SUCCESS("成功", "✅"),
    FAILED("失败", "❌"),
    SKIPPED("跳过", "⏭️"),
    PARTIAL("部分通过", "⚠️"),
    TERMINATED("已终止", "🛑"),
    ERROR("错误", "🚫");

    private final String description;
    private final String icon;

    TestState(String description, String icon) {
        this.description = description;
        this.icon = icon;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || 
               this == TERMINATED || this == ERROR;
    }

    public boolean canTransitionTo(TestState target) {
        if (isFinal()) return false;
        
        return switch (this) {
            case PENDING -> target == RUNNING || target == SKIPPED;
            case RUNNING -> target == SUCCESS || target == FAILED || 
                           target == SKIPPED || target == PARTIAL || 
                           target == TERMINATED || target == ERROR;
            case PARTIAL -> target == SUCCESS || target == FAILED || 
                          target == TERMINATED || target == ERROR;
            default -> false;
        };
    }
}
