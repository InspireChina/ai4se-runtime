package com.ai4se.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.verification.VerificationControl;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class StoryWorkflowMachineTest {

    @TempDir
    Path temp;

    @Test
    void startPersistsAnalysisRunningUnderStory() throws Exception {
        openStory("s1");
        StoryWorkflowState state = StoryWorkflowMachine.start(temp, "s1");
        assertEquals(WorkflowStage.ANALYSIS, state.stage());
        assertEquals(WorkflowStatus.RUNNING, state.status());
        assertTrue(Files.isRegularFile(StoryWorkflowMachine.statePath(temp, "s1")));
        StoryWorkflowState loaded = StoryWorkflowMachine.load(temp, "s1");
        assertEquals(WorkflowStage.ANALYSIS, loaded.stage());
    }

    @Test
    void advanceAnalysisToPlanning() throws Exception {
        openStory("s2");
        StoryWorkflowMachine.start(temp, "s2");
        prepareAnalysisClear("s2");
        StoryWorkflowState next = StoryWorkflowMachine.advance(temp, "s2");
        assertEquals(WorkflowStage.PLANNING, next.stage());
        assertEquals(WorkflowStatus.RUNNING, next.status());
        assertEquals(WorkflowStage.PLANNING, StoryWorkflowMachine.load(temp, "s2").stage());
    }

    @Test
    void stopWithReasonIsLegalEndpoint() throws Exception {
        openStory("s3");
        StoryWorkflowMachine.start(temp, "s3");
        StoryWorkflowState stopped = StoryWorkflowMachine.stop(temp, "s3", "BLOCKED: need clarification");
        assertEquals(WorkflowStatus.STOPPED, stopped.status());
        assertEquals(WorkflowStage.ANALYSIS, stopped.stage());
        assertEquals("BLOCKED: need clarification", stopped.stopReason());
        String file = new String(
                Files.readAllBytes(StoryWorkflowMachine.statePath(temp, "s3")),
                StandardCharsets.UTF_8);
        assertTrue(file.contains("status=STOPPED"));
        assertTrue(file.contains("stop_reason=BLOCKED: need clarification"));
    }

    @Test
    void cannotAdvanceAfterStop() throws Exception {
        openStory("s4");
        StoryWorkflowMachine.start(temp, "s4");
        StoryWorkflowMachine.stop(temp, "s4", "budget exhausted");
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.advance(temp, "s4"));
    }

    @Test
    void stopRequiresReason() throws Exception {
        openStory("s5");
        StoryWorkflowMachine.start(temp, "s5");
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.stop(temp, "s5", "  "));
    }

    @Test
    void linearPathToDeliveryThenComplete() throws Exception {
        openStory("s6");
        StoryWorkflowMachine.start(temp, "s6");
        prepareAnalysisClear("s6");
        assertEquals(WorkflowStage.PLANNING, StoryWorkflowMachine.advance(temp, "s6").stage());
        preparePlanApproved("s6");
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.advance(temp, "s6").stage());
        DevelopmentRecords.recordDeclaredChanges(
                temp, "s6", Collections.singletonList("src/A.java"), "implement change");
        assertEquals(WorkflowStage.VERIFICATION, StoryWorkflowMachine.advance(temp, "s6").stage());
        // entries for verify
        Files.createDirectories(temp.resolve(".ai4se/repository"));
        Files.write(
                temp.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(temp.resolve(".ai4se/repository/baseline.md"), "# f\n".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(temp.resolve(".ai4se/index"));
        Files.write(temp.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        VerificationControl.run(
                temp, "s6", "mvn -q test",
                new com.ai4se.execution.support.SequenceProcessInvoker(
                        com.ai4se.execution.support.SequenceProcessInvoker.ok(""),
                        com.ai4se.execution.support.SequenceProcessInvoker.ok("ok"),
                        com.ai4se.execution.support.SequenceProcessInvoker.ok("")));
        assertEquals(WorkflowStage.REVIEW, StoryWorkflowMachine.advance(temp, "s6").stage());
        ReviewRecords.write(temp, "s6", "通过", "none");
        assertEquals(WorkflowStage.DELIVERY, StoryWorkflowMachine.advance(temp, "s6").stage());
        DeliveryRecords.recordLocalCommit(
                temp, "s6",
                new com.ai4se.execution.support.SequenceProcessInvoker(
                        com.ai4se.execution.support.SequenceProcessInvoker.ok("## main\n"),
                        com.ai4se.execution.support.SequenceProcessInvoker.ok("abc1234\n")));
        StoryWorkflowState done = StoryWorkflowMachine.complete(temp, "s6");
        assertEquals(WorkflowStatus.COMPLETED, done.status());
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.advance(temp, "s6"));
    }

    @Test
    void cannotCompleteFromAnalysis() throws Exception {
        openStory("s7");
        StoryWorkflowMachine.start(temp, "s7");
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.complete(temp, "s7"));
    }

    @Test
    void startRequiresOpenedStory() {
        assertThrows(Exception.class, () -> StoryWorkflowMachine.start(temp, "missing"));
    }

    @ParameterizedTest
    @CsvSource({
            "ANALYSIS, PLANNING, true",
            "ANALYSIS, DEVELOPMENT, false",
            "PLANNING, DEVELOPMENT, true",
            "PLANNING, VERIFICATION, false",
            "DEVELOPMENT, VERIFICATION, true",
            "DELIVERY, ANALYSIS, false"
    })
    void stageAdjacencyIsStrict(WorkflowStage from, WorkflowStage to, boolean allowed) {
        assertEquals(allowed, from.canAdvanceTo(to));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "please go to planning",
            "skip to development now",
            "进入 Planning 吧",
            "跳到开发",
            "下一阶段"
    })
    void modelUtterancesAreNotControl(String utterance) {
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.rejectModelControlUtterance(utterance));
    }

    @Test
    void normalAnalysisTextIsNotTreatedAsControl() {
        StoryWorkflowMachine.rejectModelControlUtterance("The module map shows FeatureFlags.");
    }

    @Test
    void controlIsCodeNotFileRewriteByModel() throws Exception {
        openStory("s8");
        StoryWorkflowMachine.start(temp, "s8");
        prepareAnalysisClear("s8");
        StoryWorkflowState mid = StoryWorkflowMachine.advance(temp, "s8");
        assertEquals(WorkflowStage.PLANNING, mid.stage());
        assertFalse(mid.stage() == WorkflowStage.DEVELOPMENT);
    }

    private void prepareAnalysisClear(String id) throws Exception {
        DiscoveryRecords.writeReport(temp, id, "facts for " + id);
        GapRecords.write(temp, id, GapStatus.CLEAR, 0, "ok");
    }

    private void preparePlanApproved(String id) throws Exception {
        PlanRecords.writeFormalPlan(temp, id, "design", Arrays.asList("src/A.java"));
        ApprovalRecords.approvePlan(temp, id, "tester", "ok");
    }

    private void openStory(String id) throws Exception {
        Files.createDirectories(temp.resolve(".ai4se"));
        Files.createDirectories(temp.resolve(".story").resolve(id).resolve("packages"));
        Files.write(
                temp.resolve(".story").resolve(id).resolve("requirement.md"),
                ("## acceptance\n- ac for " + id + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
