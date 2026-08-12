package com.ai4se.orchestration.control;

/**
 * Explicit Development↔Verify round outcome persisted in the run ledger (M1 PR3).
 *
 * <p>{@code null} must not mean PASS — controlled failures share "round consumed" with PASS
 * but are distinct durable outcomes.
 */
public enum RoundOutcome {
    /** Round started; Adapter/Verify not yet settled. */
    IN_FLIGHT,
    /** Verification Contract PASS. */
    VERIFY_PASS,
    /** Verification Contract FAIL (Defect written). */
    VERIFY_FAIL,
    /** Development Adapter turn failed. */
    FAILED_ADAPTER,
    /** Policy / observe gate failed (e.g. empty diff, Allowed violation). */
    FAILED_POLICY,
    /** Verification environment / shell launch failure. */
    FAILED_ENVIRONMENT;

    public boolean isVerifyPass() {
        return this == VERIFY_PASS;
    }

    public boolean consumesBudget() {
        return this != IN_FLIGHT;
    }
}
