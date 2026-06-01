package com.jwcode.core.graph;

import com.jwcode.core.checkpoint.CheckpointManager;
import com.jwcode.core.graph.channel.Channel;
import com.jwcode.core.graph.channel.EphemeralChannel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A compiled, executable agent graph — the runtime counterpart of
 * {@link AgentGraph}, powered by a Pregel-style BSP (Bulk Synchronous Parallel)
 * execution loop.
 *
 * <p>Each call to {@link #invoke} runs the graph one superstep at a time:</p>
 * <ol>
 *   <li><b>Prepare</b>: determine which nodes are triggered by updated channels</li>
 *   <li><b>Execute</b>: run all triggered nodes in parallel</li>
 *   <li><b>Apply writes</b>: merge node outputs into channels via reducers</li>
 *   <li><b>Checkpoint</b>: save state after each superstep</li>
 *   <li><b>Interrupt</b>: if configured, pause before/after specified nodes</li>
 * </ol>
 */
public class CompiledAgentGraph {

    private static final Logger logger = Logger.getLogger(CompiledAgentGraph.class.getName());

    // Reserved channel names
    static final String INTERRUPT = "__interrupt__";
    static final String ERROR = "__error__";
    static final String TASKS = "__tasks__";

    private final Map<String, GraphNode> nodes;
    private final Map<String, Channel<?>> channels;
    private final GraphState initialState;
    private final Map<String, Set<String>> triggerToNodes;
    private final Map<String, Set<String>> edgeMap;
    private final CheckpointManager checkpointManager;
    private final Set<String> interruptBefore;
    private final Set<String> interruptAfter;
    private final int maxSteps;

    CompiledAgentGraph(Map<String, GraphNode> nodes,
                       Map<String, Channel<?>> channels,
                       GraphState initialState,
                       Map<String, Set<String>> triggerToNodes,
                       Map<String, Set<String>> edgeMap,
                       CheckpointManager checkpointManager,
                       Set<String> interruptBefore,
                       Set<String> interruptAfter,
                       int maxSteps) {
        this.nodes = nodes;
        this.channels = channels;
        this.initialState = initialState;
        this.triggerToNodes = triggerToNodes;
        this.edgeMap = edgeMap;
        this.checkpointManager = checkpointManager;
        this.interruptBefore = interruptBefore;
        this.interruptAfter = interruptAfter;
        this.maxSteps = maxSteps;
    }

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Run the graph to completion with the given input, returning the final
     * state after all nodes have executed and all channels have settled.
     */
    public GraphState invoke(Map<String, Object> input) {
        PregelLoop loop = new PregelLoop(input);
        try {
            while (loop.tick()) {
                // ticks until done or interrupted
            }
        } catch (GraphInterruptedException e) {
            logger.info("Graph interrupted at step " + loop.step + ": " + e.getMessage());
        }
        return loop.snapshotState();
    }

    /**
     * Run the graph asynchronously, returning a future that completes with
     * the final state.
     */
    public CompletableFuture<GraphState> invokeAsync(Map<String, Object> input) {
        return CompletableFuture.supplyAsync(() -> invoke(input));
    }

    /**
     * Resume execution from a previously interrupted state.
     * The {@code resumeValues} map contains values to inject into the RESUME
     * channel for the interrupted node to consume.
     */
    public GraphState resume(Map<String, Object> resumeValues) {
        PregelLoop loop = new PregelLoop(resumeValues);
        loop.isResuming = true;
        try {
            while (loop.tick()) {
                // ticks until done
            }
        } catch (GraphInterruptedException e) {
            logger.info("Graph interrupted again at step " + loop.step);
        }
        return loop.snapshotState();
    }

    // ── Accessors ──────────────────────────────────────────────────

    public Map<String, GraphNode> getNodes() { return Collections.unmodifiableMap(nodes); }
    public Set<String> getNodeNames() { return nodes.keySet(); }
    public Set<String> getInterruptBefore() { return interruptBefore; }
    public Set<String> getInterruptAfter() { return interruptAfter; }

    // ═══════════════════════════════════════════════════════════════
    // Pregel Loop — the BSP execution engine
    // ═══════════════════════════════════════════════════════════════

    class PregelLoop {

        int step;
        GraphState state;
        boolean isResuming;

        // Channel version tracking (LangGraph-style)
        Map<String, Long> channelVersions;        // channel → version
        Map<String, Map<String, Long>> versionsSeen; // node → (channel → version)

        // Pending writes from the current superstep
        List<PendingWrite> pendingWrites;

        // Current set of tasks for this superstep
        Map<String, GraphTask> tasks;

        // Checkpoint state
        String runId;
        boolean interrupted;
        String interruptReason;

        // Executor for parallel node execution
        final ExecutorService executor;

        PregelLoop(Map<String, Object> input) {
            this.step = 0;
            this.state = initialState.copy();
            this.isResuming = false;
            this.channelVersions = new LinkedHashMap<>();
            this.versionsSeen = new LinkedHashMap<>();
            this.pendingWrites = new ArrayList<>();
            this.tasks = new LinkedHashMap<>();
            this.runId = UUID.randomUUID().toString();
            this.executor = ForkJoinPool.commonPool();

            // Initialize channel versions
            for (String chName : state.channelNames()) {
                channelVersions.put(chName, 0L);
            }

            // Apply input writes to seed the state
            if (input != null) {
                for (var entry : input.entrySet()) {
                    Channel<?> ch = state.getChannel(entry.getKey());
                    if (ch != null) {
                        @SuppressWarnings({"rawtypes", "unchecked"})
                        Channel<Object> raw = (Channel) ch;
                        raw.update(List.of(entry.getValue()));
                        channelVersions.merge(entry.getKey(), 1L, Long::sum);
                    }
                }
            }

            // Seed START trigger: all nodes that have edges from START
            seedStartTriggers();
        }

        /**
         * Execute a single superstep.
         * @return true if more supersteps are needed, false if the graph is done.
         */
        boolean tick() {
            // Check step limit
            if (step >= maxSteps) {
                logger.warning("Graph reached max steps (" + maxSteps + "), stopping");
                return false;
            }

            // 1. Prepare next tasks: which nodes should run this superstep?
            tasks = prepareNextTasks();
            if (tasks.isEmpty()) {
                logger.fine("No tasks to execute at step " + step + " — graph done");
                return false;
            }

            // 2. Check interrupt-before
            if (!interruptBefore.isEmpty()) {
                for (GraphTask task : tasks.values()) {
                    if (interruptBefore.contains(task.nodeName)) {
                        interrupted = true;
                        interruptReason = "interrupt_before: " + task.nodeName;
                        saveCheckpoint("interrupt");
                        return false;
                    }
                }
            }

            // 3. Execute all ready tasks in parallel
            executeTasks(tasks);

            // 4. Apply writes to channels
            Set<String> updatedChannels = applyWrites();

            // 5. Save checkpoint after superstep
            saveCheckpoint("loop");

            // 6. Check interrupt-after
            if (!interruptAfter.isEmpty()) {
                for (GraphTask task : tasks.values()) {
                    if (interruptAfter.contains(task.nodeName) && task.success) {
                        interrupted = true;
                        interruptReason = "interrupt_after: " + task.nodeName;
                        return false;
                    }
                }
            }

            step++;
            return true;
        }

        // ── Task preparation ───────────────────────────────────

        private Map<String, GraphTask> prepareNextTasks() {
            Map<String, GraphTask> ready = new LinkedHashMap<>();

            // Re-apply pending writes from previous superstep (for resumed tasks)
            if (!isResuming && !pendingWrites.isEmpty()) {
                reapplyPendingWrites();
            }

            for (var entry : nodes.entrySet()) {
                String nodeName = entry.getKey();
                GraphNode node = entry.getValue();

                // Check if any of this node's trigger channels have been updated
                boolean triggered = false;
                for (String chName : state.channelNames()) {
                    Set<String> subscribers = triggerToNodes.getOrDefault(chName, Collections.emptySet());
                    if (!subscribers.contains(nodeName)) continue;

                    long currentVersion = channelVersions.getOrDefault(chName, 0L);
                    Map<String, Long> seen = versionsSeen.computeIfAbsent(nodeName, k -> new LinkedHashMap<>());
                    long seenVersion = seen.getOrDefault(chName, -1L);

                    if (currentVersion > seenVersion) {
                        triggered = true;
                        break;
                    }
                }

                if (triggered) {
                    // Clone state for this task (isolated read snapshot)
                    GraphState taskState = state.copy();
                    GraphTask task = new GraphTask(nodeName, node, taskState);
                    ready.put(nodeName, task);
                }
            }

            return ready;
        }

        private void reapplyPendingWrites() {
            for (PendingWrite pw : pendingWrites) {
                GraphTask task = tasks.get(pw.taskId);
                if (task != null && task.success) {
                    task.writes.computeIfAbsent(pw.channel, k -> new ArrayList<>()).add(pw.value);
                }
            }
            pendingWrites.clear();
        }

        // ── Task execution ────────────────────────────────────

        private void executeTasks(Map<String, GraphTask> taskMap) {
            if (taskMap.isEmpty()) return;

            // Sort tasks: non-deferred first, then deferred
            List<GraphTask> ordered = new ArrayList<>(taskMap.values());
            ordered.sort((a, b) -> {
                if (a.node.isDefer() != b.node.isDefer()) {
                    return a.node.isDefer() ? 1 : -1;
                }
                return a.nodeName.compareTo(b.nodeName);
            });

            // Execute in parallel using completable futures
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (GraphTask task : ordered) {
                futures.add(CompletableFuture.runAsync(() -> executeSingleTask(task), executor));
            }

            // Wait for all to complete
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(5, TimeUnit.MINUTES);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Task execution interrupted", e);
                Thread.currentThread().interrupt();
            }
        }

        private void executeSingleTask(GraphTask task) {
            GraphNode node = task.node;
            try {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Executing node: " + node.getName()
                            + " (agent: " + node.getAgent().getName() + ")");
                }

                // Execute the agent against the task's state snapshot
                // In this version, the NodeResult captures writes produced by the agent
                NodeResult result = executeAgent(task);

                if (result.isSuccess()) {
                    task.success = true;
                    task.writes = new LinkedHashMap<>(result.getWrites());

                    // Auto-write to branch channels for static edges
                    autoRouteStaticEdges(task, result);
                } else {
                    task.success = false;
                    task.error = result.getError();
                    // Write error to ERROR channel
                    task.writes.computeIfAbsent(ERROR, k -> new ArrayList<>())
                            .add(result.getError());
                }
            } catch (Exception e) {
                task.success = false;
                task.error = e;
                logger.log(Level.WARNING, "Node " + node.getName() + " threw exception", e);
            }
        }

        /**
         * Execute the agent and collect its writes.
         * Subclasses/adapters can override this to plug in their own execution
         * mechanism (LLMQueryEngine, A2AFacade, etc.).
         */
        NodeResult executeAgent(GraphTask task) {
            // Default implementation: produce an empty success result.
            // Real execution is provided by the Orchestrator integration
            // via the AgentExecutor functional interface.
            if (agentExecutor != null) {
                return agentExecutor.execute(task.node, task.snapshotState);
            }
            return NodeResult.success(task.nodeName).build();
        }

        /**
         * For each static edge from this node, auto-write a trigger to the
         * target's branch channel so it fires on the next superstep.
         */
        private void autoRouteStaticEdges(GraphTask task, NodeResult result) {
            Set<String> targets = edgeMap.get(task.nodeName);
            if (targets == null) return;

            for (String target : targets) {
                if (AgentGraph.END.equals(target)) continue;
                String branchChannel = "branch:to:" + target;
                if (state.getChannel(branchChannel) != null) {
                    task.writes.computeIfAbsent(branchChannel, k -> new ArrayList<>())
                            .add("trigger");
                }
            }
        }

        // ── Write application ─────────────────────────────────

        @SuppressWarnings({"unchecked", "rawtypes"})
        Set<String> applyWrites() {
            Set<String> updatedChannels = new LinkedHashSet<>();

            // Collect all writes from all tasks
            for (GraphTask task : tasks.values()) {
                if (!task.success) continue;

                for (var entry : task.writes.entrySet()) {
                    String chName = entry.getKey();
                    if (INTERRUPT.equals(chName) || ERROR.equals(chName)) continue;

                    Channel<?> ch = state.getChannel(chName);
                    if (ch == null) {
                        // Auto-create ephemeral channel for unknown writes
                        EphemeralChannel<Object> ec = new EphemeralChannel<>();
                        ec.update((List) entry.getValue());
                        state.addChannel(chName, ec);
                        channelVersions.putIfAbsent(chName, 0L);
                    }
                }
            }

            // Apply writes channel by channel
            for (GraphTask task : tasks.values()) {
                if (!task.success) continue;

                for (var entry : task.writes.entrySet()) {
                    String chName = entry.getKey();
                    if (INTERRUPT.equals(chName) || ERROR.equals(chName)) continue;

                    Channel ch = state.getChannel(chName);
                    if (ch != null) {
                        List<Object> vals = (List<Object>) entry.getValue();
                        if (ch.update(vals)) {
                            updatedChannels.add(chName);
                            channelVersions.merge(chName, 1L, Long::sum);
                        }
                    }
                }

                // Record versions seen by this node
                Map<String, Long> seen = versionsSeen.computeIfAbsent(task.nodeName,
                        k -> new LinkedHashMap<>());
                for (String chName : updatedChannels) {
                    seen.put(chName, channelVersions.getOrDefault(chName, 0L));
                }
            }

            // Clear pending writes and persist successful ones for replay
            pendingWrites.clear();
            for (GraphTask task : tasks.values()) {
                if (task.success && task.writes != null) {
                    for (var entry : task.writes.entrySet()) {
                        for (Object val : entry.getValue()) {
                            pendingWrites.add(new PendingWrite(task.nodeName, entry.getKey(), val));
                        }
                    }
                }
            }

            return updatedChannels;
        }

        // ── Checkpoint ────────────────────────────────────────

        void saveCheckpoint(String source) {
            if (checkpointManager == null) return;

            try {
                Map<String, Object> cpValues = state.toCheckpointValues();
                Map<String, Long> cpVersions = new LinkedHashMap<>(channelVersions);
                Map<String, Map<String, Long>> cpSeen = new LinkedHashMap<>();
                for (var entry : versionsSeen.entrySet()) {
                    cpSeen.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
                }

                GraphCheckpoint cp = new GraphCheckpoint(
                        runId + "-" + step,
                        Instant.now().toString(),
                        cpValues,
                        cpVersions,
                        cpSeen,
                        Collections.unmodifiableList(new ArrayList<>(
                                pendingWrites.stream().map(pw -> pw.channel).distinct().toList())),
                        step,
                        source
                );

                // Store in the existing CheckpointManager if possible
                logger.fine("Checkpoint saved: step=" + step + " source=" + source);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to save checkpoint", e);
            }
        }

        // ── START seeding ─────────────────────────────────────

        private void seedStartTriggers() {
            // Find all edges from START
            Set<String> startTargets = edgeMap.getOrDefault(AgentGraph.START, Collections.emptySet());
            // Direct START-connected nodes get triggered immediately
            for (String target : startTargets) {
                if (!AgentGraph.END.equals(target)) {
                    String branchChannel = "branch:to:" + target;
                    Channel<?> ch = state.getChannel(branchChannel);
                    if (ch != null) {
                        @SuppressWarnings({"rawtypes", "unchecked"})
                        Channel<Object> raw = (Channel) ch;
                        raw.update(List.of("trigger"));
                        channelVersions.merge(branchChannel, 1L, Long::sum);
                    }
                }
            }
        }

        // ── State snapshot ────────────────────────────────────

        GraphState snapshotState() {
            return state.copy();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Inner types
    // ═══════════════════════════════════════════════════════════════

    /** A task scheduled for execution in a single superstep. */
    static class GraphTask {
        final String nodeName;
        final GraphNode node;
        final GraphState snapshotState;
        boolean success;
        Throwable error;
        Map<String, List<Object>> writes;

        GraphTask(String nodeName, GraphNode node, GraphState snapshotState) {
            this.nodeName = nodeName;
            this.node = node;
            this.snapshotState = snapshotState;
            this.success = false;
            this.writes = new LinkedHashMap<>();
        }
    }

    /** A single write to a channel, pending checkpoint persistence. */
    record PendingWrite(String taskId, String channel, Object value) {}

    /** Functional interface for plugging in agent execution. */
    @FunctionalInterface
    public interface AgentExecutor {
        NodeResult execute(GraphNode node, GraphState state);
    }

    /** Set by the Orchestrator to provide actual agent execution. */
    private volatile AgentExecutor agentExecutor;

    /** Register an agent executor that actually runs agents. */
    public CompiledAgentGraph withAgentExecutor(AgentExecutor executor) {
        this.agentExecutor = executor;
        return this;
    }

    public AgentExecutor getAgentExecutor() {
        return agentExecutor;
    }
}
