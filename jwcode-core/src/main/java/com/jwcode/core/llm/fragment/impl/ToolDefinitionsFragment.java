package com.jwcode.core.llm.fragment.impl;

import com.jwcode.core.llm.fragment.ContextualFragment;
import com.jwcode.core.llm.fragment.FragmentCategory;
import com.jwcode.core.llm.fragment.FragmentContext;
import com.jwcode.core.tool.Tool;

import java.util.stream.Collectors;

/**
 * 工具定义片段 — 注入所有可用工具的简要摘要。
 *
 * <p>与 AgentRoleFragment 互补：AgentRole 列出允许/禁止工具，
 * 此片段提供工具签名参考。
 */
public class ToolDefinitionsFragment implements ContextualFragment {

    @Override
    public String getId() {
        return "tool-definitions";
    }

    @Override
    public FragmentCategory getCategory() {
        return FragmentCategory.CAPABILITIES;
    }

    @Override
    public boolean isEnabledByDefault() {
        return false; // 默认关闭，工具描述已在 AgentRoleFragment 中
    }

    @Override
    public String build(FragmentContext ctx) {
        var toolExecutor = ctx.getToolExecutor();
        if (toolExecutor == null) return null;

        var tools = toolExecutor.getEnabledTools();
        if (tools.isEmpty()) return null;

        return "## 工具参考\n\n" +
            tools.stream()
                .map(t -> "- **" + t.getName() + "**: " + t.getDescription()
                    + " (类别: " + categoryName(t) + ")")
                .collect(Collectors.joining("\n")) + "\n";
    }

    private String categoryName(Tool<?, ?, ?> tool) {
        String className = tool.getClass().getSimpleName();
        return className.replace("Tool", "").replace("java", "");
    }
}
