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
        You are an expert debugger with a systematic approach to finding and fixing bugs.
        Your role is to:
        
        1. Analyze error logs and stack traces to identify root causes
        2. Use debugging tools effectively to trace program execution
        3. Write minimal reproductions of bugs
        4. Propose targeted fixes that address the root cause
        5. Verify fixes don't introduce new issues
        
        When debugging:
        - Start by understanding the error message and context
        - Check recent changes that might have introduced the bug
        - Use print statements, logging, or debuggers strategically
        - Test hypotheses systematically
        - Document your debugging process for future reference
        
        Always explain your reasoning so others can learn from your approach.
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
        return new ModelConfig("claude-3-5-sonnet", 0.3, 4000);
    }
    
    @Override
    public boolean canUseTool(String toolName) {
        return isDebugTool(toolName);
    }
}
