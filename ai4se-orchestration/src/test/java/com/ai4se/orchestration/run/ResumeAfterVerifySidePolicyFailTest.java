package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.control.RoundOutcome;
import com.ai4se.orchestration.pathway.PathwayRunner;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * max=1: Development succeeds, but Verification throws a non-ENV StageGateException (policy).
 * Round must settle as FAILED_POLICY — resume must not re-run Development.
 */
final class ResumeAfterVerifySidePolicyFailTest {

    @TempDir
    Path temp;

    @Test
    void maxOneVerifySidePolicyFailSettlesRoundAndResumeSkipsDev() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "verify-policy");
        Path seed = ResumeFixtures.seed(temp, "verify-policy");
        String storyId = "story-verify-policy";
        AtomicInteger devCalls = new AtomicInteger();

        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 1);

        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, new AtomicInteger()),
                                    ResumeFixtures.plan(storyId, new AtomicInteger()),
                                    ResumeFixtures.dev(devCalls),
                                    ResumeFixtures.review(storyId, new AtomicInteger()))
                            .boundedDeliveryLoop(1)
                            .build(),
                    ResumeFixtures.businessMutatingVerifier());
        } catch (StageGateException e) {
            assertTrue(e.getMessage().contains("FAILED_POLICY"), e.getMessage());
        }

        assertEquals(1, devCalls.get(), "Development must have run once");
        assertEquals(1, ledger.readState().roundsUsed);
        assertEquals(0, ledger.readState().currentRound);
        assertEquals(RoundOutcome.FAILED_POLICY.name(), ledger.readState().lastRoundOutcomeOrNull);
        boolean sawPolicyOutcome = false;
        for (String line : ledger.readEventLines()) {
            if (line.contains("\"type\":\"round_completed\"")
                    && line.contains("outcome=" + RoundOutcome.FAILED_POLICY.name())) {
                sawPolicyOutcome = true;
            }
            if (line.contains("\"type\":\"round_completed\"")) {
                assertTrue(
                        !line.contains("outcome=PASS") && !line.contains("outcome=VERIFY_PASS"),
                        line);
            }
        }
        assertTrue(sawPolicyOutcome);

        AtomicInteger resumeDevCalls = new AtomicInteger();
        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, new AtomicInteger()),
                                    ResumeFixtures.plan(storyId, new AtomicInteger()),
                                    ResumeFixtures.dev(resumeDevCalls),
                                    ResumeFixtures.review(storyId, new AtomicInteger()))
                            .boundedDeliveryLoop(10)
                            .productionResume(true)
                            .build(),
                    ResumeFixtures.passingVerifier());
        } catch (StageGateException e) {
            assertTrue(
                    e.getMessage().contains("FAILED_VERIFICATION_BUDGET")
                            || e.getMessage().contains("FAILED_POLICY"),
                    e.getMessage());
        }
        assertEquals(0, resumeDevCalls.get(), "resume must not call Development Adapter again");
        assertEquals(1, ledger.readState().roundsUsed);
        assertEquals(0, ledger.readState().currentRound);
    }
}
