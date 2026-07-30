package com.ai4se.runtime.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.engine.Runtime;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.engine.service.ArtifactLifecycleService;
import com.ai4se.runtime.engine.service.CheckpointLifecycleService;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.engine.store.InMemoryArtifactStore;
import com.ai4se.runtime.engine.store.MemoryCheckpointStore;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.artifact.ArtifactLifecycle;
import com.ai4se.runtime.kernel.checkpoint.Checkpoint;
import com.ai4se.runtime.kernel.context.ExecutionContextPhase;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import com.ai4se.runtime.workers.NoopWorker;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Sprint-6 — proves Kernel drives: Task → Context → Trace → NoopWorker → Artifact → Checkpoint → Success.
 */
class WalkingSkeletonTest {

    @Test
    void fakeTask_fullLifecycle_viaExistingKernelObjectsOnly() {
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        MemoryCheckpointStore checkpointStore = new MemoryCheckpointStore();
        TraceLifecycleService traces = new TraceLifecycleService();

        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                new ArtifactLifecycleService(artifactStore),
                traces,
                new CheckpointLifecycleService(checkpointStore),
                new NoopWorker());

        RuntimeResult result = runtime.submit(WalkingSkeletonMain.fakeTaskRequest());

        assertTrue(result.isSuccess());
        assertEquals(TaskStatus.SUCCEEDED, result.getTaskStatus());
        assertEquals(ExecutionContextPhase.FROZEN, result.getContextPhase());
        assertTrue(result.getExecutionContextId() != null);

        assertEquals(1, result.getCommittedArtifactIds().size());
        Artifact artifact = artifactStore.get(result.getCommittedArtifactIds().get(0)).get();
        assertEquals(ArtifactLifecycle.COMMITTED, artifact.lifecycle());
        assertEquals(NoopWorker.ARTIFACT_NAME, artifact.name());
        assertTrue(artifact.storage().getLocator().contains("hello"));

        Checkpoint checkpoint = checkpointStore.latest(result.getTaskId()).get();
        assertEquals(1L, checkpoint.sequence());
        assertEquals(result.getTaskId(), checkpoint.taskId());
        assertFalse(checkpoint.integrity().digest().isEmpty());
        assertEquals(1, checkpoint.artifactSnapshot().artifactIds().size());

        assertEquals(
                Arrays.asList(
                        TraceRoot.SPAN_SUBMIT,
                        TraceRoot.SPAN_WORKER,
                        TraceRoot.SPAN_ARTIFACT,
                        TraceRoot.SPAN_FINISH),
                result.getTraceSpanNames());
        assertTrue(traces.get(result.getTraceId()).get().closed());

        assertTrue(result.getLifecycleEvents().stream().anyMatch(e -> e.startsWith("CHECKPOINT saved")));
        assertTrue(result.getLifecycleEvents().stream().anyMatch(e -> e.contains("TASK → SUCCEEDED")));
    }
}
