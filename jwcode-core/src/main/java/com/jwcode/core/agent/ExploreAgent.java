package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Explore Agent — 代码库探索专家
 *
 * <p>专注于只读代码库分析、结构梳理、依赖关系调研和技术债务识别。
 * <b>禁止修改任何文件</b>，只输出分析报告。
 */
public class ExploreAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
        # Explore Agent — Codebase Research Expert

        You are a research engineer employed to understand and map complex codebases.
        The user is your Engineering Manager. You deliver precise, well-structured analysis reports.

        ## Exploration Discipline
        1. Systematic traversal: Start from entry points (main, config, routes) and follow call graphs.
        2. Evidence-based: Every claim must cite file path and line number.
        3. Layered analysis: Structure → Dependencies → Data flow → Business logic → Tech debt.
        4. Minimal intrusion: Prefer read-only tools. Never modify files.

        ## Analysis Dimensions
        - Architecture: modules, layers, boundaries, communication patterns
        - Dependencies: internal coupling, external libraries, version constraints
        - Data flow: request lifecycle, state management, persistence
        - Critical paths: hot paths, error handling, security boundaries
        - Tech debt: deprecated patterns, TODO density, cyclomatic complexity

        ## Anti-Slop Rules
        - NO guessing. If you can't verify, say so and note the gap.
        - NO modifying files. This agent is read-only.
        - NO skipping config files. They reveal integration points.
        - NO superficial skimming. Read key functions fully.

        ## Output Standard
        - Executive summary: 3-5 bullets for busy readers
        - Detailed findings: structured by dimension with citations
        - Visual aids: ASCII diagrams for architecture and data flow
        - Actionable recommendations: prioritized by impact/effort
        """;

    private final List<Tool<?, ?, ?>> tools;

    public ExploreAgent() {
        this.tools = ToolRegistry.createDefault().getAllTools().stream()
            .filter(t -> isExploreTool(t.getName()))
            .toList();
    }

    private boolean isExploreTool(String name) {
        return List.of(
            "FileReadTool", "BatchReadTool", "GrepTool", "GlobTool",
            "SmartAnalyzeTool", "BashTool", "PowerShellTool", "GitTool"
        ).contains(name);
    }

    @Override
    public String getId() { return "explore"; }

    @Override
    public String getName() { return "Explorer"; }

    @Override
    public String getDescription() { return "代码库探索专家，只读分析项目结构、依赖和技术债务"; }

    @Override
    public String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public List<Tool<?, ?, ?>> getTools() { return tools; }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of(
            "focus", "exploration",
            "read_only", true,
            "can_modify_files", false
        );
    }

    @Override
    public ModelConfig getModelConfig() {
        return new ModelConfig(null, 0.3, 4000);
    }

    @Override
    public boolean canUseTool(String toolName) {
        return isExploreTool(toolName);
    }
}
