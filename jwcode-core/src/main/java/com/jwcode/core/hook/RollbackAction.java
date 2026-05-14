package com.jwcode.core.hook;

/**
 * RollbackAction — 回退策略枚举。
 *
 * <p>当 Hook 返回 {@link HookDecision#VOID} 或工具执行失败触发回退时，
 * 使用此枚举指定回退动作。</p>
 *
 * <h3>与 RetryOrchestrator 的关系</h3>
 * <p>
 * {@code RetryOrchestrator} 处理同一步骤内的重试（指数退避，3 次自修复），
 * {@code RollbackOrchestrator} 处理跨步骤/跨状态的回退（跳过、回退检查点、终止）。
 * 两者互补，不重叠。
 * </p>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public enum RollbackAction {

    /**
     * 重试当前步骤。
     * <p>适用场景：瞬态错误，短暂等待后重试可能成功。</p>
     */
    RETRY("重试当前步骤"),

    /**
     * 跳过当前步骤，继续执行后续步骤。
     * <p>适用场景：非关键步骤失败，可降级执行。</p>
     */
    SKIP("跳过当前步骤"),

    /**
     * 回退到最近检查点并重新执行。
     * <p>适用场景：状态不一致，需要从已知良好状态恢复。</p>
     */
    ROLLBACK_TO_CHECKPOINT("回退到最近检查点"),

    /**
     * 终止当前任务。
     * <p>适用场景：关键路径失败，无法继续。</p>
     */
    ABORT_TASK("终止当前任务"),

    /**
     * 请求人工介入。
     * <p>适用场景：无法自动处理的异常，需要人工决策。</p>
     */
    REQUEST_HUMAN("请求人工介入");

    private final String description;

    RollbackAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否为破坏性操作（会使当前进度丢失）。
     */
    public boolean isDestructive() {
        return this == ROLLBACK_TO_CHECKPOINT || this == ABORT_TASK;
    }

    /**
     * 是否需要人工介入。
     */
    public boolean requiresHuman() {
        return this == REQUEST_HUMAN;
    }
}
