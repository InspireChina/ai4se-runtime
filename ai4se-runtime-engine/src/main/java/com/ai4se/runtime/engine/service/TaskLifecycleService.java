package com.ai4se.runtime.engine.service;

import com.ai4se.runtime.common.error.ErrorRef;
import com.ai4se.runtime.common.error.ErrorTaxonomy;
import com.ai4se.runtime.common.error.ReasonCode;
import com.ai4se.runtime.common.id.CheckpointId;
import com.ai4se.runtime.common.id.ExecutionContextId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.support.Ids;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.worker.api.WorkResult;
import java.util.Objects;

/**
 * Task Lifecycle Service — sole mutation entry for Task aggregate.
 */
public class TaskLifecycleService {

    public Task create(RuntimeRequest request) {
        Objects.requireNonNull(request, "request");
        return Task.builder()
                .taskId(new TaskId(Ids.next("task")))
                .projectId(request.getProjectId())
                .profileId(request.getProfileId())
                .profileRevision(request.getProfileRevision())
                .workflowId(request.getWorkflowId())
                .goal(request.getGoal())
                .workspaceRef(request.getWorkspaceRef())
                .runMode(request.getRunMode())
                .budget(request.getBudget())
                .permissions(request.getPermissions())
                .labels(request.getLabels())
                .build();
    }

    /** CREATED → VALIDATING → QUEUED → SCHEDULED → STARTING */
    public void advanceToStarting(Task task) {
        Objects.requireNonNull(task, "task");
        transition(task, TaskStatus.VALIDATING);
        requireReadyForQueue(task);
        transition(task, TaskStatus.QUEUED);
        transition(task, TaskStatus.SCHEDULED);
        transition(task, TaskStatus.STARTING);
    }

    public void markRunning(Task task) {
        transition(task, TaskStatus.RUNNING);
    }

    public void succeed(Task task) {
        transition(task, TaskStatus.SUCCEEDED);
    }

    public void fail(Task task, WorkResult workResult) {
        Objects.requireNonNull(workResult, "workResult");
        ReasonCode code = workResult.getReasonCode().orElse(new ReasonCode("WORKER_FAIL"));
        markError(task, new ErrorRef(
                ErrorTaxonomy.FATAL,
                code,
                workResult.getMessage().orElse("worker failed")));
        transition(task, TaskStatus.FAILED);
    }

    public void transition(Task task, TaskStatus next) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(next, "next");
        task.transitionTo(next);
    }

    public void bindExecutionContext(Task task, ExecutionContextId contextId) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(contextId, "contextId");
        task.bindExecutionContext(contextId);
    }

    public void bindTraceId(Task task, String traceId) {
        Objects.requireNonNull(task, "task");
        task.bindTraceId(traceId);
    }

    public void markError(Task task, ErrorRef error) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(error, "error");
        task.markError(error);
    }

    /** Updates Task.latestCheckpointId (C4). Must be called while Task is non-terminal. */
    public void markCheckpoint(Task task, CheckpointId checkpointId) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(checkpointId, "checkpointId");
        task.markCheckpoint(checkpointId.value());
    }

    private static void requireReadyForQueue(Task task) {
        if (task.goal() == null || task.workspaceRef() == null) {
            throw new IllegalStateException("Task missing goal or workspace");
        }
    }
}
