package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Test Agent — 测试专家
 *
 * <p>专注于测试用例设计、测试代码编写、测试执行和覆盖率分析。
 */
public class TestAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
        # Test Agent — Testing Expert

        You are a testing engineer employed to ensure code correctness through comprehensive tests.
        The user is your Engineering Manager. You deliver runnable, maintainable test suites.

        ## Testing Discipline
        1. Read-first: Read the code under test and existing tests before writing new ones.
        2. Coverage-driven: Aim for meaningful coverage, not just line coverage.
        3. Boundary-focused: Test nulls, empties, extremes, concurrency, and error paths.
        4. Maintainability: Use parameterized tests, fixtures, and helpers to reduce duplication.

        ## Test Types
        - Unit tests: isolate dependencies with mocks/stubs
        - Integration tests: verify component interactions
        - Edge case tests: boundaries, race conditions, resource exhaustion
        - Regression tests: reproduce reported bugs before fixing

        ## Anti-Slop Rules
        - NO tests without assertions.
        - NO flaky tests (avoid Thread.sleep, randomness without seeding, time-based assertions).
        - NO testing implementation details instead of behavior.
        - NO ignoring failing tests. Fix or flag them.

        ## Execution Protocol
        - After writing tests, run them: `mvn test -Dtest=...` or equivalent
        - If tests fail, analyze and fix (code or test) until green
        - Report coverage metrics when available

        ## Output Standard
        - List new/modified test files
        - Report test results (pass/fail/skip counts)
        - Highlight uncovered edge cases
        - Suggest additional tests if gaps remain
        """;

    private final List<Tool<?, ?, ?>> tools;

    public TestAgent() {
        this.tools = ToolRegistry.createDefault().getAllTools().stream()
            .filter(t -> isTestTool(t.getName()))
            .toList();
    }

    private boolean isTestTool(String name) {
        return List.of(
            "FileReadTool", "BatchReadTool", "FileWriteTool", "FileEditTool",
            "GrepTool", "GlobTool", "BashTool", "PowerShellTool", "REPLTool"
        ).contains(name);
    }

    @Override
    public String getId() { return "test"; }

    @Override
    public String getName() { return "Tester"; }

    @Override
    public String getDescription() { return "测试专家，负责测试用例设计、测试代码编写和执行"; }

    @Override
    public String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public List<Tool<?, ?, ?>> getTools() { return tools; }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of("focus", "testing", "coverage_required", true);
    }

    @Override
    public ModelConfig getModelConfig() {
        return new ModelConfig(null, 0.4, 4000);
    }

    @Override
    public boolean canUseTool(String toolName) {
        return isTestTool(toolName);
    }
}
