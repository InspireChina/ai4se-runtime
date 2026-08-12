package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResumeAfterFailedVerificationTest {

    @TempDir
    Path temp;

    @Test
    void resumeContinuesAfterVerificationBudgetStop() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "rv");
        Path seed = ResumeFixtures.seed(temp, "rv");
        String storyId = "story-resume-verify";
        AtomicInteger analysisCalls = new AtomicInteger();
        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger devCalls = new AtomicInteger();
        AtomicInteger reviewCalls = new AtomicInteger();

        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 2);

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
                            .boundedDeliveryLoop(2)
                            .build(),
                    ResumeFixtures.alwaysFailingVerifier());
        } catch (StageGateException e) {
            assertTrue(e.getMessage().contains("FAILED_VERIFICATION_BUDGET"), e.getMessage());
        }

        assertTrue(ledger.hasCompleted(WorkflowStage.ANALYSIS));
        assertTrue(ledger.hasCompleted(WorkflowStage.PLANNING));
        assertEquals(ProductionTerminal.FAILED_VERIFICATION_BUDGET.name(),
                ledger.readState().terminalOrNull);
        int analysisBefore = analysisCalls.get();
        int planBefore = planCalls.get();
        int devBefore = devCalls.get();

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
                        .boundedDeliveryLoop(3)
                        .productionResume(true)
                        .build(),
                ResumeFixtures.passingVerifier());

        assertEquals(analysisBefore, analysisCalls.get());
        assertEquals(planBefore, planCalls.get());
        assertTrue(devCalls.get() > devBefore);
        assertEquals(1, reviewCalls.get());
        assertTrue(ledger.hasCompleted(WorkflowStage.VERIFICATION));
        assertEquals(ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE.name(),
                ledger.readState().terminalOrNull);
        assertTrue(resumed.commitShaOrNull != null && !resumed.commitShaOrNull.isEmpty());
    }
}
