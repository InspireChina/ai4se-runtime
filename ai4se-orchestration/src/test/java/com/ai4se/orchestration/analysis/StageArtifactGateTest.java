package com.ai4se.orchestration.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.workflow.IllegalWorkflowTransitionException;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StageArtifactGateTest {

    @TempDir
    Path temp;

    @Test
    void cannotAdvanceToPlanningWithoutDiscovery() throws Exception {
        openAndStart("no-disc");
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.advance(temp, "no-disc"));
    }

    @Test
    void cannotAdvanceToPlanningWhenBlocked() throws Exception {
        openAndStart("blocked");
        DiscoveryRecords.writeReport(temp, "blocked", "module X exists");
        GapRecords.write(temp, "blocked", GapStatus.BLOCKED, 1, "timeout unknown");
        IllegalWorkflowTransitionException ex = assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.advance(temp, "blocked"));
        assertTrue(ex.getMessage().contains("BLOCKED"));
    }

    @Test
    void blockedCannotWriteFormalPlan() throws Exception {
        openAndStart("plan-blocked");
        DiscoveryRecords.writeSkip(temp, "plan-blocked", "known sample paths", "alice");
        GapRecords.write(temp, "plan-blocked", GapStatus.BLOCKED, 2, "product choice");
        assertThrows(
                StageGateException.class,
                () -> PlanRecords.writeFormalPlan(
                        temp, "plan-blocked", "design", Arrays.asList("src/A.java")));
        assertFalse(PlanRecords.hasFormalPlan(temp, "plan-blocked"));
    }

    @Test
    void formalPlanRequiresAllowedFiles() throws Exception {
        openAndStart("no-allowed");
        DiscoveryRecords.writeReport(temp, "no-allowed", "facts");
        GapRecords.write(temp, "no-allowed", GapStatus.CLEAR, 0, "ok");
        assertThrows(
                StageGateException.class,
                () -> PlanRecords.writeFormalPlan(
                        temp, "no-allowed", "design", Collections.<String>emptyList()));
    }

    @Test
    void happyPathAnalysisToPlanningWithSkipAndClear() throws Exception {
        openAndStart("happy");
        DiscoveryRecords.writeSkip(temp, "happy", "author knows exact paths", "bob");
        GapRecords.write(temp, "happy", GapStatus.CLEAR, 0, "ready");
        assertEquals(
                WorkflowStage.PLANNING,
                StoryWorkflowMachine.advance(temp, "happy").stage());
    }

    @Test
    void cannotEnterDevWithoutApproval() throws Exception {
        openAndStart("no-appr");
        DiscoveryRecords.writeReport(temp, "no-appr", "facts");
        GapRecords.write(temp, "no-appr", GapStatus.ASSUMABLE, 0, "low risk");
        StoryWorkflowMachine.advance(temp, "no-appr");
        PlanRecords.writeFormalPlan(
                temp, "no-appr", "change FeatureFlags", Arrays.asList("src/FeatureFlags.java"));
        IllegalWorkflowTransitionException ex = assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.advance(temp, "no-appr"));
        assertTrue(ex.getMessage().toLowerCase().contains("approv"));
    }

    @Test
    void approvalThenAdvanceToDevelopment() throws Exception {
        openAndStart("appr");
        DiscoveryRecords.writeReport(temp, "appr", "facts");
        GapRecords.write(temp, "appr", GapStatus.CLEAR, 0, "ok");
        StoryWorkflowMachine.advance(temp, "appr");
        PlanRecords.writeFormalPlan(
                temp, "appr", "design", Arrays.asList("src/A.java", "src/B.java"));
        ApprovalRecords.approvePlan(temp, "appr", "carol", "lgtm");
        assertEquals(
                WorkflowStage.DEVELOPMENT,
                StoryWorkflowMachine.advance(temp, "appr").stage());
        assertEquals(2, PlanRecords.readAllowedFiles(temp, "appr").size());
    }

    @Test
    void skipRequiresRationaleAndApprover() {
        assertThrows(
                StageGateException.class,
                () -> DiscoveryRecords.writeSkip(temp, "x", "", "a"));
        assertThrows(
                StageGateException.class,
                () -> DiscoveryRecords.writeSkip(temp, "x", "why", ""));
    }

    @Test
    void gapConsistencyEnforced() throws Exception {
        openStory("gap");
        assertThrows(
                StageGateException.class,
                () -> GapRecords.write(temp, "gap", GapStatus.BLOCKED, 0, "bad"));
        assertThrows(
                StageGateException.class,
                () -> GapRecords.write(temp, "gap", GapStatus.CLEAR, 1, "bad"));
    }

    private void openAndStart(String id) throws Exception {
        openStory(id);
        StoryWorkflowMachine.start(temp, id);
    }

    private void openStory(String id) throws Exception {
        Files.createDirectories(temp.resolve(".ai4se"));
        Files.createDirectories(temp.resolve(".story").resolve(id).resolve("packages"));
        Files.write(
                temp.resolve(".story").resolve(id).resolve("requirement.md"),
                "# s\n".getBytes(StandardCharsets.UTF_8));
    }
}
