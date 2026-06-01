package com.jwcode.core.checkpoint;

import com.jwcode.core.graph.GraphCheckpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * In-memory checkpoint storage backed by concurrent maps.
 * Fast but does not survive process restarts. Suitable for short-lived
 * sessions where persistence is handled by the session JSON serialization.
 */
public class InMemoryCheckpointStorage implements CheckpointStorage {

    private final String sessionId;
    private final Map<String, GraphCheckpoint> byId = new ConcurrentHashMap<>();
    private final Map<Integer, GraphCheckpoint> byStep = new ConcurrentHashMap<>();
    private final List<GraphCheckpoint> ordered = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentLinkedQueue<PendingWrite> pendingWrites = new ConcurrentLinkedQueue<>();

    public InMemoryCheckpointStorage(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public void put(GraphCheckpoint checkpoint) {
        byId.put(checkpoint.getId(), checkpoint);
        byStep.put(checkpoint.getStep(), checkpoint);
        ordered.add(checkpoint);
    }

    @Override
    public void putWrite(String checkpointId, String taskId, String channel, Object value) {
        pendingWrites.add(new PendingWrite(checkpointId, taskId, channel, value));
        // Cap pending writes at 10,000 to prevent memory leaks
        while (pendingWrites.size() > 10_000) {
            pendingWrites.poll();
        }
    }

    @Override
    public Optional<GraphCheckpoint> get(String checkpointId) {
        return Optional.ofNullable(byId.get(checkpointId));
    }

    @Override
    public Optional<GraphCheckpoint> getByStep(int step) {
        return Optional.ofNullable(byStep.get(step));
    }

    @Override
    public Optional<GraphCheckpoint> getLatest() {
        if (ordered.isEmpty()) return Optional.empty();
        return Optional.of(ordered.get(ordered.size() - 1));
    }

    @Override
    public List<GraphCheckpoint> list(Integer before, Integer limit) {
        var stream = ordered.stream();
        if (before != null) {
            stream = stream.filter(cp -> cp.getStep() < before);
        }
        if (limit != null) {
            stream = stream.sorted(Comparator.comparingInt(GraphCheckpoint::getStep).reversed())
                    .limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    @Override
    public void clear() {
        byId.clear();
        byStep.clear();
        ordered.clear();
        pendingWrites.clear();
    }

    @Override
    public int size() {
        return ordered.size();
    }

    @Override
    public void close() {
        clear();
    }

    // ── Internal types ────────────────────────────────────────────

    record PendingWrite(String checkpointId, String taskId, String channel, Object value) {}
}
