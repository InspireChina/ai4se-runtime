package com.ai4se.orchestration.control;

/**
 * Machine-facing stop reasons for the production bounded Dev↔Verify loop (M1 PR2).
 * Exit-code mapping lands in PR3.
 */
public enum RunStopReason {
    /** Verify PASS — proceed to Review. */
    PASSED_VERIFICATION,
    /** Shell/env failure — never enter Defect loop. */
    FAILED_ENVIRONMENT,
    /** Hit maxDevelopmentRounds still FAIL. */
    FAILED_VERIFICATION_BUDGET,
    /** Same failure fingerprint twice with identical business diff hash. */
    FAILED_NO_PROGRESS,
    /** Development adapter failed. */
    FAILED_ADAPTER,
    /** Allowed/writeScope/policy gate. */
    FAILED_POLICY
}
