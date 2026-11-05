package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResumeAfterFailedVerificationTest {

    @TempDir
    Path temp;

    @Test
    void resumeContinuesWithRemainingBudgetAndContinuingRoundNumbers() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "rv");
        Path seed = ResumeFixtures.seed(temp, "rv");
        String storyId = "story-resume-verify";
        AtomicInteger analysisCalls = new AtomicInteger();
        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger reviewCalls = new AtomicInteger();
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
                                    ResumeFixtures.analysis(storyId, analysisCalls),
                                    ResumeFixtures.plan(storyId, planCalls),
                                    ResumeFixtures.stuckDev(stuckCalls),
                                    ResumeFixtures.review(storyId, reviewCalls))
                            .boundedDeliveryLoop(3)
                            .build(),
                    ResumeFixtures.alwaysFailingVerifier());
        } catch (StageGateException e) {
            assertTrue(e.getMessage().contains("FAILED_NO_PROGRESS"), e.getMessage());
        }

        assertEquals(2, ledger.readState().roundsUsed);
        assertEquals(3, ledger.readState().maxDevRoundsOrMinusOne);
        assertTrue(ledger.hasCompleted(WorkflowStage.ANALYSIS));
        assertTrue(ledger.hasCompleted(WorkflowStage.PLANNING));
        assertEquals(2, stuckCalls.get());
        assertTrue(Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 1)));
        assertTrue(Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 2)));

        AtomicInteger fixCalls = new AtomicInteger();
        PathwayRunner.PathwayResult resumed = PathwayRunner.run(
                ResumeFixtures.base(
                                ws,
                                storyId,
                                seed,
                                ledger,
                                ResumeFixtures.analysis(storyId, analysisCalls),
                                ResumeFixtures.plan(storyId, planCalls),
                                ResumeFixtures.dev(fixCalls),
                                ResumeFixtures.review(storyId, reviewCalls))
                        // Caller may pass a larger number; ledger max_dev_rounds=3 is the hard ceiling.
                        .boundedDeliveryLoop(99)
                        .productionResume(true)
                        .build(),
                ResumeFixtures.passingVerifier());

        assertEquals(1, fixCalls.get(), "only remaining budget (1 round) may run");
        assertEquals(3, ledger.readState().roundsUsed);
        assertTrue(Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 3)));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/" + storyId + "/execution/adapter-dev-round-3.md")));
        assertTrue(!Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 4)));
        assertEquals(1, reviewCalls.get());
        assertTrue(ledger.hasCompleted(WorkflowStage.VERIFICATION));
        assertEquals(ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE.name(),
                ledger.readState().terminalOrNull);
        assertTrue(resumed.commitShaOrNull != null && !resumed.commitShaOrNull.isEmpty());
    }

    @Test
    void resumeAfterBudgetExhaustedDoesNotGrantExtraRounds() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "budget");
        Path seed = ResumeFixtures.seed(temp, "budget");
        String storyId = "story-budget-hard";
        AtomicInteger calls = new AtomicInteger();
        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 2);

        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, new AtomicInteger()),
                                    ResumeFixtures.plan(storyId, new AtomicInteger()),
                                    ResumeFixtures.dev(calls),
                                    ResumeFixtures.review(storyId, new AtomicInteger()))
                            .boundedDeliveryLoop(2)
                            .build(),
                    ResumeFixtures.alwaysFailingVerifier());
        } catch (StageGateException e) {
            assertTrue(e.getMessage().contains("FAILED_VERIFICATION_BUDGET"), e.getMessage());
        }
        assertEquals(2, ledger.readState().roundsUsed);
        int before = calls.get();

        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, new AtomicInteger()),
                                    ResumeFixtures.plan(storyId, new AtomicInteger()),
                                    ResumeFixtures.dev(calls),
                                    ResumeFixtures.review(storyId, new AtomicInteger()))
                            .boundedDeliveryLoop(10)
                            .productionResume(true)
                            .build(),
                    ResumeFixtures.passingVerifier());
        } catch (StageGateException e) {
            assertTrue(e.getMessage().contains("FAILED_VERIFICATION_BUDGET"), e.getMessage());
        }
        assertEquals(before, calls.get(), "exhausted budget must not allow more Development rounds");
        assertEquals(2, ledger.readState().roundsUsed);
    }
}
