package com.ai4se.orchestration.control;

import java.io.IOException;

/**
 * Durable per-round progress for production resume (M1 PR3).
 *
 * <p>{@link #onRoundStarted} must flush before Adapter work begins. {@link #onRoundCompleted}
 * must flush after each Dev↔Verify attempt finishes (PASS or FAIL), before the next round.
 */
public interface RoundProgressSink {

    void onRoundStarted(int round) throws IOException;

    /**
     * @param fingerprintOrNull failure fingerprint when Verify FAIL; null on PASS
     * @param businessDiffHashOrNull working-tree digest at FAIL; null on PASS
     */
    void onRoundCompleted(
            int round, FailureFingerprint fingerprintOrNull, String businessDiffHashOrNull)
            throws IOException;
}
