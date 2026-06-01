package com.jwcode.core.graph;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * An edge in the agent graph connecting a source node to a target node.
 * <p>
 * Two flavors:
 * <ul>
 *   <li><b>Simple edge</b>: when {@code source} completes, trigger {@code target}.</li>
 *   <li><b>Conditional edge</b>: a {@code router} function maps the current state
 *       to the next node name(s), enabling dynamic branching.</li>
 * </ul>
 * Modeled after LangGraph's {@code addEdge} / {@code addConditionalEdges}.
 */
public class GraphEdge {

    private final String source;
    private final String target;             // null for conditional edges
    private final Function<GraphState, String> router; // null for simple edges
    private final Set<String> possibleTargets; // for conditional edges — set of known outcomes

    private GraphEdge(String source, String target,
                      Function<GraphState, String> router,
                      Set<String> possibleTargets) {
        this.source = Objects.requireNonNull(source);
        this.target = target;
        this.router = router;
        this.possibleTargets = possibleTargets;
    }

    /** Create a simple edge: when source completes, trigger target. */
    public static GraphEdge simple(String source, String target) {
        return new GraphEdge(source, target, null, null);
    }

    /** Create a conditional edge: call router(state) to determine next node. */
    public static GraphEdge conditional(String source,
                                         Function<GraphState, String> router,
                                         Set<String> possibleTargets) {
        return new GraphEdge(source, null, router, possibleTargets);
    }

    public String getSource() { return source; }
    public String getTarget() { return target; }
    public boolean isConditional() { return router != null; }

    /** For simple edges, returns the single target. */
    public String resolveTarget() {
        if (isConditional()) {
            throw new IllegalStateException("Cannot resolve conditional edge without state");
        }
        return target;
    }

    /** For conditional edges, evaluate the router against the current state. */
    public String resolveTarget(GraphState state) {
        if (!isConditional()) return target;
        String result = router.apply(state);
        if (possibleTargets != null && !possibleTargets.contains(result)) {
            throw new IllegalStateException(
                "Router returned '" + result + "', expected one of " + possibleTargets);
        }
        return result;
    }

    public Set<String> getPossibleTargets() {
        return possibleTargets;
    }

    @Override
    public String toString() {
        if (isConditional()) {
            return source + " --> [? → " + possibleTargets + "]";
        }
        return source + " --> " + target;
    }
}
