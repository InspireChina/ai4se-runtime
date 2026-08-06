package com.ai4se.orchestration.development;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class DevelopmentRecordsTest {

    @TempDir
    Path temp;

    @Test
    void rejectsChangedFileOutsideAllowed() throws Exception {
        readyForDev("over");
        assertThrows(
                StageGateException.class,
                () -> DevelopmentRecords.recordDeclaredChanges(
                        temp,
                        "over",
                        Arrays.asList("src/A.java", "src/Evil.java"),
                        "add feature"));
        assertFalse(DevelopmentRecords.hasValidRecord(temp, "over"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "测试已通过",
            "可以交付",
            "tests passed",
            "Acceptance passed",
            "all tests green"
    })
    void rejectsSelfGreenNotes(String phrase) throws Exception {
        readyForDev("green");
        assertThrows(
                StageGateException.class,
                () -> DevelopmentRecords.recordDeclaredChanges(
                        temp,
                        "green",
                        Collections.singletonList("src/A.java"),
                        "done; " + phrase));
    }

    @Test
    void recordsValidDiffWithinAllowed() throws Exception {
        readyForDev("ok");
        DevelopmentRecords.recordDeclaredChanges(
                temp,
                "ok",
                Arrays.asList("src/A.java", "./src/B.java"),
                "Implement FeatureFlags check");
        assertTrue(DevelopmentRecords.hasValidRecord(temp, "ok"));
        assertEquals(2, DevelopmentRecords.readChangedFiles(temp, "ok").size());
    }

    @Test
    void cannotAdvanceToVerifyWithoutDevRecord() throws Exception {
        readyForDev("nodev");
        // at DEVELOPMENT
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.advance(temp, "nodev"));
    }

    @Test
    void advanceToVerificationWhenDevValid() throws Exception {
        readyForDev("tov");
        DevelopmentRecords.recordDeclaredChanges(
                temp, "tov", Collections.singletonList("src/A.java"), "wire flag");
        assertEquals(
                WorkflowStage.VERIFICATION,
                StoryWorkflowMachine.advance(temp, "tov").stage());
    }

    @Test
    void selfGreenScannerDetectsChineseAndEnglish() {
        assertTrue(SelfGreenScanner.findHits("implement API").isEmpty());
        assertFalse(SelfGreenScanner.findHits("测试已通过，合并吧").isEmpty());
        assertFalse(SelfGreenScanner.findHits("All Tests Green today").isEmpty());
    }

    @Test
    void directoryAllowCoversChildren() {
        assertTrue(DiffScopeGuard.findViolations(
                Collections.singletonList("src/main/Foo.java"),
                Collections.singletonList("src/main/")).isEmpty());
        assertFalse(DiffScopeGuard.findViolations(
                Collections.singletonList("src/other/Foo.java"),
                Collections.singletonList("src/main/")).isEmpty());
    }

    private void readyForDev(String id) throws Exception {
        Files.createDirectories(temp.resolve(".ai4se"));
        Files.createDirectories(temp.resolve(".story").resolve(id).resolve("packages"));
        Files.write(
                temp.resolve(".story").resolve(id).resolve("requirement.md"),
                ("# s\n\n## Goal\ng\n\n## Acceptance\n\n- ac1\n").getBytes(StandardCharsets.UTF_8));
        StoryWorkflowMachine.start(temp, id);
        DiscoveryRecords.writeReport(temp, id, "facts");
        GapRecords.write(temp, id, GapStatus.CLEAR, 0, "ok");
        StoryWorkflowMachine.advance(temp, id);
        PlanRecords.writeFormalPlan(
                temp, id, "design", Arrays.asList("src/A.java", "src/B.java", "src/main/"));
        ApprovalRecords.approvePlan(temp, id, "r", "ok");
        StoryWorkflowMachine.advance(temp, id);
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.load(temp, id).stage());
    }
}
