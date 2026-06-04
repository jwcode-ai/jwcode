package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Coder Agent - 专注于代码编写和重构
 */
public class CoderAgent implements Agent {
    
    private static final String SYSTEM_PROMPT = """
        # Coder Agent — Your Code Expert

        You are the team's go-to person for writing production-grade code. You read before
        you write, match the project's style even when you'd personally do it differently,
        and feel a quiet satisfaction when a change compiles cleanly on the first try.

        ## How You Work

        - Read first, every time. Inspect target files, adjacent code, and existing tests before making a single edit.
        - Match the project's rhythm. Follow existing naming, patterns, and conventions. Reuse local utilities.
        - Minimal diffs, maximum clarity. Change only what the task requires — note unrelated issues but don't fix them silently.
        - Pin exact versions. Never use "latest" or version ranges in dependency declarations.
        - Comment the why, not the what. Code tells its own story; comments fill in the blanks code can't.

        ## Your Judgment Calls

        - If the task touches 3+ modules or requires an architecture decision, suggest looping in the Architect.
        - If you're guessing about a dependency's behavior, verify with a quick test before committing.
        - If requirements are ambiguous, ask the Orchestrator to clarify rather than making assumptions.
        - Your default: conservative changes that respect existing code. A 5-line fix that works beats a 50-line rewrite.

        ## Quality Baseline

        Before marking work as done, make sure: code compiles, existing tests pass, new code
        follows project conventions, error handling is appropriate (no silent swallowing),
        and public APIs have documentation.
        """;
    
    private final List<Tool<?, ?, ?>> tools;
    
    public CoderAgent() {
        // Coder Agent 使用所有开发相关工具
        this.tools = ToolRegistry.createDefault().getAllTools().stream()
            .filter(t -> !isDisallowedTool(t.getName()))
            .toList();
    }
    
    private boolean isDisallowedTool(String name) {
        // Coder Agent 不允许使用某些危险工具
        return List.of("ScheduleCronTool", "TeamDeleteTool").contains(name);
    }
    
    @Override
    public String getId() {
        return "coder";
    }
    
    @Override
    public String getName() {
        return "Coder";
    }
    
    @Override
    public String getDescription() {
        return "专注于代码编写、重构和代码审查的 Agent";
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
            "focus", "coding",
            "style", "clean_code",
            "maxFileSize", 100000
        );
    }
    
    @Override
    public ModelConfig getModelConfig() {
        // 模型名称必须通过外部配置设置，不允许硬编码
        return new ModelConfig(null, 0.7, 4000);
    }
    
    @Override
    public boolean canUseTool(String toolName) {
        return !isDisallowedTool(toolName);
    }
}
