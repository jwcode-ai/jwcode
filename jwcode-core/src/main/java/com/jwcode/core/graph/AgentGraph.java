package com.jwcode.core.graph;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.checkpoint.CheckpointManager;
import com.jwcode.core.graph.channel.BinaryOpChannel;
import com.jwcode.core.graph.channel.Channel;
import com.jwcode.core.graph.channel.LastValueChannel;


import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Declarative builder for agent orchestration graphs — the Java equivalent of
 * LangGraph's {@code StateGraph}.
 *
 * <pre>{@code
 * AgentGraph graph = new AgentGraph()
 *     .addNode("explore", exploreAgent)
 *     .addNode("coder", coderAgent)
 *     .addNode("reviewer", reviewerAgent)
 *     .addEdge(START, "explore")
 *     .addConditionalEdge("explore", state -> state.needsCode() ? "coder" : END)
 *     .addEdge("coder", "reviewer")
 *     .addEdge("reviewer", END);
 *
 * CompiledAgentGraph compiled = graph.compile(checkpointManager);
 * compiled.invoke(input);
 * }</pre>
 */
public class AgentGraph {

    private static final Logger logger = Logger.getLogger(AgentGraph.class.getName());

    public static final String START = "__start__";
    public static final String END = "__end__";

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final GraphState state = new GraphState();
    private final Map<String, Set<String>> nodeSubscriptions = new LinkedHashMap<>();

    // Compiled options
    private CheckpointManager checkpointManager;
    private Set<String> interruptBefore = Collections.emptySet();
    private Set<String> interruptAfter = Collections.emptySet();
    private int maxSteps = 50;

    // ── Node registration ────────────────────────────────────────

    /**
     * Register a node in the graph. A node wraps an {@link Agent} that will
     * be executed when the node is triggered by incoming channel updates.
     */
    public AgentGraph addNode(String name, Agent agent) {
        Objects.requireNonNull(name, "node name");
        Objects.requireNonNull(agent, "agent");
        if (nodes.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate node: " + name);
        }
        GraphNode node = GraphNode.builder(name, agent).build();
        nodes.put(name, node);
        nodeSubscriptions.put(name, new LinkedHashSet<>());
        return this;
    }

    /**
     * Register a fully-configured GraphNode (for advanced options like
     * retryPolicy, timeout, defer, errorHandler).
     */
    public AgentGraph addNode(GraphNode node) {
        Objects.requireNonNull(node, "node");
        if (nodes.containsKey(node.getName())) {
            throw new IllegalArgumentException("Duplicate node: " + node.getName());
        }
        nodes.put(node.getName(), node);
        nodeSubscriptions.put(node.getName(), new LinkedHashSet<>());
        return this;
    }

    // ── Channel registration ──────────────────────────────────────

    /**
     * Add a named channel to the graph state. Nodes read from and write to
     * channels. Channels define the update semantics (last-value wins, reducer
     * merge, topic/pubsub, etc.).
     */
    public <T> AgentGraph addChannel(String name, Channel<T> channel) {
        state.addChannel(name, channel);
        return this;
    }

    /**
     * Shorthand: add a last-value channel with an initial value.
     */
    public <T> AgentGraph addChannel(String name, Class<T> type, T initialValue) {
        state.addChannel(name, new LastValueChannel<>(initialValue));
        return this;
    }

    /**
     * Shorthand: add a reducer-based channel (for list-append semantics on
     * messages, etc.).
     */
    public <T> AgentGraph addChannel(String name, Class<T> type,
                                      T initialValue, BinaryOperator<T> reducer) {
        state.addChannel(name, new BinaryOpChannel<>(initialValue, reducer));
        return this;
    }

    // ── Edge registration ─────────────────────────────────────────

    /**
     * Add a simple edge: when {@code source} completes, trigger {@code target}.
     * Use {@link #START} and {@link #END} for terminal markers.
     */
    public AgentGraph addEdge(String source, String target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        edges.add(GraphEdge.simple(source, target));
        return this;
    }

    /**
     * Add a conditional edge: after {@code source} completes, call
     * {@code router} with the current state to determine the next node.
     * {@code possibleTargets} documents the known outcomes (used for
     * validation and interrupt tracking).
     */
    public AgentGraph addConditionalEdge(String source,
                                          Function<GraphState, String> router,
                                          Set<String> possibleTargets) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(router, "router");
        edges.add(GraphEdge.conditional(source, router, possibleTargets));
        return this;
    }

    // ── Compile options ───────────────────────────────────────────

    public AgentGraph withCheckpointManager(CheckpointManager cpm) {
        this.checkpointManager = cpm;
        return this;
    }

    public AgentGraph interruptBefore(Set<String> nodeNames) {
        this.interruptBefore = Collections.unmodifiableSet(new LinkedHashSet<>(nodeNames));
        return this;
    }

    public AgentGraph interruptAfter(Set<String> nodeNames) {
        this.interruptAfter = Collections.unmodifiableSet(new LinkedHashSet<>(nodeNames));
        return this;
    }

    public AgentGraph maxSteps(int max) {
        this.maxSteps = max;
        return this;
    }

    // ── Compile ────────────────────────────────────────────────────

    /**
     * Compile the graph into an executable {@link CompiledAgentGraph}.
     * This resolves edges into channel subscriptions and builds the
     * trigger-to-nodes mapping needed by the Pregel execution loop.
     */
    public CompiledAgentGraph compile() {
        validate();

        // 1. Resolve edges → channel subscriptions
        buildChannelSubscriptions();

        // 2. Ensure every node reads/writes at least one channel
        ensureDefaultChannels();

        // 3. Build trigger-to-nodes map
        Map<String, Set<String>> triggerToNodes = buildTriggerMap();

        // 4. Build node-name → GraphNode map
        Map<String, GraphNode> nodeMap = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));

        // 5. Build channel map
        Map<String, Channel<?>> channelMap = state.getChannels();

        // 6. Build static edge map (source → [targets])
        Map<String, Set<String>> edgeMap = buildEdgeMap();

        return new CompiledAgentGraph(
                nodeMap, channelMap, state.copy(),
                triggerToNodes, edgeMap,
                checkpointManager,
                interruptBefore, interruptAfter,
                maxSteps
        );
    }

    // ── Private helpers ────────────────────────────────────────────

    private void validate() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Graph must have at least one node");
        }
        // Check that all edge sources/targets reference known nodes or markers
        for (GraphEdge edge : edges) {
            String src = edge.getSource();
            if (!START.equals(src) && !nodes.containsKey(src)) {
                throw new IllegalStateException("Edge source not found: " + src);
            }
            if (!edge.isConditional()) {
                String tgt = edge.getTarget();
                if (!END.equals(tgt) && !nodes.containsKey(tgt)) {
                    throw new IllegalStateException("Edge target not found: " + tgt);
                }
            } else {
                Set<String> possible = edge.getPossibleTargets();
                if (possible != null) {
                    for (String p : possible) {
                        if (!END.equals(p) && !nodes.containsKey(p)) {
                            throw new IllegalStateException(
                                    "Conditional edge possible target not found: " + p);
                        }
                    }
                }
            }
        }
        // Check START has at least one outgoing edge
        boolean hasStartEdge = edges.stream().anyMatch(e -> START.equals(e.getSource()));
        if (!hasStartEdge) {
            logger.warning("No edge from START — graph may never trigger any node");
        }
    }

    /**
     * Convert edges into channel-based subscriptions.
     *
     * For each simple edge (A → B):
     *   1. Create a channel "branch:to:B" if it doesn't exist
     *   2. Node A writes to "branch:to:B" on completion
     *   3. Node B subscribes to "branch:to:B"
     *
     * For conditional edges, we create a single "branch:from:SOURCE" channel
     * that the router writes the chosen target name to. Downstream nodes
     * subscribe to channels named "branch:from:SOURCE".
     */
    private void buildChannelSubscriptions() {
        // Track which sources are conditional
        Set<String> conditionalSources = new LinkedHashSet<>();
        for (GraphEdge edge : edges) {
            if (edge.isConditional()) {
                conditionalSources.add(edge.getSource());
            }
        }

        for (GraphEdge edge : edges) {
            String source = edge.getSource();

            if (START.equals(source)) {
                // START → target: target subscribes to START channel
                if (!edge.isConditional()) {
                    String triggerChannel = "branch:to:" + edge.getTarget();
                    addTriggerChannel(edge.getTarget(), triggerChannel);
                } else {
                    for (String possible : edge.getPossibleTargets()) {
                        if (!END.equals(possible)) {
                            addTriggerChannel(possible, "branch:from:" + START);
                        }
                    }
                }
            } else if (!edge.isConditional()) {
                String target = edge.getTarget();
                if (!END.equals(target)) {
                    String triggerChannel = "branch:to:" + target;
                    addTriggerChannel(target, triggerChannel);
                    // Node source writes to this channel on completion
                    nodeSubscriptions.computeIfAbsent(source, k -> new LinkedHashSet<>());
                }
            } else {
                // conditional edge from source
                for (String possible : edge.getPossibleTargets()) {
                    if (!END.equals(possible)) {
                        addTriggerChannel(possible, "branch:from:" + source);
                    }
                }
            }
        }
    }

    private void addTriggerChannel(String nodeName, String channelName) {
        if (!state.getChannels().containsKey(channelName)) {
            state.addChannel(channelName, new LastValueChannel<>(false));
        }
        nodeSubscriptions.computeIfAbsent(nodeName, k -> new LinkedHashSet<>())
                .add(channelName);
        state.subscribe(channelName, nodeName);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void ensureDefaultChannels() {
        // Ensure a "messages" channel exists (nearly every graph needs one)
        if (!state.getChannels().containsKey("messages")) {
            state.addChannel("messages",
                    new BinaryOpChannel<>(new ArrayList<>(), (a, b) -> {
                        ArrayList<Object> merged = new ArrayList<>(a);
                        merged.addAll(b);
                        return merged;
                    }));
        }
        // Ensure a "next_node" channel for conditional routing
        if (!state.getChannels().containsKey("next_node")) {
            state.addChannel("next_node", new LastValueChannel<>(""));
        }
    }

    private Map<String, Set<String>> buildTriggerMap() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (String channelName : state.channelNames()) {
            map.put(channelName, state.getSubscribers(channelName));
        }
        return Collections.unmodifiableMap(map);
    }

    private Map<String, Set<String>> buildEdgeMap() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (GraphEdge edge : edges) {
            if (!edge.isConditional()) {
                map.computeIfAbsent(edge.getSource(), k -> new LinkedHashSet<>())
                        .add(edge.getTarget());
            } else {
                Set<String> targets = edge.getPossibleTargets();
                if (targets != null) {
                    map.computeIfAbsent(edge.getSource(), k -> new LinkedHashSet<>())
                            .addAll(targets);
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    // ── Accessors ──────────────────────────────────────────────────

    public Map<String, GraphNode> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public List<GraphEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }
}
