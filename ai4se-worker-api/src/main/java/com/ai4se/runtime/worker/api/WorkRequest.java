package com.ai4se.runtime.worker.api;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.common.util.Strings;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorkRequest {

    private final WorkItemId workItemId;
    private final TaskId taskId;
    private final String operation;
    private final List<ArtifactId> inputArtifactIds;
    private final Map<String, Object> params;
    private final Duration timeout;
    private final Optional<String> idempotencyKey;
    private final Optional<String> traceSpanId;

    public WorkRequest(
            WorkItemId workItemId,
            TaskId taskId,
            String operation,
            List<ArtifactId> inputArtifactIds,
            Map<String, Object> params,
            Duration timeout,
            Optional<String> idempotencyKey,
            Optional<String> traceSpanId) {
        this.workItemId = Objects.requireNonNull(workItemId, "workItemId");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.operation = Strings.requireNonBlank(operation, "operation");
        this.inputArtifactIds = Collections2.copyList(inputArtifactIds);
        this.params = Collections2.copyMap(params);
        this.timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
        this.idempotencyKey = idempotencyKey == null ? Optional.<String>empty() : idempotencyKey;
        this.traceSpanId = traceSpanId == null ? Optional.<String>empty() : traceSpanId;
    }

    public WorkItemId getWorkItemId() { return workItemId; }
    public TaskId getTaskId() { return taskId; }
    public String getOperation() { return operation; }
    public List<ArtifactId> getInputArtifactIds() { return inputArtifactIds; }
    public Map<String, Object> getParams() { return params; }
    public Duration getTimeout() { return timeout; }
    public Optional<String> getIdempotencyKey() { return idempotencyKey; }
    public Optional<String> getTraceSpanId() { return traceSpanId; }
}
