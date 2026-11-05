package com.ai4se.orchestration.control;

import java.io.IOException;

/**
 * Durable per-round progress for production resume (M1 PR3).
 *
 * <p>{@link #onRoundStarted} must flush before Adapter work begins. {@link #onRoundCompleted}
 * must flush after each Dev↔Verify attempt settles with an explicit {@link RoundOutcome}.
 */
public interface RoundProgressSink {

    void onRoundStarted(int round) throws IOException;

    /**
     * @param outcome explicit round result — never infer PASS from a null fingerprint
     * @param fingerprintOrNull failure fingerprint when {@link RoundOutcome#VERIFY_FAIL}
     * @param businessDiffHashOrNull working-tree digest when {@link RoundOutcome#VERIFY_FAIL}
     */
    void onRoundCompleted(
            int round,
            RoundOutcome outcome,
            FailureFingerprint fingerprintOrNull,
            String businessDiffHashOrNull)
            throws IOException;
}
