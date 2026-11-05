package com.ai4se.runtime.kernel.checkpoint;

import com.ai4se.runtime.common.id.CheckpointId;
import com.ai4se.runtime.common.id.TaskId;
import java.time.Instant;
import java.util.Objects;

/**
 * Kernel Checkpoint — durable Task slice at a trusted boundary (C1–C5).
 * Immutable once created; does not participate in scheduling or resume (Sprint-5).
 */
public final class Checkpoint {

    private final CheckpointId checkpointId;
    private final TaskId taskId;
    private final long sequence;
    private final Instant createdAt;
    private final ArtifactSnapshot artifactSnapshot;
    private final long contextRevision;
    private final CheckpointIntegrity integrity;

    public Checkpoint(
            CheckpointId checkpointId,
            TaskId taskId,
            long sequence,
            Instant createdAt,
            ArtifactSnapshot artifactSnapshot,
            long contextRevision,
            CheckpointIntegrity integrity) {
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        this.checkpointId = Objects.requireNonNull(checkpointId, "checkpointId");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.sequence = sequence;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.artifactSnapshot = artifactSnapshot == null ? ArtifactSnapshot.empty() : artifactSnapshot;
        this.contextRevision = contextRevision;
        this.integrity = Objects.requireNonNull(integrity, "integrity");
    }

    public CheckpointId checkpointId() {
        return checkpointId;
    }

    public TaskId taskId() {
        return taskId;
    }

    public long sequence() {
        return sequence;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ArtifactSnapshot artifactSnapshot() {
        return artifactSnapshot;
    }

    public long contextRevision() {
        return contextRevision;
    }

    public CheckpointIntegrity integrity() {
        return integrity;
    }
}
