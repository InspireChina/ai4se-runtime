package com.ai4se.orchestration.workflow;

/** Story run status under Control. */
public enum WorkflowStatus {
    /** Actively in a stage; may advance or stop. */
    RUNNING,
    /** Explicit Stop with reason — legal endpoint, not pathway green. */
    STOPPED,
    /** Delivery reached (W3 skeleton does not require Commit yet). */
    COMPLETED
}
