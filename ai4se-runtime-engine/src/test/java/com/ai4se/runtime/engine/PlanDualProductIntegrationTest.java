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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Playbook S6 — serial PlanDesign → PlanTest; EXECUTION requires plan.approved + plan.test-strategy.
 */
class PlanDualProductIntegrationTest {

    @Test
    void serialDesignThenTest_thenExecutionNeedsApprove() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);

        Artifact hit = commit(arts, StageGate.KIND_HIT_SET, "hits");
        AtomicInteger designCalls = new AtomicInteger();
        AtomicInteger testCalls = new AtomicInteger();
        AtomicInteger codeCalls = new AtomicInteger();

        Runtime designRt = runtime(arts, new NamedOkWorker("design", designCalls, "design-body"));
        RuntimeResult design = designRt.submit(request("PLAN_DESIGN", Collections.singletonList(hit.artifactId())));
        assertTrue(design.isSuccess());
        assertEquals(1, designCalls.get());
        Artifact designArt = store.get(design.getCommittedArtifactIds().get(0)).get();
        assertEquals(StageGate.KIND_PLAN_DESIGN, designArt.kind());

        Runtime testRt = runtime(arts, new NamedOkWorker("test", testCalls, "test-strategy-body"));
        RuntimeResult test = testRt.submit(
                request("PLAN_TEST", Collections.singletonList(designArt.artifactId())));
        assertTrue(test.isSuccess());
        assertEquals(1, testCalls.get());
        assertEquals(1, designCalls.get(), "design must not re-run (serial, not parallel)");
        Artifact testArt = store.get(test.getCommittedArtifactIds().get(0)).get();
        assertEquals(StageGate.KIND_PLAN_TEST, testArt.kind());
        String testPayloadBefore = testArt.storage().getLocator();

        // No approve → EXECUTION rejected
        Runtime codeRt = runtime(arts, new NamedOkWorker("code", codeCalls, "patch"));
        RuntimeResult blocked = codeRt.submit(request("EXECUTION", Arrays.asList(
                designArt.artifactId(), testArt.artifactId())));
        assertFalse(blocked.isSuccess());
        assertEquals(TaskStatus.FAILED, blocked.getTaskStatus());
        assertTrue(blocked.getMessage().contains(StageGate.KIND_PLAN_APPROVED));
        assertEquals(0, codeCalls.get());

        Artifact approved = commit(arts, StageGate.KIND_PLAN_APPROVED, "human-approved");
        RuntimeResult coded = codeRt.submit(request("EXECUTION", Arrays.asList(
                designArt.artifactId(), testArt.artifactId(), approved.artifactId())));
        assertTrue(coded.isSuccess());
        assertEquals(1, codeCalls.get());
        Artifact codeOut = store.get(coded.getCommittedArtifactIds().get(0)).get();
        assertEquals("worker.output", codeOut.kind());
        assertFalse(StageGate.KIND_PLAN_TEST.equals(codeOut.kind()), "编码不得改写测试方案 kind");

        Artifact testStill = store.get(testArt.artifactId()).get();
        assertEquals(testPayloadBefore, testStill.storage().getLocator());
    }

    @Test
    void planTest_withoutDesign_rejected() {
        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = new Runtime(new NamedOkWorker("test", calls, "x"));
        RuntimeResult result = runtime.submit(request("PLAN_TEST", Collections.<ArtifactId>emptyList()));
        assertFalse(result.isSuccess());
        assertEquals(0, calls.get());
        assertTrue(result.getMessage().contains(StageGate.KIND_PLAN_DESIGN));
    }

    private static Runtime runtime(ArtifactLifecycleService arts, Worker worker) {
        return new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                arts,
                new TraceLifecycleService(),
                worker);
    }

    private static Artifact commit(ArtifactLifecycleService arts, String kind, String payload) {
        Artifact a = arts.proposeWorkerOutput(
                new TaskId("seed"),
                new WorkItemId("wi-" + kind),
                new WorkerId("worker_seed"),
                kind,
                kind,
                payload,
                Collections.<String, String>emptyMap());
        return arts.commit(a.artifactId());
    }

    private static RuntimeRequest request(String goalType, List<ArtifactId> inputs) {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("s6.dual.plan")
                .goal(new GoalSpec(
                        goalType,
                        "acc-s6",
                        inputs,
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef("/tmp/ai4se-ws", Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    static final class NamedOkWorker implements Worker {
        private final String name;
        private final AtomicInteger calls;
        private final String payload;

        NamedOkWorker(String name, AtomicInteger calls, String payload) {
            this.name = name;
            this.calls = calls;
            this.payload = payload;
        }

        @Override
        public WorkerId workerId() {
            return new WorkerId("worker_" + name);
        }

        @Override
        public WorkerDescriptor descriptor() {
            return new WorkerDescriptor(
                    workerId(),
                    WorkerKind.CUSTOM,
                    name,
                    "0.1.0",
                    Collections.singletonList(name),
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
            java.util.Map<String, Object> metrics = new java.util.HashMap<String, Object>();
            metrics.put("stdout", payload);
            return new WorkResult(
                    com.ai4se.runtime.worker.api.WorkResultStatus.OK,
                    Collections2.<ArtifactId>emptyList(),
                    Optional.<com.ai4se.runtime.common.error.ReasonCode>empty(),
                    Optional.of(payload),
                    metrics);
        }
    }
}
