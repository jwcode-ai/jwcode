package com.jwcode.core.policy;

/**
 * 策略决策结果。
 */
public record PolicyDecision(
    boolean isAllowed,
    PolicyRule.Action action,
    String matchedRuleId,
    String reason,
    String suggestedAlternative,
    boolean requiresApproval
) {
    /** 任何规则都未匹配时的默认放行 */
    public static PolicyDecision allowed() {
        return new PolicyDecision(true, PolicyRule.Action.ALLOW, null, "no matching rule", null, false);
    }

    /** 匹配到 ALLOW 规则 */
    public static PolicyDecision allowedByRule(String ruleId, String reason) {
        return new PolicyDecision(true, PolicyRule.Action.ALLOW, ruleId, reason, null, false);
    }

    /** 匹配到 DENY 规则 */
    public static PolicyDecision deniedByRule(String ruleId, String reason, String alternative) {
        return new PolicyDecision(false, PolicyRule.Action.DENY, ruleId, reason, alternative, false);
    }

    /** 匹配到 ASK 规则 — 需要用户审批 */
    public static PolicyDecision needsApproval(String ruleId, String reason) {
        return new PolicyDecision(false, PolicyRule.Action.ASK, ruleId, reason, null, true);
    }

    /** 匹配到 DELEGATE 规则 */
    public static PolicyDecision delegated(String ruleId, String reason) {
        return new PolicyDecision(true, PolicyRule.Action.DELEGATE, ruleId, reason, null, false);
    }

    /** 命令包含注入特征时的拒绝 */
    public static PolicyDecision deniedByInjection(String description) {
        return new PolicyDecision(false, PolicyRule.Action.DENY, "injection-detector", description, null, false);
    }
}
