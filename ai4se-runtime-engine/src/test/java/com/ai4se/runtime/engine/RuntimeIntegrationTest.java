package com.ai4se.runtime.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.engine.service.ArtifactLifecycleService;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.engine.store.InMemoryArtifactStore;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.artifact.ArtifactLifecycle;
import com.ai4se.runtime.kernel.context.ExecutionContextPhase;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import com.ai4se.runtime.workers.MockWorker;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Sprint-4 stabilize — full pipeline via MockWorker (Worker SPI).
 * <pre>
 * submit → Task → Context → Trace → MockWorker → Artifact → RuntimeResult
 * </pre>
 */
class RuntimeIntegrationTest {

    @Test
    void submit_mockWorker_fullPipeline_toRuntimeResult() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ContextLifecycleService contexts = new ContextLifecycleService();
        TraceLifecycleService traces = new TraceLifecycleService();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                contexts,
                new ArtifactLifecycleService(store),
                traces,
                new MockWorker());

        RuntimeResult result = runtime.submit(mockRequest(new File("").getAbsolutePath()));

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(TaskStatus.SUCCEEDED, result.getTaskStatus());
        assertEquals(ExecutionContextPhase.FROZEN, result.getContextPhase());
        assertEquals(1, result.getCommittedArtifactIds().size());
        assertEquals(
                Arrays.asList(
                        TraceRoot.SPAN_SUBMIT,
                        TraceRoot.SPAN_WORKER,
                        TraceRoot.SPAN_ARTIFACT,
                        TraceRoot.SPAN_FINISH),
                result.getTraceSpanNames());

        Artifact artifact = store.get(result.getCommittedArtifactIds().get(0)).get();
        assertEquals(ArtifactLifecycle.COMMITTED, artifact.lifecycle());
        assertEquals(result.getTaskId(), artifact.taskId());
        assertTrue(artifact.labels().containsKey("taskId"));
        assertTrue(artifact.labels().containsKey("workerName"));
        assertTrue(artifact.labels().containsKey("timestamp"));
        assertFalse(artifact.storage().getLocator().isEmpty());

        assertTrue(contexts.get(result.getExecutionContextId()).isPresent());
        assertEquals(ExecutionContextPhase.FROZEN, contexts.get(result.getExecutionContextId()).get().phase());
        assertTrue(contexts.get(result.getExecutionContextId()).get().listArtifactIds()
                .contains(result.getCommittedArtifactIds().get(0)));

        assertTrue(traces.get(result.getTraceId()).isPresent());
        assertTrue(traces.get(result.getTraceId()).get().closed());
    }

    @Test
    void submit_mockWorker_failure_abandonsArtifact_andReturnsRuntimeResult() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService artifacts = new ArtifactLifecycleService(store);
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                artifacts,
                new TraceLifecycleService(),
                new RuntimeWalkingSkeletonTest.FailingWorker());

        RuntimeResult result = runtime.submit(mockRequest(new File("").getAbsolutePath()));

        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.getTaskStatus());
        assertTrue(result.getCommittedArtifactIds().isEmpty());
        assertEquals(ExecutionContextPhase.FROZEN, result.getContextPhase());
        assertTrue(result.getTraceSpanNames().contains(TraceRoot.SPAN_FINISH));

        boolean foundAbandoned = false;
        for (Artifact a : store.listByTask(result.getTaskId())) {
            if (a.lifecycle() == ArtifactLifecycle.ABANDONED) {
                foundAbandoned = true;
            }
        }
        assertTrue(foundAbandoned, "failure must leave ABANDONED artifact (not COMMITTED)");
    }

    private static RuntimeRequest mockRequest(String workdir) {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("integration.mock")
                .goal(new GoalSpec(
                        "MOCK",
                        "acc-int",
                        Collections2.<ArtifactId>emptyList(),
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef(workdir, Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }
}
