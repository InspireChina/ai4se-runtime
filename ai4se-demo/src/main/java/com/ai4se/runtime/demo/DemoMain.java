package com.ai4se.runtime.demo;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.engine.Runtime;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import com.ai4se.runtime.workers.CommandWorker;
import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Milestone-2 Demo: Runtime.submit → CommandWorker → {@code echo hello}.
 * {@code mvn -pl ai4se-demo -am exec:java}
 */
public final class DemoMain {

    private DemoMain() {
    }

    public static void main(String[] args) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("command", "echo hello");

        RuntimeRequest request = RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("demo.command")
                .goal(new GoalSpec(
                        "COMMAND",
                        "acc-demo",
                        Collections2.<ArtifactId>emptyList(),
                        params))
                .workspaceRef(new WorkspaceRef(new File("").getAbsolutePath(), Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();

        RuntimeResult result = new Runtime(new CommandWorker()).submit(request);

        System.out.println("=== AI4SE DemoMain (CommandWorker / echo hello) ===");
        System.out.println("--- Lifecycle ---");
        for (String event : result.getLifecycleEvents()) {
            System.out.println("  " + event);
        }
        System.out.println("--- Result ---");
        System.out.println("success       = " + result.isSuccess());
        System.out.println("taskId        = " + result.getTaskId());
        System.out.println("taskStatus    = " + result.getTaskStatus());
        System.out.println("contextId     = " + result.getExecutionContextId());
        System.out.println("contextPhase  = " + result.getContextPhase());
        System.out.println("traceId       = " + result.getTraceId());
        System.out.println("traceSpans    = " + result.getTraceSpanNames());
        System.out.println("artifacts     = " + result.getCommittedArtifactIds());
        System.out.println("message       = " + result.getMessage());

        if (!result.isSuccess()) {
            System.exit(1);
        }
    }
}
