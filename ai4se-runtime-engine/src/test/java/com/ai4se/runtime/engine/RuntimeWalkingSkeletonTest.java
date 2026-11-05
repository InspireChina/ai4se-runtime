package com.ai4se.runtime.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.error.ReasonCode;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.WorkerId;
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
import com.ai4se.runtime.kernel.context.ExecutionContext;
import com.ai4se.runtime.kernel.context.ExecutionContextPhase;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.WorkResultStatus;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.worker.api.WorkerDescriptor;
import com.ai4se.runtime.worker.api.WorkerHealth;
import com.ai4se.runtime.worker.api.WorkerKind;
import com.ai4se.runtime.workers.MockWorker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class RuntimeWalkingSkeletonTest {

    @Test
    void submitHappyPath_fullVerticalSlice() {
        ContextLifecycleService contexts = new ContextLifecycleService();
        TraceLifecycleService traces = new TraceLifecycleService();
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                contexts,
                new ArtifactLifecycleService(store),
                traces,
                new MockWorker());

        RuntimeResult result = runtime.submit(sampleRequest());

        assertTrue(result.isSuccess());
        assertEquals(TaskStatus.SUCCEEDED, result.getTaskStatus());
        assertEquals(ExecutionContextPhase.FROZEN, result.getContextPhase());
        assertEquals(
                Arrays.asList(
                        TraceRoot.SPAN_SUBMIT,
                        TraceRoot.SPAN_WORKER,
                        TraceRoot.SPAN_ARTIFACT,
                        TraceRoot.SPAN_FINISH),
                result.getTraceSpanNames());

        Artifact artifact = store.get(result.getCommittedArtifactIds().get(0)).get();
        assertEquals(ArtifactLifecycle.COMMITTED, artifact.lifecycle());
        assertTrue(artifact.labels().containsKey("timestamp"));
    }

    @Test
    void submit_followsLegalTaskStatusSequenceViaLifecycleService() {
        RecordingTaskLifecycleService tasks = new RecordingTaskLifecycleService();
        Runtime runtime = new Runtime(
                tasks,
                new ContextLifecycleService(),
                new ArtifactLifecycleService(new InMemoryArtifactStore()),
                new TraceLifecycleService(),
                new MockWorker());

        runtime.submit(sampleRequest());

        assertEquals(
                Arrays.asList(
                        TaskStatus.VALIDATING,
                        TaskStatus.QUEUED,
                        TaskStatus.SCHEDULED,
                        TaskStatus.STARTING,
                        TaskStatus.RUNNING,
                        TaskStatus.SUCCEEDED),
                tasks.transitions);
    }

    @Test
    void contextPhases_materializingThenActiveThenFrozen() {
        ContextLifecycleService contexts = new ContextLifecycleService();
        ExecutionContext context = contexts.materialize(
                new com.ai4se.runtime.common.id.ExecutionContextId("ctx_phase"),
                new com.ai4se.runtime.common.id.TaskId("task_phase"),
                "profile");
        assertEquals(ExecutionContextPhase.MATERIALIZING, context.phase());
        contexts.activate(context);
        assertEquals(ExecutionContextPhase.ACTIVE, context.phase());
        contexts.freeze(context);
        assertEquals(ExecutionContextPhase.FROZEN, context.phase());
    }

    @Test
    void indexOnlyAllowedInActivePhase() {
        ContextLifecycleService contexts = new ContextLifecycleService();
        final ExecutionContext context = contexts.materialize(
                new com.ai4se.runtime.common.id.ExecutionContextId("ctx_idx"),
                new com.ai4se.runtime.common.id.TaskId("task_idx"),
                "profile");
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                contexts.indexCommitted(
                        context, new ArtifactId("art_x"), ArtifactLifecycle.COMMITTED);
            }
        });
        contexts.activate(context);
        contexts.indexCommitted(context, new ArtifactId("art_ok"), ArtifactLifecycle.COMMITTED);
    }

    @Test
    void submit_whenWorkerFails_abandonsArtifactAndClosesTrace() {
        TraceLifecycleService traces = new TraceLifecycleService();
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                new ArtifactLifecycleService(store),
                traces,
                new FailingWorker());

        RuntimeResult result = runtime.submit(sampleRequest());

        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.getTaskStatus());
        assertTrue(result.getCommittedArtifactIds().isEmpty());
        assertTrue(result.getTraceSpanNames().contains(TraceRoot.SPAN_FINISH));
        assertTrue(traces.get(result.getTraceId()).get().closed());
        assertEquals(ArtifactLifecycle.ABANDONED,
                store.listByTask(result.getTaskId()).get(0).lifecycle());
    }

    @Test
    void artifactLifecycle_proposeCommitAndAbandon() {
        ArtifactLifecycleService artifacts = new ArtifactLifecycleService(new InMemoryArtifactStore());
        Task task = new TaskLifecycleService().create(sampleRequest());
        Artifact proposed = artifacts.proposeWorkerOutput(
                task.taskId(),
                new com.ai4se.runtime.common.id.WorkItemId("wi_1"),
                MockWorker.ID,
                "worker.output",
                "worker-output",
                "payload",
                new HashMap<String, String>());
        assertEquals(ArtifactLifecycle.PROPOSED, proposed.lifecycle());
        assertEquals(ArtifactLifecycle.COMMITTED, artifacts.commit(proposed.artifactId()).lifecycle());

        Artifact other = artifacts.proposeWorkerOutput(
                task.taskId(),
                new com.ai4se.runtime.common.id.WorkItemId("wi_2"),
                MockWorker.ID,
                "worker.output",
                "worker-output",
                "fail",
                new HashMap<String, String>());
        assertEquals(ArtifactLifecycle.ABANDONED, artifacts.abandon(other.artifactId()).lifecycle());
    }

    private static RuntimeRequest sampleRequest() {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("walking.skeleton")
                .goal(new GoalSpec(
                        "WALKING_SKELETON",
                        "acc-skeleton",
                        Collections2.<ArtifactId>emptyList(),
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef("/tmp/ai4se-ws", Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    static final class RecordingTaskLifecycleService extends TaskLifecycleService {
        final List<TaskStatus> transitions = new ArrayList<TaskStatus>();

        @Override
        public void transition(Task task, TaskStatus next) {
            super.transition(task, next);
            transitions.add(next);
        }
    }

    static final class FailingWorker implements Worker {
        @Override
        public WorkerId workerId() {
            return new WorkerId("worker_fail");
        }

        @Override
        public WorkerDescriptor descriptor() {
            return new WorkerDescriptor(
                    workerId(),
                    WorkerKind.CUSTOM,
                    "FailingWorker",
                    "0.1.0",
                    Collections.singletonList("mock.execute"),
                    1,
                    Collections.singleton("cpu"),
                    true);
        }

        @Override
        public WorkerHealth health() {
            return WorkerHealth.UP;
        }

        @Override
        public WorkResult execute(WorkRequest request, ExecutionContextView context) {
            java.util.Map<String, Object> metrics = new HashMap<String, Object>();
            metrics.put("workerName", "FailingWorker");
            metrics.put("command", request.getOperation());
            metrics.put("exitCode", Integer.valueOf(1));
            metrics.put("durationMs", Long.valueOf(1L));
            metrics.put("stdout", "");
            metrics.put("stderr", "intentional-fail");
            return new WorkResult(
                    WorkResultStatus.FATAL_FAIL,
                    Collections2.<ArtifactId>emptyList(),
                    Optional.of(new ReasonCode("MOCK_FATAL")),
                    Optional.of("intentional-fail"),
                    metrics);
        }
    }
}
