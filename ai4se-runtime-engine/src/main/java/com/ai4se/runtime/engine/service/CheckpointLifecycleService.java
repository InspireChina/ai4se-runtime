package com.ai4se.runtime.engine.service;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.CheckpointId;
import com.ai4se.runtime.engine.support.Ids;
import com.ai4se.runtime.kernel.checkpoint.ArtifactSnapshot;
import com.ai4se.runtime.kernel.checkpoint.Checkpoint;
import com.ai4se.runtime.kernel.checkpoint.CheckpointIntegrity;
import com.ai4se.runtime.kernel.checkpoint.CheckpointStore;
import com.ai4se.runtime.kernel.context.ExecutionContext;
import com.ai4se.runtime.kernel.task.Task;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Checkpoint Lifecycle Service — create/save only (no Resume / Recovery).
 * Does not advance Task status machine (no CHECKPOINTING).
 */
public class CheckpointLifecycleService {

    private final CheckpointStore store;

    public CheckpointLifecycleService(CheckpointStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public CheckpointStore store() {
        return store;
    }

    /**
     * Creates an immutable Checkpoint for the Task's current Artifact index.
     * Caller must still be non-terminal if updating {@link Task#latestCheckpointId()}.
     */
    public Checkpoint createAtBoundary(Task task, ExecutionContext context) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(context, "context");
        if (!task.taskId().equals(context.taskId())) {
            throw new IllegalArgumentException("Context taskId must match Task");
        }

        long sequence = nextSequence(task.taskId());
        List<ArtifactId> ids = new ArrayList<ArtifactId>(context.listArtifactIds());
        ArtifactSnapshot snapshot = ArtifactSnapshot.of(ids);
        long contextRevision = computeContextRevision(context);
        CheckpointId checkpointId = new CheckpointId(Ids.next("cp"));
        Instant createdAt = Instant.now();
        CheckpointIntegrity integrity = new CheckpointIntegrity(
                digest(checkpointId, task.taskId().value(), sequence, snapshot, contextRevision));

        Checkpoint checkpoint = new Checkpoint(
                checkpointId,
                task.taskId(),
                sequence,
                createdAt,
                snapshot,
                contextRevision,
                integrity);
        store.save(checkpoint);
        return checkpoint;
    }

    public Optional<Checkpoint> load(CheckpointId checkpointId) {
        return store.load(checkpointId);
    }

    public Optional<Checkpoint> latest(com.ai4se.runtime.common.id.TaskId taskId) {
        return store.latest(taskId);
    }

    public List<Checkpoint> list(com.ai4se.runtime.common.id.TaskId taskId) {
        return store.list(taskId);
    }

    private long nextSequence(com.ai4se.runtime.common.id.TaskId taskId) {
        Optional<Checkpoint> latest = store.latest(taskId);
        if (!latest.isPresent()) {
            return 1L;
        }
        return latest.get().sequence() + 1L;
    }

    private static long computeContextRevision(ExecutionContext context) {
        // Foundation: revision derived from indexed size + phase ordinal (no separate counter yet).
        return ((long) context.phase().ordinal() << 32) | (context.listArtifactIds().size() & 0xffffffffL);
    }

    private static String digest(
            CheckpointId checkpointId,
            String taskId,
            long sequence,
            ArtifactSnapshot snapshot,
            long contextRevision) {
        StringBuilder payload = new StringBuilder();
        payload.append(checkpointId.value()).append('|')
                .append(taskId).append('|')
                .append(sequence).append('|')
                .append(contextRevision).append('|');
        List<String> ids = new ArrayList<String>();
        for (ArtifactId id : snapshot.artifactIds()) {
            ids.add(id.value());
        }
        Collections.sort(ids);
        for (String id : ids) {
            payload.append(id).append(',');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.toString().getBytes(Charset.forName("UTF-8")));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", Integer.valueOf(b & 0xff)));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute checkpoint integrity", ex);
        }
    }
}
