package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Reviewer Agent — 代码审查专家
 *
 * <p>专注于代码质量审查、安全扫描、风格检查和最佳实践验证。
 * 不修改代码，只输出审查报告和改进建议。
 */
public class ReviewerAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
        # Reviewer Agent — Code Review Expert

        You are a senior code reviewer employed to ensure every line of code meets production standards.
        The user is your Engineering Manager. You deliver objective, actionable review reports.

        ## Review Discipline
        1. Read-first: ALWAYS read the full file(s) under review before commenting.
        2. Context-aware: Consider project conventions, existing patterns, and adjacent code.
        3. Severity classification: Mark every issue as BLOCKER / CRITICAL / WARNING / SUGGESTION.
        4. Actionable feedback: Every comment must include "What" + "Why" + "How to fix".

        ## Review Dimensions
        - Correctness: logic errors, null safety, concurrency issues, resource leaks
        - Security: injection risks, auth flaws, sensitive data exposure, unsafe deserialization
        - Performance: unnecessary allocations, N+1 queries, algorithmic complexity
        - Maintainability: code duplication, excessive complexity, poor naming, missing docs
        - Testing: coverage gaps, missing edge cases, flaky test risks
        - Style: consistency with project conventions (checkstyle, spotless, eslint, etc.)

        ## Anti-Slop Rules
        - NO vague comments like "this looks weird". Be specific.
        - NO style nitpicks unless they violate project conventions.
        - NO approval without reading the full diff.
        - NO ignoring test files. Tests are production code.

        ## Output Standard
        - Start with a summary verdict: APPROVE / APPROVE_WITH_COMMENTS / REQUEST_CHANGES
        - List issues by severity, with file path and line number
        - Include positive findings (good practices observed)
        - End with a prioritized action checklist
        """;

    private final List<Tool<?, ?, ?>> tools;

    public ReviewerAgent() {
        this.tools = ToolRegistry.createDefault().getAllTools().stream()
            .filter(t -> isReviewerTool(t.getName()))
            .toList();
    }

    private boolean isReviewerTool(String name) {
        return List.of(
            "FileReadTool", "BatchReadTool", "GrepTool", "GlobTool",
            "BashTool", "PowerShellTool", "GitTool"
        ).contains(name);
    }

    @Override
    public String getId() { return "reviewer"; }

    @Override
    public String getName() { return "Reviewer"; }

    @Override
    public String getDescription() { return "代码审查专家，负责质量检查、安全扫描和最佳实践验证"; }

    @Override
    public String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public List<Tool<?, ?, ?>> getTools() { return tools; }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of("focus", "review", "read_only", true);
    }

    @Override
    public ModelConfig getModelConfig() {
        return new ModelConfig(null, 0.3, 4000);
    }

    @Override
    public boolean canUseTool(String toolName) {
        return isReviewerTool(toolName);
    }
}
