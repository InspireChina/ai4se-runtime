package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.story.StoryOpener;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kill after advance → VERIFICATION/RUNNING: resume must reconstruct DEVELOPMENT and reopen the
 * same in-flight round (not FAILED_POLICY from BoundedDeliveryLoop stage gate).
 */
final class ResumeAfterVerificationInterruptTest {

    @TempDir
    Path temp;

    @Test
    void killAfterEnteringVerificationResumeReturnsToDevelopmentSameRound() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "verify-kill");
        Path seed = ResumeFixtures.seed(temp, "verify-kill");
        String storyId = "story-verify-kill";

        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 3);

        StoryOpener.open(ws, storyId, seed);
        DiscoveryRecords.writeReport(ws, storyId, "## 摸底\n- A\n");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "clear");
        PlanRecords.writeFormalPlan(
                ws, storyId, "plan", Collections.singletonList(ResumeFixtures.ALLOWED));
        ApprovalRecords.approvePlan(ws, storyId, "t", "ok");
        ledger.stageCompleted(WorkflowStage.ANALYSIS);
        ledger.stageCompleted(WorkflowStage.PLANNING);
        ledger.stageStarted(WorkflowStage.DEVELOPMENT);

        StoryWorkflowMachine.save(
                ws,
                new StoryWorkflowState(
                        storyId, WorkflowStage.DEVELOPMENT, WorkflowStatus.RUNNING, null));
        // In-flight round 1: ledger says started; machine advanced to VERIFICATION; process killed
        // before Verify returns. Seed VERIFICATION directly (crash mid-stage after advance).
        ledger.onRoundStarted(1);
        StoryWorkflowMachine.save(
                ws,
                new StoryWorkflowState(
                        storyId, WorkflowStage.VERIFICATION, WorkflowStatus.RUNNING, null));
        assertEquals(WorkflowStage.VERIFICATION, StoryWorkflowMachine.load(ws, storyId).stage());
        assertEquals(1, ledger.readState().currentRound);
        assertEquals(0, ledger.readState().roundsUsed);

        AtomicInteger analysisCalls = new AtomicInteger();
        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger fixCalls = new AtomicInteger();
        AtomicInteger reviewCalls = new AtomicInteger();

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
                        .boundedDeliveryLoop(99)
                        .productionResume(true)
                        .build(),
                ResumeFixtures.passingVerifier());

        assertEquals(0, analysisCalls.get());
        assertEquals(0, planCalls.get());
        assertEquals(1, fixCalls.get(), "same round 1 must reopen from DEVELOPMENT");
        assertTrue(Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 1)));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/" + storyId + "/execution/adapter-dev-round-1.md")));
        assertTrue(!Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 2)),
                "must not skip to round 2 after VERIFICATION interrupt");
        assertEquals(1, ledger.readState().roundsUsed);
        assertEquals(0, ledger.readState().currentRound);
        assertEquals(1, reviewCalls.get());
        assertTrue(resumed.commitShaOrNull != null && !resumed.commitShaOrNull.isEmpty());
    }
}
