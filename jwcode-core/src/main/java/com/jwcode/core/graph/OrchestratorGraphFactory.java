package com.jwcode.core.graph;

import com.jwcode.core.a2a.A2AFacade;
import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.checkpoint.CheckpointManager;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory that builds pre-defined {@link AgentGraph} instances for common
 * orchestration workflows and wires them to the existing A2A execution
 * infrastructure.
 *
 * <p>Each workflow is a DAG of agent nodes connected by edges. The factory
 * compiles graphs and injects an {@link CompiledAgentGraph.AgentExecutor}
 * that delegates to {@link A2AFacade} for actual agent dispatch.</p>
 */
public class OrchestratorGraphFactory {

    private static final Logger logger = Logger.getLogger(OrchestratorGraphFactory.class.getName());

    private final AgentRegistry agentRegistry;
    private final A2AFacade a2aFacade;
    private final CheckpointManager checkpointManager;

    public OrchestratorGraphFactory(AgentRegistry agentRegistry,
                                     A2AFacade a2aFacade,
                                     CheckpointManager checkpointManager) {
        this.agentRegistry = agentRegistry;
        this.a2aFacade = a2aFacade;
        this.checkpointManager = checkpointManager;
    }

    // ── Workflow builders ──────────────────────────────────────────

    /** Single-agent dispatch for simple tasks. */
    public CompiledAgentGraph buildSimpleTaskGraph(String agentName) {
        Agent agent = agentRegistry.get(agentName);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentName);
        }

        AgentGraph graph = new AgentGraph()
                .addNode("executor", agent)
                .addEdge(AgentGraph.START, "executor")
                .addEdge("executor", AgentGraph.END)
                .addChannel("task_result", String.class, "")
                .withCheckpointManager(checkpointManager);

        return compileWithExecutor(graph);
    }

    /**
     * Feature development pipeline:
     * Explorer → Architect → Coder → Tester → Reviewer
     */
    public CompiledAgentGraph buildFeatureDevGraph() {
        Agent explore = requireAgent("Explorer");
        Agent architect = requireAgent("Architect");
        Agent coder = requireAgent("Coder");
        Agent tester = requireAgent("Tester");
        Agent reviewer = requireAgent("Reviewer");

        AgentGraph graph = new AgentGraph()
                .addNode("explore", explore)
                .addNode("architect", architect)
                .addNode("coder", coder)
                .addNode("tester", tester)
                .addNode("reviewer", reviewer)
                .addEdge(AgentGraph.START, "explore")
                .addEdge("explore", "architect")
                .addEdge("architect", "coder")
                .addEdge("coder", "tester")
                .addEdge("tester", "reviewer")
                .addEdge("reviewer", AgentGraph.END)
                .addChannel("exploration_result", String.class, "")
                .addChannel("architecture_result", String.class, "")
                .addChannel("code_result", String.class, "")
                .addChannel("test_result", String.class, "")
                .addChannel("review_findings", String.class, "")
                .withCheckpointManager(checkpointManager)
                .maxSteps(10);

        return compileWithExecutor(graph);
    }

    /**
     * Bug fix pipeline:
     * Explorer → Debugger → Coder → Tester
     */
    public CompiledAgentGraph buildBugFixGraph() {
        Agent explore = requireAgent("Explorer");
        Agent debug = requireAgent("Debugger");
        Agent coder = requireAgent("Coder");
        Agent tester = requireAgent("Tester");

        AgentGraph graph = new AgentGraph()
                .addNode("explore", explore)
                .addNode("debug", debug)
                .addNode("coder", coder)
                .addNode("tester", tester)
                .addEdge(AgentGraph.START, "explore")
                .addEdge("explore", "debug")
                .addEdge("debug", "coder")
                .addEdge("coder", "tester")
                .addEdge("tester", AgentGraph.END)
                .addChannel("bug_analysis", String.class, "")
                .addChannel("fix_result", String.class, "")
                .withCheckpointManager(checkpointManager)
                .maxSteps(8);

        return compileWithExecutor(graph);
    }

    /**
     * Code review pipeline:
     * Explorer → Reviewer
     */
    public CompiledAgentGraph buildReviewGraph() {
        Agent explore = requireAgent("Explorer");
        Agent reviewer = requireAgent("Reviewer");

        AgentGraph graph = new AgentGraph()
                .addNode("explore", explore)
                .addNode("reviewer", reviewer)
                .addEdge(AgentGraph.START, "explore")
                .addEdge("explore", "reviewer")
                .addEdge("reviewer", AgentGraph.END)
                .addChannel("review_result", String.class, "")
                .withCheckpointManager(checkpointManager)
                .maxSteps(6);

        return compileWithExecutor(graph);
    }

    /**
     * Refactoring pipeline:
     * Explorer → Architect → Coder → Tester
     */
    public CompiledAgentGraph buildRefactorGraph() {
        Agent explore = requireAgent("Explorer");
        Agent architect = requireAgent("Architect");
        Agent coder = requireAgent("Coder");
        Agent tester = requireAgent("Tester");

        AgentGraph graph = new AgentGraph()
                .addNode("explore", explore)
                .addNode("architect", architect)
                .addNode("coder", coder)
                .addNode("tester", tester)
                .addEdge(AgentGraph.START, "explore")
                .addEdge("explore", "architect")
                .addEdge("architect", "coder")
                .addEdge("coder", "tester")
                .addEdge("tester", AgentGraph.END)
                .addChannel("refactor_plan", String.class, "")
                .addChannel("refactor_result", String.class, "")
                .withCheckpointManager(checkpointManager)
                .maxSteps(8);

        return compileWithExecutor(graph);
    }

    /**
     * Conditional routing example: explore then decide.
     * Explorer → [if needs_architecture → Architect] → Coder → Tester
     */
    public CompiledAgentGraph buildConditionalDevGraph() {
        Agent explore = requireAgent("Explorer");
        Agent architect = requireAgent("Architect");
        Agent coder = requireAgent("Coder");
        Agent tester = requireAgent("Tester");

        Set<String> architectOrCoder = new LinkedHashSet<>();
        architectOrCoder.add("architect");
        architectOrCoder.add("coder");

        AgentGraph graph = new AgentGraph()
                .addNode("explore", explore)
                .addNode("architect", architect)
                .addNode("coder", coder)
                .addNode("tester", tester)
                .addEdge(AgentGraph.START, "explore")
                .addConditionalEdge("explore", state -> {
                    // Simple heuristic: if exploration found architecture concerns, route to architect
                    String result = state.getValue("exploration_result");
                    if (result != null && (result.contains("architecture")
                            || result.contains("design")
                            || result.length() > 2000)) {
                        return "architect";
                    }
                    return "coder";
                }, architectOrCoder)
                .addEdge("architect", "coder")
                .addEdge("coder", "tester")
                .addEdge("tester", AgentGraph.END)
                .addChannel("exploration_result", String.class, "")
                .addChannel("architecture_result", String.class, "")
                .addChannel("code_result", String.class, "")
                .withCheckpointManager(checkpointManager)
                .maxSteps(8);

        return compileWithExecutor(graph);
    }

    // ── Private helpers ────────────────────────────────────────────

    private Agent requireAgent(String name) {
        Agent agent = agentRegistry.get(name);
        if (agent == null) {
            throw new IllegalStateException("Required agent not registered: " + name);
        }
        return agent;
    }

    /** Compile a graph and inject the A2A-based agent executor. */
    private CompiledAgentGraph compileWithExecutor(AgentGraph graph) {
        CompiledAgentGraph compiled = graph.compile();
        compiled.withAgentExecutor((node, state) -> executeAgentViaA2A(node, state));
        return compiled;
    }

    /**
     * Execute a graph node by dispatching to the agent via A2AFacade.
     * Reads the current task description from the "task_description" channel
     * (or uses the node name), creates an A2ATask, and waits for output.
     */
    private NodeResult executeAgentViaA2A(GraphNode node, GraphState state) {
        if (a2aFacade == null) {
            return NodeResult.success(node.getName())
                    .write("task_result", "A2A Facade not available — no-op for " + node.getName())
                    .build();
        }

        try {
            String taskDesc = state.getValue("task_description");
            if (taskDesc == null || taskDesc.isEmpty()) {
                taskDesc = "Execute " + node.getAgent().getName() + " task";
            }

            A2ATask task = A2ATask.builder()
                    .taskId(UUID.randomUUID().toString().substring(0, 8))
                    .skillId(node.getAgent().getId())
                    .description(taskDesc)
                    .priority(5)
                    .build();

            TaskOutput output = a2aFacade.submitTaskSync(node.getName(), task);

            if (output != null && output.isSuccess()) {
                return NodeResult.success(node.getName())
                        .write("task_result", output.getSummary() != null
                                ? output.getSummary() : "Success")
                        .build();
            } else {
                String errorMsg = output != null ? output.getSummary() : "Unknown error";
                return NodeResult.failure(node.getName(),
                        new RuntimeException("Agent " + node.getName() + " failed: " + errorMsg))
                        .build();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Agent execution failed for node " + node.getName(), e);
            return NodeResult.failure(node.getName(), e).build();
        }
    }
}
