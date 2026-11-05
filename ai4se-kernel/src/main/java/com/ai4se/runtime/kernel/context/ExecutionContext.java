package com.ai4se.runtime.kernel.context;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.ExecutionContextId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.util.Strings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ExecutionContext implements ExecutionContextView {

    private final ExecutionContextId contextId;
    private final TaskId taskId;
    private final String profileId;
    private final Instant createdAt;
    private final List<ArtifactId> artifactIndex = new CopyOnWriteArrayList<ArtifactId>();
    private ExecutionContextPhase phase;

    public ExecutionContext(ExecutionContextId contextId, TaskId taskId, String profileId) {
        this(contextId, taskId, profileId, Instant.now());
    }

    public ExecutionContext(
            ExecutionContextId contextId,
            TaskId taskId,
            String profileId,
            Instant createdAt) {
        this.contextId = Objects.requireNonNull(contextId, "contextId");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.profileId = Strings.requireNonBlank(profileId, "profileId");
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.phase = ExecutionContextPhase.MATERIALIZING;
    }

    @Override
    public ExecutionContextId contextId() {
        return contextId;
    }

    @Override
    public TaskId taskId() {
        return taskId;
    }

    @Override
    public boolean frozen() {
        return phase == ExecutionContextPhase.FROZEN;
    }

    public ExecutionContextPhase phase() {
        return phase;
    }

    @Override
    public List<ArtifactId> listArtifactIds() {
        return Collections.unmodifiableList(new ArrayList<ArtifactId>(artifactIndex));
    }

    @Override
    public Optional<String> profileId() {
        return Optional.of(profileId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Materializing → Active (E2: immutable snapshot identity fixed after this). */
    public void activate() {
        if (phase != ExecutionContextPhase.MATERIALIZING) {
            throw new IllegalStateException(
                    "Cannot activate from phase " + phase + " (expected MATERIALIZING)");
        }
        this.phase = ExecutionContextPhase.ACTIVE;
    }

    public void indexArtifact(ArtifactId artifactId) {
        if (phase != ExecutionContextPhase.ACTIVE) {
            throw new IllegalStateException(
                    "Artifact index only allowed in ACTIVE phase, was " + phase);
        }
        artifactIndex.add(Objects.requireNonNull(artifactId, "artifactId"));
    }

    /** Active (or Materializing on early fail) → Frozen (E3). */
    public void freeze() {
        if (phase == ExecutionContextPhase.FROZEN) {
            throw new IllegalStateException("ExecutionContext already frozen");
        }
        // TODO(arch-conflict): Invariants list Materializing→Active→Frozen only;
        // early fail may freeze from MATERIALIZING without Active — allowed here for G4.
        this.phase = ExecutionContextPhase.FROZEN;
    }

    public ExecutionContextView asWorkerView() {
        return this;
    }
}
