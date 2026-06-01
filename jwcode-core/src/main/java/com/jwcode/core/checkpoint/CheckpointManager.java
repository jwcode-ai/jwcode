package com.jwcode.core.checkpoint;

import com.jwcode.core.graph.GraphCheckpoint;
import com.jwcode.core.session.Session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Checkpoint manager with channel-level version tracking.
 * <p>
 * Enhanced with graph-aware checkpoint support modeled after LangGraph's
 * checkpoint system: each channel has a monotonic version number, and
 * each node records which versions it has seen. This enables fine-grained
 * "which node saw what" tracking for incremental graph execution and
 * selective recovery.
 */
public class CheckpointManager {

    private static final Logger logger = Logger.getLogger(CheckpointManager.class.getName());

    private final String sessionId;
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private final Map<String, Checkpoint> checkpointMap = new ConcurrentHashMap<>();

    // ── Graph-aware checkpoint storage ──────────────────────────
    private final List<GraphCheckpoint> graphCheckpoints = new ArrayList<>();
    private final Map<String, GraphCheckpoint> graphCheckpointMap = new ConcurrentHashMap<>();
    private final Map<Integer, GraphCheckpoint> graphCheckpointsByStep = new ConcurrentHashMap<>();

    private int currentStep = 0;

    // ── Channel version tracking ────────────────────────────────
    /** Latest version for each channel. Monotonic, incremented on each write. */
    private final Map<String, Long> channelVersions = new ConcurrentHashMap<>();
    /** Per-node record of (channel → version) at last execution. */
    private final Map<String, Map<String, Long>> nodeVersionsSeen = new ConcurrentHashMap<>();

    // ── Checkpoint listeners ────────────────────────────────────
    private final List<Consumer<GraphCheckpoint>> checkpointListeners = new ArrayList<>();

    public CheckpointManager(String sessionId) {
        this.sessionId = sessionId;
    }

    // ═══════════════════════════════════════════════════════════
    // Session-level checkpoint API (existing, unchanged)
    // ═══════════════════════════════════════════════════════════

    public Checkpoint createCheckpoint(Session session, String description) {
        currentStep++;
        Checkpoint checkpoint = Checkpoint.fromSession(session, currentStep, description);
        checkpoints.add(checkpoint);
        checkpointMap.put(checkpoint.getId(), checkpoint);
        logger.info("Checkpoint created [" + currentStep + "]: " + description);
        return checkpoint;
    }

    public boolean revertTo(Session session, String checkpointId) {
        Checkpoint checkpoint = checkpointMap.get(checkpointId);
        if (checkpoint == null) {
            logger.warning("Checkpoint not found: " + checkpointId);
            return false;
        }
        checkpoint.restoreTo(session);
        currentStep = checkpoint.getStepNumber();
        logger.info("Reverted to checkpoint [" + checkpoint.getStepNumber() + "]: "
                + checkpoint.getDescription());
        return true;
    }

    public boolean revertToPrevious(Session session) {
        if (checkpoints.size() < 2) {
            logger.warning("Not enough checkpoints to revert");
            return false;
        }
        Checkpoint target = null;
        for (int i = checkpoints.size() - 1; i >= 0; i--) {
            if (checkpoints.get(i).getStepNumber() < currentStep) {
                target = checkpoints.get(i);
                break;
            }
        }
        if (target == null) {
            logger.warning("No reversible checkpoint found");
            return false;
        }
        return revertTo(session, target.getId());
    }

    public List<Checkpoint> getAllCheckpoints() {
        return List.copyOf(checkpoints);
    }

    public int getCheckpointCount() {
        return checkpoints.size();
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public String getCheckpointHistory() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Checkpoint History ===\n");
        for (Checkpoint cp : checkpoints) {
            String marker = cp.getStepNumber() == currentStep ? " → " : "   ";
            sb.append(marker).append(cp.getSummary()).append("\n");
        }
        return sb.toString();
    }

    public void cleanupOldCheckpoints(int keepCount) {
        if (checkpoints.size() <= keepCount) return;
        int removeCount = checkpoints.size() - keepCount;
        for (int i = 0; i < removeCount; i++) {
            Checkpoint removed = checkpoints.remove(0);
            checkpointMap.remove(removed.getId());
        }
        logger.info("Cleaned up " + removeCount + " old checkpoints");
    }

    // ═══════════════════════════════════════════════════════════
    // Graph checkpoint API (new — channel version tracking)
    // ═══════════════════════════════════════════════════════════

    /**
     * Save a graph-aware checkpoint with channel-level version tracking.
     * Called by {@code CompiledAgentGraph.PregelLoop} after each superstep.
     */
    public void saveGraphCheckpoint(GraphCheckpoint cp) {
        graphCheckpoints.add(cp);
        graphCheckpointMap.put(cp.getId(), cp);
        graphCheckpointsByStep.put(cp.getStep(), cp);

        // Update channel version tracking from the checkpoint
        for (var entry : cp.getChannelVersions().entrySet()) {
            channelVersions.merge(entry.getKey(), entry.getValue(), Math::max);
        }

        // Update node versions-seen tracking
        for (var nodeEntry : cp.getVersionsSeen().entrySet()) {
            nodeVersionsSeen.merge(nodeEntry.getKey(), nodeEntry.getValue(),
                    (existing, incoming) -> {
                        Map<String, Long> merged = new LinkedHashMap<>(existing);
                        incoming.forEach((k, v) -> merged.merge(k, v, Math::max));
                        return merged;
                    });
        }

        // Notify listeners (e.g., for streaming checkpoint events)
        for (Consumer<GraphCheckpoint> listener : checkpointListeners) {
            try {
                listener.accept(cp);
            } catch (Exception e) {
                logger.warning("Checkpoint listener error: " + e.getMessage());
            }
        }
    }

    /** Get the latest graph checkpoint. */
    public GraphCheckpoint getLatestGraphCheckpoint() {
        if (graphCheckpoints.isEmpty()) return null;
        return graphCheckpoints.get(graphCheckpoints.size() - 1);
    }

    /** Get a graph checkpoint by its unique ID. */
    public GraphCheckpoint getGraphCheckpoint(String id) {
        return graphCheckpointMap.get(id);
    }

    /** Get a graph checkpoint by step number. */
    public GraphCheckpoint getGraphCheckpointByStep(int step) {
        return graphCheckpointsByStep.get(step);
    }

    /** List all graph checkpoints in order. */
    public List<GraphCheckpoint> getAllGraphCheckpoints() {
        return List.copyOf(graphCheckpoints);
    }

    /** List graph checkpoints with optional filtering. */
    public List<GraphCheckpoint> listGraphCheckpoints(Integer before, Integer limit) {
        var stream = graphCheckpoints.stream();
        if (before != null) {
            stream = stream.filter(cp -> cp.getStep() < before);
        }
        if (limit != null) {
            stream = stream.sorted((a, b) -> Integer.compare(b.getStep(), a.getStep()))
                    .limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    /**
     * Check whether a given node should be triggered — i.e., whether any
     * of its subscribed channels have a version newer than what the node
     * has seen.
     *
     * @param nodeName       the node to check
     * @param channelNames   the channels this node subscribes to
     * @return true if the node has unseen updates
     */
    public boolean isNodeTriggered(String nodeName, Set<String> channelNames) {
        Map<String, Long> seen = nodeVersionsSeen.getOrDefault(nodeName, Collections.emptyMap());
        for (String ch : channelNames) {
            long current = channelVersions.getOrDefault(ch, 0L);
            long seenVer = seen.getOrDefault(ch, -1L);
            if (current > seenVer) return true;
        }
        return false;
    }

    /** Get the current version of a specific channel. */
    public long getChannelVersion(String channelName) {
        return channelVersions.getOrDefault(channelName, 0L);
    }

    /** Get the version that a specific node has seen for a specific channel. */
    public long getNodeSeenVersion(String nodeName, String channelName) {
        return nodeVersionsSeen
                .getOrDefault(nodeName, Collections.emptyMap())
                .getOrDefault(channelName, -1L);
    }

    /** Get all channel versions. */
    public Map<String, Long> getChannelVersions() {
        return Collections.unmodifiableMap(channelVersions);
    }

    /** Get all per-node versions-seen records. */
    public Map<String, Map<String, Long>> getNodeVersionsSeen() {
        return Collections.unmodifiableMap(nodeVersionsSeen);
    }

    /** Register a listener to be notified on each graph checkpoint save. */
    public void addCheckpointListener(Consumer<GraphCheckpoint> listener) {
        checkpointListeners.add(listener);
    }

    /** Remove a previously registered checkpoint listener. */
    public void removeCheckpointListener(Consumer<GraphCheckpoint> listener) {
        checkpointListeners.remove(listener);
    }

    /**
     * Clear all graph checkpoints and version tracking state.
     * Does NOT clear the legacy session-level checkpoints.
     */
    public void resetGraphState() {
        graphCheckpoints.clear();
        graphCheckpointMap.clear();
        graphCheckpointsByStep.clear();
        channelVersions.clear();
        nodeVersionsSeen.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // D-Mail (existing, unchanged)
    // ═══════════════════════════════════════════════════════════

    public DMail createDMail(Session session, String targetCheckpointId, String message) {
        Checkpoint target = checkpointMap.get(targetCheckpointId);
        if (target == null) {
            throw new IllegalArgumentException("Target checkpoint not found: " + targetCheckpointId);
        }
        return new DMail(generateDMailId(), target, message, currentStep);
    }

    private String generateDMailId() {
        return "dmail_" + System.currentTimeMillis();
    }

    public static class DMail {
        private final String id;
        private final Checkpoint target;
        private final String message;
        private final int fromStep;
        private final long timestamp;

        public DMail(String id, Checkpoint target, String message, int fromStep) {
            this.id = id;
            this.target = target;
            this.message = message;
            this.fromStep = fromStep;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public Checkpoint getTarget() { return target; }
        public String getMessage() { return message; }
        public int getFromStep() { return fromStep; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("D-Mail [%s]: Step %d → Step %d - %s",
                id, fromStep, target.getStepNumber(), message);
        }
    }
}
