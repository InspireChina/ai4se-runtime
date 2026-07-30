package com.ai4se.runtime.engine.service;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.engine.support.Ids;
import com.ai4se.runtime.engine.support.WorkResultMapper;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.artifact.ArtifactLifecycle;
import com.ai4se.runtime.kernel.artifact.ArtifactStore;
import com.ai4se.runtime.kernel.artifact.ArtifactStorage;
import com.ai4se.runtime.kernel.artifact.ArtifactStorageKind;
import com.ai4se.runtime.kernel.artifact.ProducedBy;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.Worker;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Artifact Lifecycle Service — propose → commit / abandon (A3–A5).
 */
public class ArtifactLifecycleService {

    private final ArtifactStore store;

    public ArtifactLifecycleService(ArtifactStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Success path: PROPOSED → COMMITTED (Context indexing stays on ContextLifecycleService). */
    public Artifact commitFromWorkResult(
            Task task,
            WorkItemId workItemId,
            Worker worker,
            WorkResult workResult) {
        Artifact proposed = proposeFromWorkResult(task, workItemId, worker, workResult);
        Artifact committed = commit(proposed.artifactId());
        return requireCommitted(committed.artifactId());
    }

    /** Failure path: PROPOSED → ABANDONED (never COMMITTED / never indexed). */
    public Artifact abandonFromWorkResult(
            Task task,
            WorkItemId workItemId,
            Worker worker,
            WorkResult workResult) {
        Artifact proposed = proposeFromWorkResult(task, workItemId, worker, workResult);
        return abandon(proposed.artifactId());
    }

    public Artifact proposeWorkerOutput(
            TaskId taskId,
            WorkItemId workItemId,
            WorkerId workerId,
            String kind,
            String name,
            String inlinePayload,
            Map<String, String> metadata) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(workItemId, "workItemId");
        Objects.requireNonNull(workerId, "workerId");
        String payload = (inlinePayload == null || inlinePayload.isEmpty()) ? "(empty)" : inlinePayload;
        Map<String, String> labels = metadata == null
                ? Collections.<String, String>emptyMap()
                : new HashMap<String, String>(metadata);

        Artifact proposed = Artifact.builder()
                .artifactId(new ArtifactId(Ids.next("art")))
                .taskId(taskId)
                .kind(kind == null ? "worker.output" : kind)
                .name(name == null ? "worker-output" : name)
                .mediaType("text/plain")
                .storage(new ArtifactStorage(ArtifactStorageKind.INLINE, payload))
                .producedBy(new ProducedBy(
                        workItemId,
                        workerId,
                        Optional.<String>empty(),
                        Optional.<String>empty()))
                .labels(labels)
                .build();

        if (proposed.lifecycle() != ArtifactLifecycle.PROPOSED) {
            throw new IllegalStateException("New artifact must start as PROPOSED");
        }
        return store.put(proposed);
    }

    public Artifact commit(ArtifactId artifactId) {
        Artifact artifact = require(artifactId);
        artifact.commit();
        return store.put(artifact);
    }

    public Artifact abandon(ArtifactId artifactId) {
        Artifact artifact = require(artifactId);
        artifact.abandon();
        return store.put(artifact);
    }

    public Artifact requireCommitted(ArtifactId artifactId) {
        Artifact artifact = require(artifactId);
        if (artifact.lifecycle() != ArtifactLifecycle.COMMITTED) {
            throw new IllegalStateException(
                    "Artifact not COMMITTED: " + artifactId + " lifecycle=" + artifact.lifecycle());
        }
        return artifact;
    }

    public Optional<Artifact> get(ArtifactId artifactId) {
        return store.get(artifactId);
    }

    private Artifact proposeFromWorkResult(
            Task task, WorkItemId workItemId, Worker worker, WorkResult workResult) {
        String kind = com.ai4se.runtime.engine.support.StageGate.outputKind(task.goal().getType());
        return proposeWorkerOutput(
                task.taskId(),
                workItemId,
                worker.workerId(),
                kind,
                WorkResultMapper.artifactName(workResult, kind),
                WorkResultMapper.payload(workResult),
                WorkResultMapper.metadata(task, worker, workResult));
    }

    private Artifact require(ArtifactId artifactId) {
        Optional<Artifact> found = store.get(artifactId);
        if (!found.isPresent()) {
            throw new IllegalArgumentException("Unknown artifact: " + artifactId);
        }
        return found.get();
    }
}
