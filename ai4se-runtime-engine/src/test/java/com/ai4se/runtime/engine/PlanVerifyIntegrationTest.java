package com.ai4se.runtime.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.error.ReasonCode;
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
import com.ai4se.runtime.worker.api.WorkResultStatus;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.worker.api.WorkerDescriptor;
import com.ai4se.runtime.worker.api.WorkerHealth;
import com.ai4se.runtime.worker.api.WorkerKind;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Playbook S7 — VERIFY reads plan.test-strategy; skip/fail/missing acceptance ⇒ not SUCCEEDED. */
class PlanVerifyIntegrationTest {

    @Test
    void skipCommand_failsWithoutWorker_andCannotPretendSuccess() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        Artifact strategy = commitStrategy(arts, "command: skip\nacceptance: A1\n");
        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = runtime(arts, new ScriptedVerifyWorker(calls));

        RuntimeResult result = runtime.submit(request(Collections.singletonList(strategy.artifactId())));
        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.getTaskStatus());
        assertTrue(result.getMessage().contains("not run") || result.getMessage().contains("skip"));
        assertEquals(0, calls.get());
    }

    @Test
    void redCommand_exitFail_taskFailed() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        Artifact strategy = commitStrategy(arts, "command: fail-tests\nacceptance: A1\n");
        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = runtime(arts, new ScriptedVerifyWorker(calls));

        RuntimeResult result = runtime.submit(request(Collections.singletonList(strategy.artifactId())));
        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.getTaskStatus());
        assertEquals(1, calls.get());
        assertFalse(result.isSuccess(), "exit≠0 must not SUCCEEDED");
    }

    @Test
    void greenCommand_citesAcceptance_succeeds() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        Artifact strategy = commitStrategy(arts, "command: pass-tests\nacceptance: A1, A2\n");
        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = runtime(arts, new ScriptedVerifyWorker(calls));

        RuntimeResult result = runtime.submit(request(Collections.singletonList(strategy.artifactId())));
        assertTrue(result.isSuccess());
        assertEquals(TaskStatus.SUCCEEDED, result.getTaskStatus());
        assertEquals(1, calls.get());
    }

    @Test
    void greenButMissingAcceptanceCitation_fails() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        Artifact strategy = commitStrategy(arts, "command: pass-silent\nacceptance: A1\n");
        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = runtime(arts, new ScriptedVerifyWorker(calls));

        RuntimeResult result = runtime.submit(request(Collections.singletonList(strategy.artifactId())));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("acceptance"));
        assertEquals(1, calls.get());
    }

    private static Runtime runtime(ArtifactLifecycleService arts, Worker worker) {
        return new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                arts,
                new TraceLifecycleService(),
                worker);
    }

    private static Artifact commitStrategy(ArtifactLifecycleService arts, String body) {
        Artifact a = arts.proposeWorkerOutput(
                new TaskId("seed"),
                new WorkItemId("wi-strategy"),
                new WorkerId("worker_seed"),
                StageGate.KIND_PLAN_TEST,
                "plan-test-strategy",
                body,
                Collections.<String, String>emptyMap());
        return arts.commit(a.artifactId());
    }

    private static RuntimeRequest request(java.util.List<ArtifactId> inputs) {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("s7.verify")
                .goal(new GoalSpec(
                        "VERIFY",
                        "acc-s7",
                        inputs,
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef("/tmp/ai4se-ws", Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    /** Interprets plan-injected command: fail-tests | pass-tests | pass-silent. */
    static final class ScriptedVerifyWorker implements Worker {
        private final AtomicInteger calls;

        ScriptedVerifyWorker(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public WorkerId workerId() {
            return new WorkerId("worker_verify_script");
        }

        @Override
        public WorkerDescriptor descriptor() {
            return new WorkerDescriptor(
                    workerId(),
                    WorkerKind.CUSTOM,
                    "ScriptedVerifyWorker",
                    "0.1.0",
                    Collections.singletonList("verify"),
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
            String command = String.valueOf(request.getParams().get("command"));
            Map<String, Object> metrics = new HashMap<String, Object>();
            metrics.put("command", command);
            if (command.contains("fail")) {
                metrics.put("exitCode", Integer.valueOf(1));
                metrics.put("stdout", "");
                return new WorkResult(
                        WorkResultStatus.FATAL_FAIL,
                        Collections2.<ArtifactId>emptyList(),
                        Optional.of(new ReasonCode("SHELL_EXIT_1")),
                        Optional.of("tests failed"),
                        metrics);
            }
            metrics.put("exitCode", Integer.valueOf(0));
            if (command.contains("silent")) {
                metrics.put("stdout", "ok");
                return new WorkResult(
                        WorkResultStatus.OK,
                        Collections2.<ArtifactId>emptyList(),
                        Optional.<ReasonCode>empty(),
                        Optional.of("ok"),
                        metrics);
            }
            metrics.put("stdout", "A1 PASS\nA2 PASS\n");
            return new WorkResult(
                    WorkResultStatus.OK,
                    Collections2.<ArtifactId>emptyList(),
                    Optional.<ReasonCode>empty(),
                    Optional.of("A1 PASS A2 PASS"),
                    metrics);
        }
    }
}
