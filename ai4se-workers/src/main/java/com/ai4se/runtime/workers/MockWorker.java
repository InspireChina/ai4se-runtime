package com.ai4se.runtime.workers;

import com.ai4se.runtime.common.context.ExecutionContextView;
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

/** Fixed-result mock worker — same WorkResult shape as CommandWorker. */
public final class MockWorker implements Worker {

    public static final WorkerId ID = new WorkerId("worker_mock");
    public static final String FIXED_MESSAGE = "mock-worker-ok";
    public static final String FIXED_PAYLOAD = "walking-skeleton-mock-result";

    private final WorkerDescriptor descriptor;

    public MockWorker() {
        this.descriptor = new WorkerDescriptor(
                ID,
                WorkerKind.CUSTOM,
                "MockWorker",
                "0.1.0",
                Collections.singletonList("mock.execute"),
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
        metrics.put("workerName", "MockWorker");
        metrics.put("command", request.getOperation());
        metrics.put("exitCode", Integer.valueOf(0));
        metrics.put("durationMs", Long.valueOf(0L));
        metrics.put("stdout", FIXED_PAYLOAD);
        metrics.put("stderr", "");
        return new WorkResult(
                WorkResultStatus.OK,
                Collections2.<com.ai4se.runtime.common.id.ArtifactId>emptyList(),
                Optional.<com.ai4se.runtime.common.error.ReasonCode>empty(),
                Optional.of(FIXED_MESSAGE),
                metrics);
    }
}
