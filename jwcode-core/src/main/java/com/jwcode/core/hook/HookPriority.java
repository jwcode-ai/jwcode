package com.jwcode.core.hook;

/**
 * HookPriority — Hook 优先级枚举与冲突裁决规则。
 *
 * <p>5 级优先级，数值越大优先级越高。当多个 Hook 返回不同决策时，
 * 按优先级从高到低串行执行，由 {@code HookConflictResolver} 应用裁决规则。</p>
 *
 * <h3>裁决规则</h3>
 * <ol>
 *   <li><b>DENY/VOID 最高优先</b>：任一 Hook 拒绝即拒绝，不因低优先级 Hook 允许而覆盖</li>
 *   <li><b>MODIFY 链式传递</b>：高优先级先修改输入，低优先级基于修改后的输入再判断</li>
 *   <li><b>ASK 覆盖 ALLOW</b>：如果任一 Hook 需要确认，最终结果即为需要确认</li>
 *   <li><b>DEFER 聚合</b>：所有 DEFER 审批完成后才能继续</li>
 * </ol>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public enum HookPriority {

    /** 系统级（100），内置关键拦截（如 Token 预算保护） */
    SYSTEM(100, "系统级关键拦截"),

    /** 安全级（80），安全审计与合规检查 */
    SECURITY(80, "安全审计与合规"),

    /** 项目级（60），项目级别策略 */
    PROJECT(60, "项目级别策略"),

    /** 用户级（40），用户自定义规则 */
    USER(40, "用户自定义"),

    /** 插件级（20），第三方扩展 */
    PLUGIN(20, "第三方插件");

    private final int level;
    private final String description;

    HookPriority(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 当异常发生时，此优先级的 Hook 是否应采用 fail-closed（拒绝）策略。
     * <p>只有 SECURITY 和 SYSTEM 级别在异常时默认 DENY，其余 ALLOW。</p>
     */
    public boolean isFailClosed() {
        return this == SYSTEM || this == SECURITY;
    }

    /**
     * 比较两个 Hook 的优先级。
     *
     * @return 负数表示 this 优先级更低，正数表示更高
     */
    public int comparePriorityTo(HookPriority other) {
        return Integer.compare(this.level, other.level);
    }

    /**
     * 冲突裁决器 —— 聚合多个 HookResult 为最终决策。
     *
     * <p>冲突裁决规则（v2.1）：</p>
     * <ol>
     *   <li><b>DENY/VOID 最高优先</b> — 任一拒绝即拒绝</li>
     *   <li><b>MODIFY 链式传递</b> — 高优先级先修改，低优先级基于新输入</li>
     *   <li><b>ASK 覆盖 ALLOW</b> — 只要有确认需求，最终就需要确认</li>
     *   <li><b>DEFER 聚合</b> — 等待所有审批完成</li>
     * </ol>
     */
    public static final class ConflictResolver {

        private ConflictResolver() {}

        /**
         * 合并两个 HookResult 的 contextOutput，用换行分隔。
         */
        private static String mergeContextOutput(HookResult a, HookResult b) {
            String outA = a != null && a.hasContextOutput() ? a.getContextOutput() : null;
            String outB = b != null && b.hasContextOutput() ? b.getContextOutput() : null;
            if (outA == null && outB == null) return null;
            if (outA == null) return outB;
            if (outB == null) return outA;
            return outA + "\n---\n" + outB;
        }

        /**
         * 将新的 HookResult 合并到当前累积结果中。
         * <p>contextOutput 在所有 Hook 间累积，不因决策覆盖而丢失。</p>
         *
         * @param accumulated 当前累积结果（可为 null 表示第一个 Hook）
         * @param next        下一个 Hook 的结果
         * @return 合并后的结果
         */
        public static HookResult merge(HookResult accumulated, HookResult next) {
            if (accumulated == null) {
                return next;
            }

            HookDecision accDecision = accumulated.getDecision();
            HookDecision nextDecision = next.getDecision();

            // 规则 1：DENY/VOID 最高优先
            if (nextDecision.isTerminal()) {
                return new HookResult.Builder(next.getDecision(), next.getHookName())
                    .reason(next.getReason())
                    .contextOutput(mergeContextOutput(accumulated, next))
                    .build();
            }
            if (accDecision.isTerminal()) {
                return new HookResult.Builder(accumulated.getDecision(), accumulated.getHookName())
                    .reason(accumulated.getReason())
                    .contextOutput(mergeContextOutput(accumulated, next))
                    .build();
            }

            // 规则 2：MODIFY 链式传递
            if (nextDecision == HookDecision.MODIFY) {
                return new HookResult.Builder(HookDecision.MODIFY, next.getHookName())
                    .modifiedInput(next.getModifiedInput())
                    .reason(next.getReason())
                    .contextOutput(mergeContextOutput(accumulated, next))
                    .build();
            }
            if (accDecision == HookDecision.MODIFY && nextDecision == HookDecision.ALLOW) {
                return new HookResult.Builder(accumulated.getDecision(), accumulated.getHookName())
                    .modifiedInput(accumulated.getModifiedInput())
                    .reason(accumulated.getReason())
                    .contextOutput(mergeContextOutput(accumulated, next))
                    .build();
            }

            // 规则 3：ASK 覆盖 ALLOW
            if (nextDecision == HookDecision.ASK) {
                return new HookResult.Builder(next.getDecision(), next.getHookName())
                    .askPayload(next.getAskPayload())
                    .reason(next.getReason())
                    .contextOutput(mergeContextOutput(accumulated, next))
                    .build();
            }

            // 规则 4：DEFER 聚合
            if (nextDecision == HookDecision.DEFER) {
                return new HookResult.Builder(next.getDecision(), next.getHookName())
                    .deferToken(next.getDeferToken())
                    .reason(next.getReason())
                    .contextOutput(mergeContextOutput(accumulated, next))
                    .build();
            }

            // 默认：保留当前结果，累积 contextOutput
            return new HookResult.Builder(accumulated.getDecision(), accumulated.getHookName())
                .reason(accumulated.getReason())
                .modifiedInput(accumulated.getModifiedInput())
                .askPayload(accumulated.getAskPayload())
                .deferToken(accumulated.getDeferToken())
                .contextOutput(mergeContextOutput(accumulated, next))
                .build();
        }

        /**
         * 计算最终的 HookResult。
         * 如果没有任何 Hook 匹配，返回默认 ALLOW。
         */
        public static HookResult resolve(java.util.List<HookResult> results) {
            if (results == null || results.isEmpty()) {
                return HookResult.allow("default", "No hooks matched");
            }

            HookResult finalResult = null;
            for (HookResult result : results) {
                finalResult = merge(finalResult, result);
                // 短路：遇到终止性决策不再继续
                if (finalResult != null && finalResult.getDecision().isTerminal()) {
                    break;
                }
            }

            return finalResult != null ? finalResult
                : HookResult.allow("default", "All hooks allowed");
        }
    }
}
