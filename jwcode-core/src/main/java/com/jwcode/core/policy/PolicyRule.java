package com.jwcode.core.policy;

import java.util.Set;

/**
 * 执行策略规则 — 定义命令匹配条件和对应的执行决策。
 *
 * <p>对标 Codex execpolicy crate 的 PrefixRule / NetworkRule，
 * 支持前缀匹配、正则匹配、以及网络访问控制。</p>
 */
public record PolicyRule(
    String id,
    String description,
    int priority,
    String commandPrefix,
    boolean isRegex,
    Action action,
    Set<String> allowedDomains,
    Set<String> allowedProtocols,
    String suggestedAlternative
) {
    public enum Action {
        ALLOW,
        DENY,
        ASK,
        DELEGATE
    }

    public PolicyRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (allowedDomains == null) {
            allowedDomains = Set.of();
        }
        if (allowedProtocols == null) {
            allowedProtocols = Set.of();
        }
    }

    /** 创建允许规则 */
    public static PolicyRule allow(String id, String prefix, int priority, String description) {
        return new PolicyRule(id, description, priority, prefix, false, Action.ALLOW, null, null, null);
    }

    /** 创建拒绝规则 */
    public static PolicyRule deny(String id, String prefix, int priority, String description, String alternative) {
        return new PolicyRule(id, description, priority, prefix, false, Action.DENY, null, null, alternative);
    }

    /** 创建需审批规则 */
    public static PolicyRule ask(String id, String prefix, int priority, String description) {
        return new PolicyRule(id, description, priority, prefix, false, Action.ASK, null, null, null);
    }

    /** 创建正则拒绝规则 */
    public static PolicyRule denyRegex(String id, String regex, int priority, String description, String alternative) {
        return new PolicyRule(id, description, priority, regex, true, Action.DENY, null, null, alternative);
    }
}
