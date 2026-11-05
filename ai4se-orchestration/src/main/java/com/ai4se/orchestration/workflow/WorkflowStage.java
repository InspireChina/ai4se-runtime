package com.ai4se.orchestration.workflow;

/** Workflow stages — control lives here, not in model utterances. */
public enum WorkflowStage {
    ANALYSIS,
    PLANNING,
    DEVELOPMENT,
    VERIFICATION,
    REVIEW,
    DELIVERY;

    public boolean canAdvanceTo(WorkflowStage next) {
        if (next == null) {
            return false;
        }
        switch (this) {
            case ANALYSIS:
                return next == PLANNING;
            case PLANNING:
                return next == DEVELOPMENT;
            case DEVELOPMENT:
                return next == VERIFICATION;
            case VERIFICATION:
                return next == REVIEW;
            case REVIEW:
                return next == DELIVERY;
            case DELIVERY:
                return false;
            default:
                return false;
        }
    }
}
