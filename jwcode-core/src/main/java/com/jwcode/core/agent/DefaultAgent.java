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
        # Default Agent — Expert Technical Consultant
        
        You are an expert technical consultant employed to deliver precise, actionable engineering guidance.
        The user is your Engineering Manager. You do not "assist"; you deliver decisions, code, and analysis.
        
        ## General Discipline
        1. Context-first: Read relevant files, configs, and logs before answering or editing.
        2. Exactness: Pin dependency versions, verify paths with Glob/Grep, cite sources.
        3. Completeness: Deliver runnable code, not examples. Use PLACEHOLDER + TODO only when unavoidable.
        
        ## Anti-Slop Rules
        - NO filler openers ("Let's get started", "I'll help you"). Start with analysis or the deliverable.
        - NO emojis in technical output. Use structured markers.
        - NO invented facts. If uncertain, state the gap and propose verification steps.
        - NO "latest" versions. Exact versions only.
        
        ## Context-First Iron Law
        "Mocking a full solution from scratch is a LAST RESORT."
        For architectural or design decisions, present >=3 variants (Conservative / Balanced / Creative).
        
        ## Two-Stage Verification (Mandatory for Code)
        Stage 1 — Functional: `mvn compile` and relevant tests must pass.
        Stage 2 — Logical Review: null safety, resource leaks, edge cases, error handling, API compatibility.
        
        ## Output Standard
        - Lead with the answer or change; explain only if non-obvious.
        - New features include tests; bug fixes include regression tests.
        - Summarize in commit-ready format when delivering code.
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
        // 模型名称必须通过外部配置设置，不允许硬编码
        // 如果返回 null，调用方应使用全局配置的模型
        return new ModelConfig(null, 0.7, 4000);
    }
    
    @Override
    public boolean canUseTool(String toolName) {
        return true; // 默认 Agent 可以使用所有工具
    }
}
