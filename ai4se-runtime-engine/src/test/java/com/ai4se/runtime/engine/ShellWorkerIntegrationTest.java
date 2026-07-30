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
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.artifact.ArtifactLifecycle;
import com.ai4se.runtime.kernel.checkpoint.Checkpoint;
import com.ai4se.runtime.kernel.context.ExecutionContextPhase;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import com.ai4se.runtime.workers.ShellWorker;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Sprint-7 — Runtime drives a real local process via ShellWorker.
 * <pre>
 * submit → Worker.execute → Process → Artifact → Checkpoint → Trace → Task Completed
 * </pre>
 */
class ShellWorkerIntegrationTest {

    @Test
    void submit_echo_realProcess_fullLifecycle() {
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        MemoryCheckpointStore checkpointStore = new MemoryCheckpointStore();
        TraceLifecycleService traces = new TraceLifecycleService();

        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                new ArtifactLifecycleService(artifactStore),
                traces,
                new CheckpointLifecycleService(checkpointStore),
                new ShellWorker());

        RuntimeResult result = runtime.submit(shellRequest("echo hello-sprint7"));

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(TaskStatus.SUCCEEDED, result.getTaskStatus());
        assertEquals(ExecutionContextPhase.FROZEN, result.getContextPhase());

        Artifact artifact = artifactStore.get(result.getCommittedArtifactIds().get(0)).get();
        assertEquals(ArtifactLifecycle.COMMITTED, artifact.lifecycle());
        assertEquals(ShellWorker.ARTIFACT_NAME, artifact.name());
        assertTrue(artifact.storage().getLocator().contains("hello-sprint7"));
        assertEquals("0", artifact.labels().get("exitCode"));

        Checkpoint checkpoint = checkpointStore.latest(result.getTaskId()).get();
        assertEquals(1L, checkpoint.sequence());
        assertFalse(checkpoint.integrity().digest().isEmpty());

        assertEquals(
                Arrays.asList(
                        TraceRoot.SPAN_SUBMIT,
                        TraceRoot.SPAN_WORKER,
                        TraceRoot.SPAN_ARTIFACT,
                        TraceRoot.SPAN_FINISH),
                result.getTraceSpanNames());
        assertTrue(traces.get(result.getTraceId()).get().closed());
    }

    @Test
    void submit_pwd_and_gitStatus_sameRuntimeContract() {
        Runtime pwdRuntime = new Runtime(new ShellWorker());
        RuntimeResult pwd = pwdRuntime.submit(shellRequest("pwd"));
        assertTrue(pwd.isSuccess(), pwd.getMessage());
        assertFalse(pwd.getCommittedArtifactIds().isEmpty());

        Runtime gitRuntime = new Runtime(new ShellWorker());
        RuntimeResult git = gitRuntime.submit(shellRequest("git status"));
        assertTrue(git.isSuccess(), git.getMessage());
        assertEquals(pwd.getTraceSpanNames(), git.getTraceSpanNames());
    }

    @Test
    void submit_disallowedCommand_failsWithoutCommit() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                new ArtifactLifecycleService(store),
                new TraceLifecycleService(),
                new CheckpointLifecycleService(new MemoryCheckpointStore()),
                new ShellWorker());

        RuntimeResult result = runtime.submit(shellRequest("rm -rf /"));

        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.getTaskStatus());
        assertTrue(result.getCommittedArtifactIds().isEmpty());
        boolean abandoned = false;
        for (Artifact a : store.listByTask(result.getTaskId())) {
            if (a.lifecycle() == ArtifactLifecycle.ABANDONED) {
                abandoned = true;
            }
        }
        assertTrue(abandoned);
    }

    private static RuntimeRequest shellRequest(String command) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("command", command);
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("sprint7.shell")
                .goal(new GoalSpec(
                        "SHELL",
                        "acc-shell",
                        Collections2.<ArtifactId>emptyList(),
                        params))
                .workspaceRef(new WorkspaceRef(new File("").getAbsolutePath(), Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }
}
