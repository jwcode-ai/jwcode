package com.jwcode.core.llm.fragment.impl;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.aicl.AICLPromptBuilder;
import com.jwcode.core.llm.fragment.ContextualFragment;
import com.jwcode.core.llm.fragment.FragmentCategory;
import com.jwcode.core.llm.fragment.FragmentContext;
import com.jwcode.core.tool.Tool;

import java.util.List;

/**
 * Agent 角色声明片段 — 注入当前 Agent 的系统提示词、可用/禁止工具列表。
 *
 * <p>重构自 LLMQueryEngine.injectAgentSystemPrompt()。
 */
public class AgentRoleFragment implements ContextualFragment {

    @Override
    public String getId() {
        return "agent-role";
    }

    @Override
    public FragmentCategory getCategory() {
        return FragmentCategory.SYSTEM_IDENTITY;
    }

    @Override
    public String getDedupMarker() {
        return "[AGENT_ROLE:";
    }

    @Override
    public String build(FragmentContext ctx) {
        Agent agent = ctx.getAgent();
        if (agent == null) return null;

        StringBuilder prompt = new StringBuilder();
        prompt.append("[AGENT_ROLE:").append(agent.getId()).append("]\n");
        prompt.append("# 当前角色：").append(agent.getName()).append("\n\n");
        prompt.append(agent.getSystemPrompt()).append("\n\n");

        // AICL 上下文解析规则
        prompt.append(AICLPromptBuilder.buildCompactPrompt()).append("\n\n");

        // 可用工具列表
        var toolExecutor = ctx.getToolExecutor();
        if (toolExecutor != null) {
            List<Tool<?, ?, ?>> allowedTools = toolExecutor.getEnabledTools().stream()
                .filter(t -> agent.canUseTool(t.getName()))
                .toList();
            if (!allowedTools.isEmpty()) {
                prompt.append("## 你当前可用的工具（仅限以下工具）\n\n");
                for (Tool<?, ?, ?> t : allowedTools) {
                    prompt.append("- ").append(t.getName()).append(": ")
                        .append(t.getDescription()).append("\n");
                }
                prompt.append("\n");
            }

            List<Tool<?, ?, ?>> disallowedTools = toolExecutor.getEnabledTools().stream()
                .filter(t -> !agent.canUseTool(t.getName()))
                .toList();
            if (!disallowedTools.isEmpty()) {
                prompt.append("## 你【禁止】使用的工具（必须通过 AgentTool 指派给子Agent）\n\n");
                for (Tool<?, ?, ?> t : disallowedTools) {
                    prompt.append("- ").append(t.getName()).append("\n");
                }
                prompt.append("\n如果你需要执行上述禁止工具的工作，请使用 AgentTool 创建对应角色的子Agent来完成。\n\n");
            }
        }

        return prompt.toString();
    }
}
