package com.ai4se.runtime.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.engine.service.ArtifactLifecycleService;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.engine.store.InMemoryArtifactStore;
import com.ai4se.runtime.engine.support.StageGate;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.artifact.ArtifactLifecycle;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.worker.api.WorkerDescriptor;
import com.ai4se.runtime.worker.api.WorkerHealth;
import com.ai4se.runtime.worker.api.WorkerKind;
import java.time.Duration;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Playbook S5 — StageGate in Engine before Worker. */
class StageGateIntegrationTest {

    @Test
    void planWithoutHitSet_rejected_workerNotInvoked() {
        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = new Runtime(new CountingOkWorker(calls));

        RuntimeResult result = runtime.submit(request("PLAN", Collections.<ArtifactId>emptyList()));

        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.getTaskStatus());
        assertTrue(result.getMessage().contains("discovery.hit-set"));
        assertEquals(0, calls.get(), "Worker must not run when StageGate fails");
    }

    @Test
    void planWithCommittedHitSet_allowsWorker() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        Artifact hit = arts.proposeWorkerOutput(
                new TaskId("seed-task"),
                new WorkItemId("wi-seed"),
                new WorkerId("worker_seed"),
                "discovery.hit-set",
                "hit-set",
                "OrderApi.java",
                Collections.<String, String>emptyMap());
        hit = arts.commit(hit.artifactId());
        assertEquals(ArtifactLifecycle.COMMITTED, hit.lifecycle());

        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                arts,
                new TraceLifecycleService(),
                new CountingOkWorker(calls));

        RuntimeResult result = runtime.submit(
                request("PLAN", Collections.singletonList(hit.artifactId())));

        assertTrue(result.isSuccess());
        assertEquals(TaskStatus.SUCCEEDED, result.getTaskStatus());
        assertEquals(1, calls.get());
    }

    @Test
    void discoveryStage_noRequiredKinds_engineRunsWithoutWorker(@org.junit.jupiter.api.io.TempDir Path ws)
            throws Exception {
        java.nio.file.Files.write(
                ws.resolve("README.md"),
                "ok\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = new Runtime(new CountingOkWorker(calls));
        RuntimeResult result = runtime.submit(request("DISCOVERY", Collections.<ArtifactId>emptyList(), ws.toString()));
        // Empty keyword surface may BLOCKED_POLICY (gaps) or SUCCEEDED — either way Worker skipped.
        assertTrue(result.isSuccess() || result.getTaskStatus() == TaskStatus.BLOCKED_POLICY, result.getMessage());
        assertEquals(0, calls.get(), "S8a DISCOVERY is Engine Map+Search; Worker must not run");
        assertTrue(StageGate.requiredKinds("DISCOVERY").isEmpty());
        assertEquals(StageGate.KIND_HIT_SET, StageGate.outputKind("DISCOVERY"));
    }

    private static RuntimeRequest request(String goalType, java.util.List<ArtifactId> inputs) {
        return request(goalType, inputs, "/tmp/ai4se-ws");
    }

    private static RuntimeRequest request(String goalType, java.util.List<ArtifactId> inputs, String workspace) {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("s5.stage.gate")
                .goal(new GoalSpec(
                        goalType,
                        "acc-s5",
                        inputs,
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef(workspace, Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    static final class CountingOkWorker implements Worker {
        private final AtomicInteger calls;

        CountingOkWorker(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public WorkerId workerId() {
            return new WorkerId("worker_count");
        }

        @Override
        public WorkerDescriptor descriptor() {
            return new WorkerDescriptor(
                    workerId(),
                    WorkerKind.CUSTOM,
                    "CountingOkWorker",
                    "0.1.0",
                    Collections.singletonList("plan.execute"),
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
            calls.incrementAndGet();
            return WorkResult.ok(Collections2.<ArtifactId>emptyList());
        }
    }
}
