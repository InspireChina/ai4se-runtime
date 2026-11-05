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
import com.ai4se.runtime.kernel.context.ExecutionContextPhase;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Playbook S4 — Task BLOCKED_POLICY until human Artifact; no pretend SUCCEEDED. */
class HumanWaitIntegrationTest {

    @Test
    void policyException_blocksUntilClarifyAnswers_thenStubSucceeds() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                new ArtifactLifecycleService(store),
                new TraceLifecycleService(),
                new PolicyExceptionWorker());

        RuntimeResult waiting = runtime.submit(sampleRequest());
        assertFalse(waiting.isSuccess());
        assertEquals(TaskStatus.BLOCKED_POLICY, waiting.getTaskStatus());
        assertEquals(ExecutionContextPhase.ACTIVE, waiting.getContextPhase());
        assertFalse(waiting.getCommittedArtifactIds().isEmpty());
        Artifact questionnaire = store.get(waiting.getCommittedArtifactIds().get(0)).get();
        assertEquals(ArtifactLifecycle.COMMITTED, questionnaire.lifecycle());

        RuntimeResult cleared = runtime.submitClarifyAnswers(
                waiting.getTaskId(), "timeout=app.order.timeout.ms=3000");
        assertTrue(cleared.isSuccess());
        assertEquals(TaskStatus.SUCCEEDED, cleared.getTaskStatus());
        Artifact answer = store.get(cleared.getCommittedArtifactIds().get(0)).get();
        assertEquals("clarification.answers", answer.kind());
        assertEquals(ArtifactLifecycle.COMMITTED, answer.lifecycle());
    }

    @Test
    void emptyAnswers_rejected_taskStaysBlocked() {
        Runtime runtime = new Runtime(new PolicyExceptionWorker());
        final RuntimeResult waiting = runtime.submit(sampleRequest());
        assertEquals(TaskStatus.BLOCKED_POLICY, waiting.getTaskStatus());

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                runtime.submitClarifyAnswers(waiting.getTaskId(), "  ");
            }
        });
        // Session still waiting — second call with real answers works
        RuntimeResult cleared = runtime.submitClarifyAnswers(waiting.getTaskId(), "ok");
        assertTrue(cleared.isSuccess());
    }

    @Test
    void approvePlan_alsoClearsHumanWait() {
        Runtime runtime = new Runtime(new PolicyExceptionWorker());
        RuntimeResult waiting = runtime.submit(sampleRequest());
        RuntimeResult cleared = runtime.approvePlan(waiting.getTaskId(), "plan approved by human");
        assertTrue(cleared.isSuccess());
        assertEquals(TaskStatus.SUCCEEDED, cleared.getTaskStatus());
    }

    private static RuntimeRequest sampleRequest() {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("s4.human.wait")
                .goal(new GoalSpec(
                        "CLARIFY",
                        "acc-s4",
                        Collections2.<ArtifactId>emptyList(),
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef("/tmp/ai4se-ws", Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    static final class PolicyExceptionWorker implements Worker {
        @Override
        public WorkerId workerId() {
            return new WorkerId("worker_policy");
        }

        @Override
        public WorkerDescriptor descriptor() {
            return new WorkerDescriptor(
                    workerId(),
                    WorkerKind.CUSTOM,
                    "PolicyExceptionWorker",
                    "0.1.0",
                    Collections.singletonList("clarify.ask"),
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
            metrics.put("workerName", "PolicyExceptionWorker");
            return new WorkResult(
                    WorkResultStatus.POLICY_EXCEPTION,
                    Collections2.<ArtifactId>emptyList(),
                    Optional.of(new ReasonCode("NEEDS_CLARIFICATION")),
                    Optional.of("Q: which timeout key?"),
                    metrics);
        }
    }
}
