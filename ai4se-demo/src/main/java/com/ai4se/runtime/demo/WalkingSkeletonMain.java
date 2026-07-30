package com.ai4se.runtime.demo;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.engine.Runtime;
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
import com.ai4se.runtime.workers.NoopWorker;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Sprint-6 Walking Skeleton Demo — FakeTask + NoopWorker.
 * <pre>
 * Task → Validate → Context → Trace → NoopWorker → Artifact(hello.txt)
 *   → Commit → Checkpoint → Task Success
 * </pre>
 * {@code mvn -pl ai4se-demo -am exec:java}
 */
public final class WalkingSkeletonMain {

    private WalkingSkeletonMain() {
    }

    public static void main(String[] args) {
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

        RuntimeResult result = runtime.submit(fakeTaskRequest());

        System.out.println("=== Sprint-6 Walking Skeleton (NoopWorker) ===");
        System.out.println("--- Lifecycle ---");
        for (String event : result.getLifecycleEvents()) {
            System.out.println("  " + event);
        }

        Optional<Checkpoint> checkpoint = checkpointStore.latest(result.getTaskId());
        Artifact artifact = result.getCommittedArtifactIds().isEmpty()
                ? null
                : artifactStore.get(result.getCommittedArtifactIds().get(0)).orElse(null);

        System.out.println("--- Assertions ---");
        boolean ok = true;
        ok &= check("Task succeeded", result.isSuccess() && result.getTaskStatus() == TaskStatus.SUCCEEDED);
        ok &= check("ExecutionContext bound + frozen",
                result.getExecutionContextId() != null
                        && result.getContextPhase() == ExecutionContextPhase.FROZEN);
        ok &= check("Artifact COMMITTED as hello.txt",
                artifact != null
                        && artifact.lifecycle() == ArtifactLifecycle.COMMITTED
                        && NoopWorker.ARTIFACT_NAME.equals(artifact.name()));
        ok &= check("Checkpoint generated",
                checkpoint.isPresent()
                        && checkpoint.get().sequence() >= 1L
                        && !checkpoint.get().integrity().digest().isEmpty());
        ok &= check("Trace complete (submit/worker/artifact/finish + closed)",
                result.getTraceSpanNames().equals(Arrays.asList(
                        TraceRoot.SPAN_SUBMIT,
                        TraceRoot.SPAN_WORKER,
                        TraceRoot.SPAN_ARTIFACT,
                        TraceRoot.SPAN_FINISH))
                        && traces.get(result.getTraceId()).isPresent()
                        && traces.get(result.getTraceId()).get().closed());

        System.out.println("--- Result ---");
        System.out.println("success        = " + result.isSuccess());
        System.out.println("taskId         = " + result.getTaskId());
        System.out.println("taskStatus     = " + result.getTaskStatus());
        System.out.println("contextId      = " + result.getExecutionContextId());
        System.out.println("contextPhase   = " + result.getContextPhase());
        System.out.println("traceId        = " + result.getTraceId());
        System.out.println("traceSpans     = " + result.getTraceSpanNames());
        System.out.println("artifactId     = " + result.getCommittedArtifactIds());
        if (artifact != null) {
            System.out.println("artifactName   = " + artifact.name());
            System.out.println("artifactLife   = " + artifact.lifecycle());
            System.out.println("artifactBody   = " + artifact.storage().getLocator().trim());
        }
        if (checkpoint.isPresent()) {
            System.out.println("checkpointId   = " + checkpoint.get().checkpointId());
            System.out.println("cp.sequence    = " + checkpoint.get().sequence());
        }
        System.out.println("message        = " + result.getMessage());

        if (!ok) {
            System.err.println("Walking Skeleton FAILED — Kernel path incomplete.");
            System.exit(1);
        }
        System.out.println("Walking Skeleton OK — Kernel drives full lifecycle.");
    }

    static RuntimeRequest fakeTaskRequest() {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("sprint6.walking-skeleton")
                .goal(new GoalSpec(
                        "NOOP",
                        "acc-sprint6",
                        Collections2.<ArtifactId>emptyList(),
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef(new File("").getAbsolutePath(), Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    private static boolean check(String label, boolean pass) {
        System.out.println((pass ? "  [OK] " : "  [FAIL] ") + label);
        return pass;
    }
}
