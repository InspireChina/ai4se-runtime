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
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.verification.AcceptanceProbeSet;
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
        DevPackageBuilder.build(temp, "tov");
        assertEquals(
                WorkflowStage.VERIFICATION,
                StoryWorkflowMachine.advance(temp, "tov").stage());
    }

    @Test
    void devPackageCarriesApprovedImpactAndApiContractAsPriorityOne() throws Exception {
        readyForDev("impact-context");
        Path planning = PlanRecords.planningDir(temp, "impact-context");
        Files.write(planning.resolve("impact-assessment.md"),
                "# Impact Assessment\n\n- api: PRESENT\n".getBytes(StandardCharsets.UTF_8));
        Files.write(planning.resolve("api-contract.md"),
                "# API Contract\n\n- GET /orders/{id}\n".getBytes(StandardCharsets.UTF_8));

        Path pkg = DevPackageBuilder.build(temp, "impact-context");

        String input = new String(Files.readAllBytes(pkg.resolve("model-input.md")), StandardCharsets.UTF_8);
        assertTrue(input.contains("impact-assessment.md"), input);
        assertTrue(input.contains("GET /orders/{id}"), input);
    }

    @Test
    void devPackageCarriesFrozenProbeSelectorsAsPriorityOne() throws Exception {
        readyForDev("frozen-probe-context");
        Path root = temp.resolve(".ai4se/acceptance-probes/frozen-probe-context");
        Files.createDirectories(root);
        Path probe = root.resolve("ac1.sh");
        Files.write(probe, ("#!/bin/sh\n"
                + "mvn -Dtest=PromotionPricingServiceTest#ac1ExactContract test\n")
                .getBytes(StandardCharsets.UTF_8));
        String sha = AcceptanceProbeSet.sha256(probe);
        Files.write(root.resolve("probes.properties"), ("ac.count=1\n"
                + "ac.1.path=.ai4se/acceptance-probes/frozen-probe-context/ac1.sh\n"
                + "ac.1.command=sh .ai4se/acceptance-probes/frozen-probe-context/ac1.sh\n"
                + "ac.1.sha256=" + sha + "\n").getBytes(StandardCharsets.UTF_8));

        Path pkg = DevPackageBuilder.build(temp, "frozen-probe-context");
        String input = new String(Files.readAllBytes(pkg.resolve("model-input.md")), StandardCharsets.UTF_8);
        assertTrue(input.contains("frozen-acceptance-probes.md"), input);
        assertTrue(input.contains("ac1ExactContract"), input);
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
