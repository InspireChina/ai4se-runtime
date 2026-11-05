package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.story.StoryOpener;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResumeAfterAnalysisBoundaryTest {

    @TempDir
    Path temp;

    @Test
    void resumeSkipsAnalysisAdapterAfterStageCompleted() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "ra");
        Path seed = ResumeFixtures.seed(temp, "ra");
        String storyId = "story-resume-analysis";
        AtomicInteger analysisCalls = new AtomicInteger();
        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger devCalls = new AtomicInteger();
        AtomicInteger reviewCalls = new AtomicInteger();

        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 3);

        StoryOpener.open(ws, storyId, seed);
        DiscoveryRecords.writeReport(ws, storyId, "## 摸底\n- A\n");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "clear");
        StoryWorkflowMachine.save(
                ws,
                new StoryWorkflowState(storyId, WorkflowStage.PLANNING, WorkflowStatus.RUNNING, null));
        ledger.stageCompleted(WorkflowStage.ANALYSIS);

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
                        .productionResume(true)
                        .build(),
                ResumeFixtures.passingVerifier());

        assertEquals(0, analysisCalls.get(), "analysis adapter must not re-run after stage_completed");
        assertEquals(1, planCalls.get());
        assertEquals(1, devCalls.get());
        assertEquals(1, reviewCalls.get());
        assertTrue(ledger.hasCompleted(WorkflowStage.PLANNING));
        assertTrue(ledger.hasCompleted(WorkflowStage.DELIVERY));
        assertEquals(WorkflowStatus.COMPLETED, resumed.finalState.status());
    }
}
