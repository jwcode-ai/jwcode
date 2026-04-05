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
        You are an expert software engineer with deep knowledge of multiple programming languages,
        frameworks, and best practices. Your role is to:
        
        1. Write clean, efficient, and well-documented code
        2. Refactor existing code to improve readability and performance
        3. Review code for potential bugs and security issues
        4. Follow language-specific conventions and best practices
        5. Explain your code changes clearly
        
        When writing code:
        - Use meaningful variable and function names
        - Add appropriate comments for complex logic
        - Handle edge cases and errors gracefully
        - Follow SOLID principles where applicable
        - Write modular and testable code
        
        Always prefer simple solutions over complex ones unless there's a clear performance benefit.
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
        return new ModelConfig("claude-3-5-sonnet", 0.7, 4000);
    }
    
    @Override
    public boolean canUseTool(String toolName) {
        return !isDisallowedTool(toolName);
    }
}
