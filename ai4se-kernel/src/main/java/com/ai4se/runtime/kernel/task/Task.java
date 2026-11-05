package com.ai4se.runtime.kernel.task;

import com.ai4se.runtime.common.error.ErrorRef;
import com.ai4se.runtime.common.id.ExecutionContextId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.common.util.Strings;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Task {

    private final TaskId taskId;
    private final String projectId;
    private final String profileId;
    private final String profileRevision;
    private final String workflowId;
    private final GoalSpec goal;
    private final WorkspaceRef workspaceRef;
    private final RunMode runMode;
    private final int priority;
    private final Budget budget;
    private final Set<String> permissions;
    private final Map<String, String> labels;
    private final Optional<String> idempotencyKey;
    private final Optional<TaskId> parentTaskId;
    private final Instant createdAt;

    private TaskStatus status;
    private Budget budgetRemaining;
    private Optional<ExecutionContextId> executionContextId;
    private Optional<String> latestCheckpointId;
    private Optional<String> traceId;
    private Optional<ErrorRef> lastError;
    private Instant updatedAt;

    private Task(Builder b) {
        this.taskId = Objects.requireNonNull(b.taskId, "taskId");
        this.projectId = Strings.requireNonBlank(b.projectId, "projectId");
        this.profileId = Strings.requireNonBlank(b.profileId, "profileId");
        this.profileRevision = Strings.requireNonBlank(b.profileRevision, "profileRevision");
        this.workflowId = Strings.requireNonBlank(b.workflowId, "workflowId");
        this.goal = Objects.requireNonNull(b.goal, "goal");
        this.workspaceRef = Objects.requireNonNull(b.workspaceRef, "workspaceRef");
        this.runMode = b.runMode == null ? RunMode.UNATTENDED : b.runMode;
        this.priority = b.priority;
        this.budget = Objects.requireNonNull(b.budget, "budget");
        this.permissions = Collections2.copySet(b.permissions);
        this.labels = Collections2.copyMap(b.labels);
        this.idempotencyKey = optionalNonBlank(b.idempotencyKey);
        this.parentTaskId = Optional.ofNullable(b.parentTaskId);
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;

        this.status = TaskStatus.CREATED;
        this.budgetRemaining = this.budget;
        this.executionContextId = Optional.empty();
        this.latestCheckpointId = Optional.empty();
        this.traceId = Optional.empty();
        this.lastError = Optional.empty();
        this.updatedAt = this.createdAt;
    }

    private static Optional<String> optionalNonBlank(String value) {
        if (Strings.isBlank(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public TaskId taskId() { return taskId; }
    public String projectId() { return projectId; }
    public String profileId() { return profileId; }
    public String profileRevision() { return profileRevision; }
    public String workflowId() { return workflowId; }
    public GoalSpec goal() { return goal; }
    public WorkspaceRef workspaceRef() { return workspaceRef; }
    public RunMode runMode() { return runMode; }
    public int priority() { return priority; }
    public Budget budget() { return budget; }
    public Set<String> permissions() { return permissions; }
    public Map<String, String> labels() { return labels; }
    public Optional<String> idempotencyKey() { return idempotencyKey; }
    public Optional<TaskId> parentTaskId() { return parentTaskId; }
    public Instant createdAt() { return createdAt; }
    public TaskStatus status() { return status; }
    public Budget budgetRemaining() { return budgetRemaining; }
    public Optional<ExecutionContextId> executionContextId() { return executionContextId; }
    public Optional<String> latestCheckpointId() { return latestCheckpointId; }
    public Optional<String> traceId() { return traceId; }
    public Optional<ErrorRef> lastError() { return lastError; }
    public Instant updatedAt() { return updatedAt; }

    public void transitionTo(TaskStatus next) {
        TaskTransitions.requireTransition(this.status, next);
        this.status = next;
        touch();
    }

    public void bindExecutionContext(ExecutionContextId contextId) {
        ensureNotTerminal();
        if (this.executionContextId.isPresent()) {
            throw new IllegalStateException("ExecutionContext already bound: " + this.executionContextId.get());
        }
        this.executionContextId = Optional.of(Objects.requireNonNull(contextId, "contextId"));
        touch();
    }

    public void bindTraceId(String traceId) {
        ensureNotTerminal();
        if (this.traceId.isPresent()) {
            throw new IllegalStateException("Trace already bound: " + this.traceId.get());
        }
        this.traceId = Optional.of(Strings.requireNonBlank(traceId, "traceId"));
        touch();
    }

    public void markCheckpoint(String checkpointId) {
        ensureNotTerminal();
        this.latestCheckpointId = Optional.of(Strings.requireNonBlank(checkpointId, "checkpointId"));
        touch();
    }

    public void updateBudgetRemaining(Budget remaining) {
        ensureNotTerminal();
        this.budgetRemaining = Objects.requireNonNull(remaining, "remaining");
        touch();
    }

    public void markError(ErrorRef error) {
        this.lastError = Optional.of(Objects.requireNonNull(error, "error"));
        touch();
    }

    private void ensureNotTerminal() {
        if (status.isTerminal()) {
            throw new IllegalStateException("Task is terminal: " + status);
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public static final class Builder {
        private TaskId taskId;
        private String projectId;
        private String profileId;
        private String profileRevision;
        private String workflowId;
        private GoalSpec goal;
        private WorkspaceRef workspaceRef;
        private RunMode runMode = RunMode.UNATTENDED;
        private int priority;
        private Budget budget;
        private Set<String> permissions;
        private Map<String, String> labels;
        private String idempotencyKey;
        private TaskId parentTaskId;
        private Instant createdAt;

        private Builder() {
        }

        public Builder taskId(TaskId taskId) { this.taskId = taskId; return this; }
        public Builder projectId(String projectId) { this.projectId = projectId; return this; }
        public Builder profileId(String profileId) { this.profileId = profileId; return this; }
        public Builder profileRevision(String profileRevision) { this.profileRevision = profileRevision; return this; }
        public Builder workflowId(String workflowId) { this.workflowId = workflowId; return this; }
        public Builder goal(GoalSpec goal) { this.goal = goal; return this; }
        public Builder workspaceRef(WorkspaceRef workspaceRef) { this.workspaceRef = workspaceRef; return this; }
        public Builder runMode(RunMode runMode) { this.runMode = runMode; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder budget(Budget budget) { this.budget = budget; return this; }
        public Builder permissions(Set<String> permissions) { this.permissions = permissions; return this; }
        public Builder labels(Map<String, String> labels) { this.labels = labels; return this; }
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder parentTaskId(TaskId parentTaskId) { this.parentTaskId = parentTaskId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Task build() {
            return new Task(this);
        }
    }
}
