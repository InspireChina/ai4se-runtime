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
import com.ai4se.runtime.workers.ShellWorker;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint-7 Demo — Runtime.submit drives real local shell via {@link ShellWorker}.
 * <pre>
 * mvn -pl ai4se-demo -am install -DskipTests
 * mvn -pl ai4se-demo exec:java -Dexec.mainClass=com.ai4se.runtime.demo.ShellWorkerMain
 * </pre>
 */
public final class ShellWorkerMain {

    private ShellWorkerMain() {
    }

    public static void main(String[] args) {
        String command = args != null && args.length > 0 ? args[0] : "echo hello-from-shell";

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

        RuntimeResult result = runtime.submit(request(command));

        System.out.println("=== Sprint-7 Real Worker (ShellWorker) ===");
        System.out.println("command = " + command);
        System.out.println("--- Lifecycle ---");
        for (String event : result.getLifecycleEvents()) {
            System.out.println("  " + event);
        }

        Optional<Checkpoint> checkpoint = checkpointStore.latest(result.getTaskId());
        Artifact artifact = result.getCommittedArtifactIds().isEmpty()
                ? null
                : artifactStore.get(result.getCommittedArtifactIds().get(0)).orElse(null);

        boolean ok = true;
        System.out.println("--- Assertions ---");
        ok &= check("Task completed SUCCEEDED",
                result.isSuccess() && result.getTaskStatus() == TaskStatus.SUCCEEDED);
        ok &= check("ExecutionContext frozen",
                result.getContextPhase() == ExecutionContextPhase.FROZEN);
        ok &= check("Artifact COMMITTED from real stdout",
                artifact != null
                        && artifact.lifecycle() == ArtifactLifecycle.COMMITTED
                        && ShellWorker.ARTIFACT_NAME.equals(artifact.name())
                        && !artifact.storage().getLocator().isEmpty());
        ok &= check("Checkpoint generated",
                checkpoint.isPresent() && checkpoint.get().sequence() >= 1L);
        ok &= check("Trace complete + closed",
                result.getTraceSpanNames().equals(Arrays.asList(
                        TraceRoot.SPAN_SUBMIT,
                        TraceRoot.SPAN_WORKER,
                        TraceRoot.SPAN_ARTIFACT,
                        TraceRoot.SPAN_FINISH))
                        && traces.get(result.getTraceId()).get().closed());

        System.out.println("--- Result ---");
        System.out.println("success      = " + result.isSuccess());
        System.out.println("taskStatus   = " + result.getTaskStatus());
        System.out.println("traceSpans   = " + result.getTraceSpanNames());
        if (artifact != null) {
            System.out.println("artifact     = " + artifact.artifactId() + " name=" + artifact.name());
            System.out.println("stdoutBody   = " + artifact.storage().getLocator().trim());
            System.out.println("exitCode     = " + artifact.labels().get("exitCode"));
        }
        if (checkpoint.isPresent()) {
            System.out.println("checkpointId = " + checkpoint.get().checkpointId());
        }
        System.out.println("message      = " + result.getMessage());

        if (!ok) {
            System.err.println("Sprint-7 FAILED — real worker path incomplete.");
            System.exit(1);
        }
        System.out.println("Sprint-7 OK — Runtime drove a real local process.");
    }

    private static RuntimeRequest request(String command) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("command", command);
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("sprint7.shell")
                .goal(new GoalSpec(
                        "SHELL",
                        "acc-sprint7",
                        Collections2.<ArtifactId>emptyList(),
                        params))
                .workspaceRef(new WorkspaceRef(new File("").getAbsolutePath(), Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    private static boolean check(String label, boolean pass) {
        System.out.println((pass ? "  [OK] " : "  [FAIL] ") + label);
        return pass;
    }
}
