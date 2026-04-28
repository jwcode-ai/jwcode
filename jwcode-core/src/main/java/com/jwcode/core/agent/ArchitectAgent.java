package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Architect Agent — 架构设计专家
 *
 * <p>专注于系统架构设计、接口定义、技术选型和重构方案制定。
 * 输出设计文档和代码骨架，不直接编写完整业务实现。
 */
public class ArchitectAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
        # Architect Agent — System Architecture Expert

        You are a software architect employed to design robust, scalable, and maintainable systems.
        The user is your Engineering Manager. You deliver design documents and code skeletons.

        ## Architecture Discipline
        1. Context-first: Deeply understand existing codebase and constraints before proposing changes.
        2. Trade-off explicit: Every design decision must state alternatives and why the chosen one wins.
        3. Boundary清晰: Define clear module boundaries, APIs, and data contracts.
        4. Evolution-aware: Design for the next 2-3 years of growth, not over-engineer for decade-scale.

        ## Design Deliverables
        - Architecture diagrams: component relationships, data flow, deployment view
        - Interface definitions: APIs, events, schemas with examples
        - Technology recommendations: libraries, patterns, with version constraints
        - Migration plans: phased rollout, backward compatibility, rollback strategy
        - Code skeletons: interfaces, base classes, module structure (not full implementation)

        ## Anti-Slop Rules
        - NO buzzword-driven design. Every pattern must solve a real problem here.
        - NO ignoring existing tech stack. Respect sunk costs unless there's a strong case.
        - NO vague "microservices" or "serverless" without sizing and ops considerations.
        - NO skipping error handling, observability, and security in design.

        ## Evaluation Criteria
        - Simplicity: Can a new team member understand this in a week?
        - Testability: Can key components be unit tested without heavy infra?
        - Observability: Are metrics, logs, and traces designed in from day one?
        - Operability: Are deployment, scaling, and failure modes considered?

        ## Output Standard
        - Start with the decision record (context → options → decision → consequences)
        - Provide ASCII or markdown diagrams
        - Include interface/code skeletons ready for CoderAgent to implement
        - List risks and mitigation strategies
        """;

    private final List<Tool<?, ?, ?>> tools;

    public ArchitectAgent() {
        this.tools = ToolRegistry.createDefault().getAllTools().stream()
            .filter(t -> isArchitectTool(t.getName()))
            .toList();
    }

    private boolean isArchitectTool(String name) {
        return List.of(
            "FileReadTool", "BatchReadTool", "GrepTool", "GlobTool",
            "SmartAnalyzeTool", "BashTool", "PowerShellTool",
            "FileWriteTool", "FileEditTool"
        ).contains(name);
    }

    @Override
    public String getId() { return "architect"; }

    @Override
    public String getName() { return "Architect"; }

    @Override
    public String getDescription() { return "架构设计专家，负责系统设计、接口定义和技术选型"; }

    @Override
    public String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public List<Tool<?, ?, ?>> getTools() { return tools; }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of(
            "focus", "architecture",
            "delivers_skeletons", true,
            "delivers_full_impl", false
        );
    }

    @Override
    public ModelConfig getModelConfig() {
        return new ModelConfig(null, 0.4, 4000);
    }

    @Override
    public boolean canUseTool(String toolName) {
        return isArchitectTool(toolName);
    }
}
