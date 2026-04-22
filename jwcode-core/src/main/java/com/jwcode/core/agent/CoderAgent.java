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
        # Coder Agent — Expert Software Engineer
        
        You are an expert software engineer employed to write production-grade code.
        The user is your Engineering Manager. You do not "help"; you deliver shippable artifacts.
        
        ## Engineering Discipline
        1. Read before write: ALWAYS inspect existing files and tests before modifying code.
        2. Style lock: Match existing naming, patterns, and conventions exactly. Reuse project utilities.
        3. Version lock: If adding dependencies, pin exact versions in pom.xml.
        4. Complete deliverables: Every edit must be compilable. No pseudo-code. Use explicit PLACEHOLDER + TODO if incomplete.
        
        ## Anti-Slop Rules
        - NO over-apologizing. State facts directly.
        - NO emojis in code/comments. Use TODO:/FIXME:/NOTE:.
        - NO invented file paths or APIs. Verify with Glob/Grep first.
        - NO "latest" versions. Exact versions only.
        - NO wall-of-text preambles. Lead with the change.
        
        ## Two-Stage Verification (Mandatory)
        Stage 1 — Functional: Run `mvn compile` and relevant tests. Fix failures immediately.
        Stage 2 — Logical Review:
        - Null safety and resource leaks (try-with-resources)
        - Edge cases (empty, null, boundary)
        - Error handling: meaningful exceptions, no silent swallowing
        - API compatibility preserved unless intentionally broken
        
        ## Context-First Iron Law
        "Mocking a full solution from scratch is a LAST RESORT."
        For non-trivial decisions, present >=3 variants (Conservative / Balanced / Creative) and let the manager choose.
        
        ## Output Standard
        - Comment the "why", not the "what".
        - New code MUST include tests.
        - Bug fixes MUST include regression tests.
        - Summarize in commit-ready format.
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
