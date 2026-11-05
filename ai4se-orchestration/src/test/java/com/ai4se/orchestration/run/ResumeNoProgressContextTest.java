package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * After FAILED_NO_PROGRESS, an identical resume (same fingerprint + same business diff) must
 * still stop as FAILED_NO_PROGRESS — not reclassify as FAILED_VERIFICATION_BUDGET.
 */
final class ResumeNoProgressContextTest {

    @TempDir
    Path temp;

    @Test
    void resumeAfterNoProgressWithSameFailureStillNoProgress() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "noprog-resume");
        Path seed = ResumeFixtures.seed(temp, "noprog-resume");
        String storyId = "story-noprog-resume";
        AtomicInteger stuckCalls = new AtomicInteger();

        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 3);

        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, new AtomicInteger()),
                                    ResumeFixtures.plan(storyId, new AtomicInteger()),
                                    ResumeFixtures.stuckDev(stuckCalls),
                                    ResumeFixtures.review(storyId, new AtomicInteger()))
                            .boundedDeliveryLoop(3)
                            .build(),
                    ResumeFixtures.alwaysFailingVerifier());
        } catch (StageGateException e) {
            assertTrue(e.getMessage().contains("FAILED_NO_PROGRESS"), e.getMessage());
        }
        assertEquals(2, ledger.readState().roundsUsed);
        assertTrue(ledger.readState().failureFingerprintOrNull != null
                && !ledger.readState().failureFingerprintOrNull.isEmpty());
        assertTrue(ledger.readState().failureDiffHashOrNull != null
                && !ledger.readState().failureDiffHashOrNull.isEmpty());
        int afterFirst = stuckCalls.get();

        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, new AtomicInteger()),
                                    ResumeFixtures.plan(storyId, new AtomicInteger()),
                                    ResumeFixtures.stuckDev(stuckCalls),
                                    ResumeFixtures.review(storyId, new AtomicInteger()))
                            .boundedDeliveryLoop(3)
                            .productionResume(true)
                            .build(),
                    ResumeFixtures.alwaysFailingVerifier());
        } catch (StageGateException e) {
            assertTrue(
                    e.getMessage().contains("FAILED_NO_PROGRESS"),
                    "expected FAILED_NO_PROGRESS after identical resume, got: " + e.getMessage());
        }
        assertEquals(afterFirst + 1, stuckCalls.get(), "one remaining round may run");
        assertEquals(3, ledger.readState().roundsUsed);
    }
}
