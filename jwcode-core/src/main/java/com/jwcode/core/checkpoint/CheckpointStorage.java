package com.jwcode.core.checkpoint;

import com.jwcode.core.graph.GraphCheckpoint;

import java.util.List;
import java.util.Optional;

/**
 * Pluggable storage backend for graph checkpoints.
 * <p>
 * Modeled after LangGraph's {@code BaseCheckpointSaver}. Implementations can
 * be in-memory (for ephemeral sessions), SQLite-based (for local durability),
 * or Postgres-based (for production multi-process deployments).
 */
public interface CheckpointStorage extends AutoCloseable {

    /**
     * Persist a graph checkpoint and its associated metadata.
     * @param checkpoint the checkpoint to save
     */
    void put(GraphCheckpoint checkpoint);

    /**
     * Persist pending writes for a task within a checkpoint.
     * @param checkpointId the checkpoint these writes belong to
     * @param taskId       the task that produced the writes
     * @param channel      the channel name
     * @param value        the serialized write value (JSON or blob)
     */
    void putWrite(String checkpointId, String taskId, String channel, Object value);

    /**
     * Retrieve a checkpoint by its unique ID.
     */
    Optional<GraphCheckpoint> get(String checkpointId);

    /**
     * Retrieve a checkpoint by its step number within the current session.
     */
    Optional<GraphCheckpoint> getByStep(int step);

    /**
     * Get the latest (most recent) checkpoint for the current session.
     */
    Optional<GraphCheckpoint> getLatest();

    /**
     * List checkpoints, optionally filtered.
     * @param before step number to filter on (exclusive), or null
     * @param limit  max results, or null
     */
    List<GraphCheckpoint> list(Integer before, Integer limit);

    /**
     * Delete all checkpoints for the current session.
     */
    void clear();

    /** Number of stored checkpoints. */
    int size();

    @Override
    void close();
}
