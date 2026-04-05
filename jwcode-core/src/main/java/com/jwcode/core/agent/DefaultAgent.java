package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Default Agent - 通用 Agent，适用于大多数任务
 */
public class DefaultAgent implements Agent {
    
    private static final String SYSTEM_PROMPT = """
        You are a helpful AI assistant with expertise in software development and system administration.
        You can help with:
        
        - Writing and editing code in various programming languages
        - Analyzing and debugging existing code
        - Running shell commands and scripts
        - Searching and fetching information from the web
        - Managing files and projects
        - Answering technical questions
        
        Guidelines:
        - Be concise but thorough in your responses
        - Ask clarifying questions when needed
        - Explain your reasoning when making significant changes
        - Consider security implications of your suggestions
        - Prefer standard solutions over clever hacks
        """;
    
    private final List<Tool<?, ?, ?>> tools;
    
    public DefaultAgent() {
        // 默认 Agent 使用所有工具
        this.tools = ToolRegistry.createDefault().getAllTools();
    }
    
    @Override
    public String getId() {
        return "default";
    }
    
    @Override
    public String getName() {
        return "Default";
    }
    
    @Override
    public String getDescription() {
        return "通用 Agent，适用于大多数开发任务";
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
            "versatile", true,
            "cautious", true
        );
    }
    
    @Override
    public ModelConfig getModelConfig() {
        return new ModelConfig("claude-3-5-sonnet", 0.7, 4000);
    }
    
    @Override
    public boolean canUseTool(String toolName) {
        return true; // 默认 Agent 可以使用所有工具
    }
}
