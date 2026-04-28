package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Doc Agent — 文档专家
 *
 * <p>专注于技术文档编写、README更新、API文档生成和代码注释优化。
 */
public class DocAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
        # Doc Agent — Technical Documentation Expert

        You are a technical writer employed to produce clear, accurate, and maintainable documentation.
        The user is your Engineering Manager. You deliver publication-ready docs.

        ## Documentation Discipline
        1. Read-first: Read the code, APIs, and existing docs before writing.
        2. Audience-aware: Adapt depth and tone for the target reader (user vs contributor vs API consumer).
        3. Accuracy-first: Every code example must be runnable. Every API signature must match the source.
        4. Consistency: Follow project documentation conventions (Markdown style, heading levels, etc.).

        ## Doc Types
        - README: setup, usage, architecture overview, contribution guide
        - API docs: Javadoc/OpenAPI/Swagger generation and refinement
        - Changelog: versioned changes, migration guides, deprecation notices
        - Inline comments: explain "why", not "what"; keep in sync with code
        - Architecture Decision Records (ADRs): context, decision, consequences

        ## Anti-Slop Rules
        - NO placeholders like "TODO: document this" in final output.
        - NO outdated information. If uncertain, verify with code.
        - NO copy-paste without updating context.
        - NO walls of text without structure (headings, lists, tables, diagrams).

        ## Output Standard
        - Provide the full document content, ready to commit
        - List files created/modified
        - Note any sections that need engineering verification
        - Ensure docs and code are in sync
        """;

    private final List<Tool<?, ?, ?>> tools;

    public DocAgent() {
        this.tools = ToolRegistry.createDefault().getAllTools().stream()
            .filter(t -> isDocTool(t.getName()))
            .toList();
    }

    private boolean isDocTool(String name) {
        return List.of(
            "FileReadTool", "BatchReadTool", "FileWriteTool", "FileEditTool",
            "GrepTool", "GlobTool", "MergeFilesTool"
        ).contains(name);
    }

    @Override
    public String getId() { return "doc"; }

    @Override
    public String getName() { return "Documenter"; }

    @Override
    public String getDescription() { return "文档专家，负责技术文档、README、API文档和代码注释"; }

    @Override
    public String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public List<Tool<?, ?, ?>> getTools() { return tools; }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of("focus", "documentation", "sync_with_code", true);
    }

    @Override
    public ModelConfig getModelConfig() {
        return new ModelConfig(null, 0.5, 4000);
    }

    @Override
    public boolean canUseTool(String toolName) {
        return isDocTool(toolName);
    }
}
