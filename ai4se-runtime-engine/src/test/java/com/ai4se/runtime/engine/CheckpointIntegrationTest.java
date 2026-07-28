package com.ai4se.runtime.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.engine.service.ArtifactLifecycleService;
import com.ai4se.runtime.engine.service.CheckpointLifecycleService;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.engine.store.InMemoryArtifactStore;
import com.ai4se.runtime.engine.store.MemoryCheckpointStore;
import com.ai4se.runtime.kernel.artifact.ArtifactLifecycle;
import com.ai4se.runtime.kernel.checkpoint.Checkpoint;
import com.ai4se.runtime.kernel.context.ExecutionContextPhase;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import com.ai4se.runtime.workers.MockWorker;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Sprint-5 — Task → Worker → Artifact → Checkpoint → Task End.
 */
class CheckpointIntegrationTest {

    @Test
    void submit_success_createsCheckpointBeforeTaskEnd() {
        MemoryCheckpointStore checkpointStore = new MemoryCheckpointStore();
        CheckpointLifecycleService checkpoints = new CheckpointLifecycleService(checkpointStore);
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();

        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                new ArtifactLifecycleService(artifactStore),
                new TraceLifecycleService(),
                checkpoints,
                new MockWorker());

        RuntimeResult result = runtime.submit(mockRequest());

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(TaskStatus.SUCCEEDED, result.getTaskStatus());
        assertEquals(ExecutionContextPhase.FROZEN, result.getContextPhase());
        assertEquals(1, result.getCommittedArtifactIds().size());
        assertEquals(
                ArtifactLifecycle.COMMITTED,
                artifactStore.get(result.getCommittedArtifactIds().get(0)).get().lifecycle());

        Optional<Checkpoint> latest = checkpointStore.latest(result.getTaskId());
        assertTrue(latest.isPresent(), "Checkpoint must exist after Artifact");
        Checkpoint checkpoint = latest.get();
        assertEquals(result.getTaskId(), checkpoint.taskId());
        assertEquals(1L, checkpoint.sequence());
        assertEquals(1, checkpoint.artifactSnapshot().artifactIds().size());
        assertEquals(
                result.getCommittedArtifactIds().get(0),
                checkpoint.artifactSnapshot().artifactIds().get(0));
        assertFalse(checkpoint.integrity().digest().isEmpty());

        assertTrue(checkpointStore.load(checkpoint.checkpointId()).isPresent());
        List<Checkpoint> listed = checkpointStore.list(result.getTaskId());
        assertEquals(1, listed.size());
        assertEquals(checkpoint.checkpointId(), listed.get(0).checkpointId());

        assertTrue(result.getLifecycleEvents().stream().anyMatch(e -> e.startsWith("CHECKPOINT saved")));
        assertTrue(result.getLifecycleEvents().stream().anyMatch(e -> e.contains("TASK → SUCCEEDED")));

        int checkpointIdx = indexOfPrefix(result.getLifecycleEvents(), "CHECKPOINT saved");
        int succeedIdx = indexOfPrefix(result.getLifecycleEvents(), "TASK → SUCCEEDED");
        assertTrue(checkpointIdx >= 0 && succeedIdx > checkpointIdx,
                "Checkpoint must precede Task End (SUCCEEDED)");
    }

    @Test
    void submit_failure_doesNotCreateCheckpoint() {
        MemoryCheckpointStore checkpointStore = new MemoryCheckpointStore();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                new ArtifactLifecycleService(new InMemoryArtifactStore()),
                new TraceLifecycleService(),
                new CheckpointLifecycleService(checkpointStore),
                new RuntimeWalkingSkeletonTest.FailingWorker());

        RuntimeResult result = runtime.submit(mockRequest());

        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.getTaskStatus());
        assertFalse(checkpointStore.latest(result.getTaskId()).isPresent());
        assertTrue(checkpointStore.list(result.getTaskId()).isEmpty());
    }

    private static int indexOfPrefix(List<String> events, String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static RuntimeRequest mockRequest() {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("integration.checkpoint")
                .goal(new GoalSpec(
                        "MOCK",
                        "acc-cp",
                        Collections2.<ArtifactId>emptyList(),
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef(new File("").getAbsolutePath(), Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }
}
