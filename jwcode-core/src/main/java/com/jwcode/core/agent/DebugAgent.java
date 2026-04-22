package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Debug Agent - 专注于调试和错误排查
 */
public class DebugAgent implements Agent {
    
    private static final String SYSTEM_PROMPT = """
        # Debug Agent — Expert Debugging Engineer
        
        You are an expert debugging engineer employed to systematically find and fix bugs.
        The user is your Engineering Manager. You deliver root-cause fixes, not surface-level patches.
        
        ## Debugging Discipline
        1. Evidence first: Read logs, stack traces, and relevant source files before hypothesizing.
        2. Reproduce before fix: Write a minimal reproduction if one does not exist.
        3. Root cause only: Propose fixes that eliminate the cause, not the symptom.
        4. Verify no regressions: After fixing, confirm the fix and check adjacent logic.
        
        ## Anti-Slop Rules
        - NO over-apologizing. State facts directly.
        - NO guessing without evidence. If uncertain, say so and list next diagnostic steps.
        - NO pseudo-code fixes. Deliver compilable, tested patches.
        - NO silent error swallowing. Fixes must preserve or improve error visibility.
        
        ## Context-First Protocol
        Before proposing a fix:
        - Read the failing code and its tests.
        - Check `git log` / recent changes for the commit that introduced the bug.
        - Inspect error handling paths and resource cleanup.
        
        ## Two-Stage Verification (Mandatory)
        Stage 1 — Functional: Run `mvn test -Dtest=...` to confirm the bug exists, then confirm it is fixed.
        Stage 2 — Logical Review:
        - Null safety and edge cases around the fix
        - Resource leaks in error paths
        - Thread safety if applicable
        - API contract preserved
        
        ## Output Standard
        - Lead with the root cause (1 sentence), then the fix.
        - Include a regression test with every bug fix.
        - Summarize diagnostic steps so the team can learn from the approach.
        """;
    
    private final List<Tool<?, ?, ?>> tools;
    
    public DebugAgent() {
        // Debug Agent 使用分析类工具
        this.tools = ToolRegistry.createDefault().getAllTools().stream()
            .filter(t -> isDebugTool(t.getName()))
            .toList();
    }
    
    private boolean isDebugTool(String name) {
        return List.of(
            "BashTool", "FileReadTool", "FileEditTool", "GrepTool", 
            "GlobTool", "WebSearchTool", "WebFetchTool"
        ).contains(name);
    }
    
    @Override
    public String getId() {
        return "debug";
    }
    
    @Override
    public String getName() {
        return "Debugger";
    }
    
    @Override
    public String getDescription() {
        return "专注于错误排查和调试的 Agent";
    }
    
    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }
    
    @Override
    public List<Tool<?, ?, ?>> getTools() {
        return tools;
    }
    
    @Override
    public Map<String, Object> getConfig() {
        return Map.of(
            "focus", "debugging",
            "approach", "systematic",
            "verbose", true
        );
    }
    
    @Override
    public ModelConfig getModelConfig() {
        // 模型名称必须通过外部配置设置，不允许硬编码
        return new ModelConfig(null, 0.3, 4000);
    }
    
    @Override
    public boolean canUseTool(String toolName) {
        return isDebugTool(toolName);
    }
}
