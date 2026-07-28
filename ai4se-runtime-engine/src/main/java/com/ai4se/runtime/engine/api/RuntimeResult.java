package com.ai4se.runtime.engine.api;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.ExecutionContextId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.kernel.context.ExecutionContextPhase;
import com.ai4se.runtime.kernel.task.TaskStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Outcome of Runtime.submit vertical slice. */
public final class RuntimeResult {

    private final TaskId taskId;
    private final ExecutionContextId executionContextId;
    private final String traceId;
    private final TaskStatus taskStatus;
    private final ExecutionContextPhase contextPhase;
    private final List<ArtifactId> committedArtifactIds;
    private final List<String> traceSpanNames;
    private final List<String> lifecycleEvents;
    private final boolean success;
    private final String message;

    public RuntimeResult(
            TaskId taskId,
            ExecutionContextId executionContextId,
            String traceId,
            TaskStatus taskStatus,
            ExecutionContextPhase contextPhase,
            List<ArtifactId> committedArtifactIds,
            List<String> traceSpanNames,
            List<String> lifecycleEvents,
            boolean success,
            String message) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.executionContextId = Objects.requireNonNull(executionContextId, "executionContextId");
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.taskStatus = Objects.requireNonNull(taskStatus, "taskStatus");
        this.contextPhase = Objects.requireNonNull(contextPhase, "contextPhase");
        this.committedArtifactIds = copyList(committedArtifactIds);
        this.traceSpanNames = copyList(traceSpanNames);
        this.lifecycleEvents = copyList(lifecycleEvents);
        this.success = success;
        this.message = message;
    }

    private static <T> List<T> copyList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public TaskId getTaskId() { return taskId; }
    public ExecutionContextId getExecutionContextId() { return executionContextId; }
    public String getTraceId() { return traceId; }
    public TaskStatus getTaskStatus() { return taskStatus; }
    public ExecutionContextPhase getContextPhase() { return contextPhase; }
    public List<ArtifactId> getCommittedArtifactIds() { return committedArtifactIds; }
    public List<String> getTraceSpanNames() { return traceSpanNames; }
    public List<String> getLifecycleEvents() { return lifecycleEvents; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
