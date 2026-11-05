package com.ai4se.runtime.kernel.task;

public enum TaskStatus {
    CREATED,
    VALIDATING,
    QUEUED,
    SCHEDULED,
    STARTING,
    RUNNING,
    WAITING_DEPENDENCY,
    RETRY_WAIT,
    CHECKPOINTING,
    BLOCKED_POLICY,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
