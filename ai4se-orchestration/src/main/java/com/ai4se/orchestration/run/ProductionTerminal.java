package com.ai4se.orchestration.run;

/**
 * Machine-facing production terminals (M1 PR3) with process exit codes.
 */
public enum ProductionTerminal {

    AWAITING_HUMAN_ACCEPTANCE(0),
    STOPPED_NEEDS_CLARIFICATION(20),
    STOPPED_NEEDS_PLAN_APPROVAL(21),
    FAILED_ENVIRONMENT(30),
    FAILED_ADAPTER(31),
    FAILED_VERIFICATION_BUDGET(40),
    FAILED_NO_PROGRESS(41),
    FAILED_POLICY(50);

    public final int exitCode;

    ProductionTerminal(int exitCode) {
        this.exitCode = exitCode;
    }

    public boolean isSuccess() {
        return this == AWAITING_HUMAN_ACCEPTANCE;
    }

    public static ProductionTerminal fromRunStopReason(
            com.ai4se.orchestration.control.RunStopReason reason) {
        if (reason == null) {
            return FAILED_POLICY;
        }
        switch (reason) {
            case FAILED_ENVIRONMENT:
                return FAILED_ENVIRONMENT;
            case FAILED_ADAPTER:
                return FAILED_ADAPTER;
            case FAILED_VERIFICATION_BUDGET:
                return FAILED_VERIFICATION_BUDGET;
            case FAILED_NO_PROGRESS:
                return FAILED_NO_PROGRESS;
            case FAILED_POLICY:
                return FAILED_POLICY;
            case PASSED_VERIFICATION:
                return AWAITING_HUMAN_ACCEPTANCE;
            default:
                return FAILED_POLICY;
        }
    }

    /** Best-effort map from StageGateException messages (production CLI). */
    public static ProductionTerminal fromStageGateMessage(String message) {
        String m = message == null ? "" : message;
        if (m.contains("PRODUCTION_STOP:")) {
            int start = m.indexOf("PRODUCTION_STOP:") + "PRODUCTION_STOP:".length();
            int end = m.indexOf(':', start);
            String name = end < 0 ? m.substring(start).trim() : m.substring(start, end).trim();
            try {
                return ProductionTerminal.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (m.contains("Clarification Stop") || m.contains("Clarification pending")
                || m.contains("Gap BLOCKED")) {
            return STOPPED_NEEDS_CLARIFICATION;
        }
        if (m.contains("Plan Approval required")) {
            return STOPPED_NEEDS_PLAN_APPROVAL;
        }
        if (m.contains("FAILED_ENVIRONMENT") || m.contains("ENV_FAIL")) {
            return FAILED_ENVIRONMENT;
        }
        if (m.contains("FAILED_ADAPTER") || m.contains("Analysis Adapter failed")
                || m.contains("Planning Adapter failed") || m.contains("Dev Adapter failed")
                || m.contains("Review Adapter failed")
                || m.contains("FAILED_ADAPTER: Review")
                || m.contains("Review Adapter must write")) {
            return FAILED_ADAPTER;
        }
        if (m.contains("Review CONDITIONAL") || m.contains("cannot auto Delivery")) {
            return STOPPED_NEEDS_CLARIFICATION;
        }
        if (m.contains("FAILED_VERIFICATION_BUDGET")) {
            return FAILED_VERIFICATION_BUDGET;
        }
        if (m.contains("FAILED_NO_PROGRESS")) {
            return FAILED_NO_PROGRESS;
        }
        return FAILED_POLICY;
    }
}
