package com.ai4se.orchestration.workflow;

import java.util.Objects;

/** In-memory snapshot of a Story workflow. */
public final class StoryWorkflowState {

    private final String storyId;
    private final WorkflowStage stage;
    private final WorkflowStatus status;
    private final String stopReason;

    public StoryWorkflowState(
            String storyId,
            WorkflowStage stage,
            WorkflowStatus status,
            String stopReason) {
        this.storyId = Objects.requireNonNull(storyId, "storyId");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.status = Objects.requireNonNull(status, "status");
        this.stopReason = stopReason;
    }

    public String storyId() {
        return storyId;
    }

    public WorkflowStage stage() {
        return stage;
    }

    public WorkflowStatus status() {
        return status;
    }

    public String stopReason() {
        return stopReason;
    }

    public boolean isRunnable() {
        return status == WorkflowStatus.RUNNING;
    }
}
