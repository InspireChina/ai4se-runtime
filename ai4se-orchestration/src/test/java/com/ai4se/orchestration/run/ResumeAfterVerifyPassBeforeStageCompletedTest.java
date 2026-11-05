package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.control.FailureFingerprint;
import com.ai4se.orchestration.control.RoundOutcome;
import com.ai4se.orchestration.control.RoundProgressSink;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * max=1 Verify PASS persisted, then kill before stage_completed: resume must enter Review, not
 * FAILED_VERIFICATION_BUDGET or another Development round.
 */
final class ResumeAfterVerifyPassBeforeStageCompletedTest {

    @TempDir
    Path temp;

    @Test
    void maxOnePassThenKillBeforeStageCompletedResumesIntoReview() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "pass-kill");
        Path seed = ResumeFixtures.seed(temp, "pass-kill");
        String storyId = "story-pass-kill";
        AtomicInteger analysisCalls = new AtomicInteger();
        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger devCalls = new AtomicInteger();
        AtomicInteger reviewCalls = new AtomicInteger();

        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 1);

        AtomicBoolean killArmed = new AtomicBoolean(true);
        RoundProgressSink killAfterPass = new RoundProgressSink() {
            @Override
            public void onRoundStarted(int round) throws IOException {
                ledger.onRoundStarted(round);
            }

            @Override
            public void onRoundCompleted(
                    int round,
                    RoundOutcome outcome,
                    FailureFingerprint fingerprintOrNull,
                    String businessDiffHashOrNull)
                    throws IOException {
                ledger.onRoundCompleted(round, outcome, fingerprintOrNull, businessDiffHashOrNull);
                if (outcome == RoundOutcome.VERIFY_PASS && killArmed.getAndSet(false)) {
                    throw new IOException("simulated kill after VERIFY_PASS before stage_completed");
                }
            }
        };

        IOException killed = null;
        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, analysisCalls),
                                    ResumeFixtures.plan(storyId, planCalls),
                                    ResumeFixtures.dev(devCalls),
                                    ResumeFixtures.review(storyId, reviewCalls))
                            .boundedDeliveryLoop(1)
                            .roundProgressSink(killAfterPass)
                            .build(),
                    ResumeFixtures.passingVerifier());
        } catch (IOException e) {
            killed = e;
        }
        assertTrue(killed != null && killed.getMessage().contains("VERIFY_PASS"), String.valueOf(killed));
        assertEquals(RoundOutcome.VERIFY_PASS.name(), ledger.readState().lastRoundOutcomeOrNull);
        assertEquals(1, ledger.readState().roundsUsed);
        assertTrue(!ledger.hasCompleted(WorkflowStage.DEVELOPMENT)
                || !ledger.hasCompleted(WorkflowStage.VERIFICATION));
        assertEquals(0, reviewCalls.get(), "Review must not run before stage_completed");
        int devBeforeResume = devCalls.get();

        PathwayRunner.PathwayResult resumed = PathwayRunner.run(
                ResumeFixtures.base(
                                ws,
                                storyId,
                                seed,
                                ledger,
                                ResumeFixtures.analysis(storyId, analysisCalls),
                                ResumeFixtures.plan(storyId, planCalls),
                                ResumeFixtures.dev(devCalls),
                                ResumeFixtures.review(storyId, reviewCalls))
                        .boundedDeliveryLoop(1)
                        .productionResume(true)
                        .build(),
                ResumeFixtures.passingVerifier());

        assertEquals(devBeforeResume, devCalls.get(), "must not re-enter Development after VERIFY_PASS");
        assertEquals(1, reviewCalls.get(), "resume must continue into Review");
        assertTrue(ledger.hasCompleted(WorkflowStage.DEVELOPMENT));
        assertTrue(ledger.hasCompleted(WorkflowStage.VERIFICATION));
        assertTrue(ledger.hasCompleted(WorkflowStage.REVIEW));
        assertEquals(ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE.name(),
                ledger.readState().terminalOrNull);
        assertTrue(resumed.commitShaOrNull != null && !resumed.commitShaOrNull.isEmpty());
    }
}
