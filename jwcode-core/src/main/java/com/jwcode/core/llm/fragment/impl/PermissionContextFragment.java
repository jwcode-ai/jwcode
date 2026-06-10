package com.jwcode.core.llm.fragment.impl;

import com.jwcode.core.llm.fragment.ContextualFragment;
import com.jwcode.core.llm.fragment.FragmentCategory;
import com.jwcode.core.llm.fragment.FragmentContext;
import com.jwcode.core.policy.ExecPolicyEngine;
import com.jwcode.core.policy.PolicyRule;

import java.util.stream.Collectors;

/**
 * 权限上下文片段 — 注入当前策略规则的摘要。
 *
 * <p>让 AI 知晓哪些命令需要审批，哪些被禁止，避免尝试执行已知会被拒绝的命令。
 */
public class PermissionContextFragment implements ContextualFragment {

    @Override
    public String getId() {
        return "permission-context";
    }

    @Override
    public FragmentCategory getCategory() {
        return FragmentCategory.POLICY;
    }

    @Override
    public boolean isEnabledByDefault() {
        return false; // 默认关闭，策略决策在执行时由 ExecPolicyEngine 处理
    }

    @Override
    public String build(FragmentContext ctx) {
        var rules = ExecPolicyEngine.getInstance().getActiveRules();
        if (rules.isEmpty()) return null;

        String denyRules = rules.stream()
            .filter(r -> r.action() == PolicyRule.Action.DENY)
            .map(r -> "- " + r.description() + " (匹配: " + r.commandPrefix() + ")")
            .collect(Collectors.joining("\n"));

        String askRules = rules.stream()
            .filter(r -> r.action() == PolicyRule.Action.ASK)
            .map(r -> "- " + r.description() + " (匹配: " + r.commandPrefix() + ")")
            .collect(Collectors.joining("\n"));

        StringBuilder sb = new StringBuilder();
        sb.append("## 执行策略摘要\n\n");

        if (!denyRules.isEmpty()) {
            sb.append("### 禁止的命令\n").append(denyRules).append("\n\n");
        }
        if (!askRules.isEmpty()) {
            sb.append("### 需要审批的命令\n").append(askRules).append("\n\n");
        }

        sb.append("注意：尝试执行被禁止的命令会立即失败，需要审批的命令会触发审批流程。\n");
        return sb.toString();
    }
}
