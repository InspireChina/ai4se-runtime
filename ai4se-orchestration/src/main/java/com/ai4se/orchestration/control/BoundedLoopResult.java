package com.ai4se.orchestration.control;

import com.ai4se.orchestration.verification.VerificationControl.VerificationRecord;
import java.nio.file.Path;

/** Result of {@link BoundedDeliveryLoop#run}. */
public final class BoundedLoopResult {

    public final RunStopReason reason;
    public final int developmentRoundsUsed;
    public final VerificationRecord lastVerifyOrNull;
    public final Path lastDefectOrNull;

    public BoundedLoopResult(
            RunStopReason reason,
            int developmentRoundsUsed,
            VerificationRecord lastVerifyOrNull,
            Path lastDefectOrNull) {
        this.reason = reason;
        this.developmentRoundsUsed = developmentRoundsUsed;
        this.lastVerifyOrNull = lastVerifyOrNull;
        this.lastDefectOrNull = lastDefectOrNull;
    }

    public boolean passed() {
        return reason == RunStopReason.PASSED_VERIFICATION;
    }
}
