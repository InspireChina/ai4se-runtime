package com.ai4se.runtime.workers;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.WorkResultStatus;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.worker.api.WorkerDescriptor;
import com.ai4se.runtime.worker.api.WorkerHealth;
import com.ai4se.runtime.worker.api.WorkerKind;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint-6 FakeWorker / NoopWorker — no business logic; always OK.
 * Signals Runtime to materialize Artifact named {@code hello.txt}.
 */
public final class NoopWorker implements Worker {

    public static final WorkerId ID = new WorkerId("worker_noop");
    public static final String ARTIFACT_NAME = "hello.txt";
    public static final String HELLO_PAYLOAD = "hello\n";
    public static final String FIXED_MESSAGE = "noop-ok";

    private final WorkerDescriptor descriptor;

    public NoopWorker() {
        this.descriptor = new WorkerDescriptor(
                ID,
                WorkerKind.CUSTOM,
                "NoopWorker",
                "0.1.0",
                Collections.singletonList("noop.execute"),
                1,
                Collections.singleton("cpu"),
                true);
    }

    @Override
    public WorkerId workerId() {
        return ID;
    }

    @Override
    public WorkerDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public WorkerHealth health() {
        return WorkerHealth.UP;
    }

    @Override
    public WorkResult execute(WorkRequest request, ExecutionContextView context) {
        if (request == null || context == null) {
            throw new IllegalArgumentException("request and context required");
        }
        if (!request.getTaskId().equals(context.taskId())) {
            throw new IllegalArgumentException("WorkRequest.taskId must match ExecutionContext.taskId");
        }
        Map<String, Object> metrics = new HashMap<String, Object>();
        metrics.put("workerName", "NoopWorker");
        metrics.put("command", request.getOperation() == null ? "noop" : request.getOperation());
        metrics.put("exitCode", Integer.valueOf(0));
        metrics.put("durationMs", Long.valueOf(0L));
        metrics.put("stdout", HELLO_PAYLOAD);
        metrics.put("stderr", "");
        metrics.put("artifactName", ARTIFACT_NAME);
        return new WorkResult(
                WorkResultStatus.OK,
                Collections2.<ArtifactId>emptyList(),
                Optional.<com.ai4se.runtime.common.error.ReasonCode>empty(),
                Optional.of(FIXED_MESSAGE),
                metrics);
    }
}
