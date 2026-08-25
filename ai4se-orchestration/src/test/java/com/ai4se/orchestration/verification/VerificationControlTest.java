package com.ai4se.orchestration.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.control.FailureFingerprint;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.verification.DefectPackageWriter;
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

final class VerificationControlTest {

    @TempDir
    Path temp;

    @Test
    void compileOnlyCannotPass() throws Exception {
        readyAtVerification("c1");
        // include compile command in entries so allowlist passes; gate must still refuse
        Files.write(
                temp.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n  - mvn -q -DskipTests package\n")
                        .getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new SequenceProcessInvoker();
        assertThrows(
                StageGateException.class,
                () -> VerificationControl.run(temp, "c1", "mvn -q -DskipTests package", invoker));
    }

    @Test
    void commandMustBeInEntries() throws Exception {
        readyAtVerification("c2");
        ProcessInvoker invoker = new SequenceProcessInvoker(SequenceProcessInvoker.ok(""));
        assertThrows(
                StageGateException.class,
                () -> VerificationControl.run(temp, "c2", "curl http://evil", invoker));
    }

    @Test
    void failCreatesDefectAndReturnsToDev() throws Exception {
        readyAtVerification("fail1");
        ProcessInvoker invoker = verifyFailInvoker();
        VerificationControl.VerificationRecord rec = VerificationControl.run(
                temp, "fail1", "mvn -q test", invoker);
        assertEquals(VerificationOutcome.FAIL, rec.outcome);
        assertNotNull(rec.defectOrNull);
        assertTrue(Files.isRegularFile(rec.defectOrNull));
        String defect = new String(Files.readAllBytes(rec.defectOrNull), StandardCharsets.UTF_8);
        assertTrue(defect.contains("VERIFY_FAIL"), defect);
        assertTrue(defect.contains("failing_command="), defect);
        assertTrue(defect.contains("per_command=["), defect);
        assertTrue(defect.contains("stderr_excerpt"), defect);
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.load(temp, "fail1").stage());
        assertTrue(Files.isRegularFile(
                temp.resolve(".story/fail1/packages/verification/round-1/manifest.md")));
        // FAIL writes Defect and returns to DEVELOPMENT; next Dev Package is built at next Dev round.
        assertTrue(Files.isRegularFile(
                temp.resolve(".story/fail1/packages/development/round-1/manifest.md")));
        assertFalse(Files.isRegularFile(
                temp.resolve(".story/fail1/packages/development/round-2/manifest.md")));
        assertNotNull(DefectPackageWriter.latest(temp, "fail1"));

        DevPackageBuilder.build(temp, "fail1");
        String reDevManifest = new String(Files.readAllBytes(
                latestDevManifest(temp, "fail1")), StandardCharsets.UTF_8);
        assertTrue(reDevManifest.contains("defect"));
        assertTrue(Files.isRegularFile(
                temp.resolve(".story/fail1/packages/development/round-2/manifest.md")));
    }

    @Test
    void stdoutOnlyFailureAttachesStdoutExcerptForFingerprint() throws Exception {
        readyAtVerification("stdout-fail");
        ProcessInvoker invoker = new SequenceProcessInvoker(
                SequenceProcessInvoker.ok(""),
                SequenceProcessInvoker.exit(1, "Tests run: 1, Failures: 1 AssertionError: expected true", ""),
                SequenceProcessInvoker.ok(""));
        VerificationControl.VerificationRecord rec = VerificationControl.run(
                temp, "stdout-fail", "mvn -q test", invoker);
        assertEquals(VerificationOutcome.FAIL, rec.outcome);
        assertNotNull(rec.defectOrNull);
        String defect = new String(Files.readAllBytes(rec.defectOrNull), StandardCharsets.UTF_8);
        assertTrue(defect.contains("stdout_excerpt="), defect);
        assertTrue(defect.contains("AssertionError: expected true"), defect);
        assertFalse(defect.contains("stderr_excerpt="), defect);

        FailureFingerprint fp = FailureFingerprint.fromDefectFile(rec.defectOrNull);
        assertEquals("mvn -q test", fp.failingEntry);
        assertEquals(1, fp.exitCode);
        assertTrue(fp.logDigest.length() == 64);

        FailureFingerprint otherFailure = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=mvn -q test"
                        + " per_command=[mvn -q test exit=1 FAIL]"
                        + " verdict_basis=" + VerificationControl.VERDICT_BASIS
                        + " acceptance_scoring=not_performed_all_impacted_via_entry_fail"
                        + " stdout_excerpt=Tests run: 1, Failures: 1 AssertionError: other");
        assertNotEquals(fp, otherFailure);
    }

    @Test
    void mutateBusinessCodeObservedRejected() throws Exception {
        readyAtVerification("mut");
        ProcessInvoker invoker = new SequenceProcessInvoker(
                SequenceProcessInvoker.ok(""), // before
                SequenceProcessInvoker.ok("TESTS OK"), // command
                SequenceProcessInvoker.ok(" M src/A.java")); // after — business mutated
        assertThrows(
                StageGateException.class,
                () -> VerificationControl.run(temp, "mut", "mvn -q test", invoker));
    }

    @Test
    void reVerifyUsesNewPackageRound() throws Exception {
        readyAtVerification("loop");
        VerificationControl.run(temp, "loop", "mvn -q test", verifyFailInvoker());
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.load(temp, "loop").stage());
        DevPackageBuilder.build(temp, "loop");
        DevelopmentRecords.recordObservedChanges(
                temp, "loop", "fix after defect",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(" M src/A.java")));
        StoryWorkflowMachine.advance(temp, "loop");
        VerificationControl.VerificationRecord pass = VerificationControl.run(
                temp, "loop", "mvn -q test", verifyPassInvoker());
        assertEquals(VerificationOutcome.PASS, pass.outcome);
        assertEquals(2, pass.round);
        assertTrue(Files.isRegularFile(
                temp.resolve(".story/loop/packages/verification/round-2/manifest.md")));
        String r1 = new String(Files.readAllBytes(
                temp.resolve(".story/loop/packages/verification/round-1/manifest.md")),
                StandardCharsets.UTF_8);
        String r2 = new String(Files.readAllBytes(
                temp.resolve(".story/loop/packages/verification/round-2/manifest.md")),
                StandardCharsets.UTF_8);
        assertTrue(r1.contains("round: 1"));
        assertTrue(r2.contains("round: 2"));
        assertTrue(r2.contains("defect") || r2.contains("defects"));
    }

    @Test
    void cannotAdvanceToReviewWithoutPass() throws Exception {
        readyAtVerification("nopass");
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.advance(temp, "nopass"));
    }

    @Test
    void packageBuiltBeforeRunEmbedsAcceptanceAndHonestVerdict() throws Exception {
        readyAtVerification("honest");
        VerificationControl.VerificationRecord pass = VerificationControl.run(
                temp, "honest", "mvn -q test", verifyPassInvoker());
        assertEquals(VerificationOutcome.PASS, pass.outcome);
        String pkgAc = new String(Files.readAllBytes(
                pass.verifyPackage.resolve("slices/acceptance.md")), StandardCharsets.UTF_8);
        assertTrue(pkgAc.contains("isEnabled returns false when off"));
        assertFalse(pkgAc.contains("See .story/"));
        String report = new String(Files.readAllBytes(pass.report), StandardCharsets.UTF_8);
        assertTrue(report.contains("verdict_basis: " + VerificationControl.VERDICT_BASIS));
        assertTrue(report.contains("acceptance_item_scoring: frozen_probe"));
        assertTrue(report.contains("package_built_before_run: true"));
        assertTrue(report.contains("command_ok: true"));
        assertTrue(report.contains("entry_commands_passed: true"), report);
        assertTrue(report.contains("acceptance_met: unproven"), report);
        assertTrue(report.contains("verdict: UNPROVEN"), report);
        assertTrue(report.contains("acceptance_all_proven: false"), report);
        assertFalse(report.contains("asserted_via_entry_command"), report);
        assertTrue(report.contains("coverage_gap: none"), report);
        assertTrue(Files.isRegularFile(pass.verifyPackage.resolve("slices/diff.md")));
        assertTrue(Files.isRegularFile(pass.verifyPackage.resolve("slices/entry.md")));
    }

    @Test
    void frozenProbeProvesEachAcceptanceAndRecordsHash() throws Exception {
        readyAtVerification("proven");
        freezeOneProbe("proven");
        VerificationControl.VerificationRecord pass = VerificationControl.run(
                temp,
                "proven",
                "mvn -q test",
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.ok(""),
                        SequenceProcessInvoker.ok("entry pass"),
                        SequenceProcessInvoker.ok("probe pass"),
                        SequenceProcessInvoker.ok("")));

        String report = new String(Files.readAllBytes(pass.report), StandardCharsets.UTF_8);
        assertEquals(VerificationOutcome.PASS, pass.outcome);
        assertTrue(report.contains("verdict: PROVEN"), report);
        assertTrue(report.contains("acceptance_all_proven: true"), report);
        assertTrue(report.contains("probe_sha256: "), report);
        assertTrue(VerificationControl.allAcceptanceProven(temp, "proven"));
    }

    @Test
    void changedFrozenProbeIsRejectedBeforeAnyTestCommand() throws Exception {
        readyAtVerification("probe-dirty");
        freezeOneProbe("probe-dirty");
        assertThrows(
                StageGateException.class,
                () -> VerificationControl.run(
                        temp,
                        "probe-dirty",
                        "mvn -q test",
                        new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                                " M .ai4se/acceptance-probes/probe-dirty/ac1.sh"))));
    }

    @Test
    void businessCodeMutationIsDerivedFromObservedDiff() throws Exception {
        readyAtVerification("business-diff");
        VerificationControl.VerificationRecord pass = VerificationControl.run(
                temp,
                "business-diff",
                "mvn -q test",
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.ok(" M src/A.java"),
                        SequenceProcessInvoker.ok("entry pass"),
                        SequenceProcessInvoker.ok(" M src/A.java")));
        String report = new String(Files.readAllBytes(pass.report), StandardCharsets.UTF_8);
        assertTrue(report.contains("business_code_mutated: true"), report);
        assertTrue(report.contains("business_changed_paths: src/A.java"), report);
    }

    @Test
    void frontendDiffWithBackendOnlyEntriesDisclosesCoverageGapButStillPasses() throws Exception {
        readyAtVerificationWithChanges(
                "fe-gap",
                Arrays.asList(
                        "src/A.java",
                        "wmp-be-frontend/src/feature.test.ts",
                        "wmp-be-frontend/src/Feature.vue"));
        VerificationControl.VerificationRecord pass = VerificationControl.run(
                temp, "fe-gap", "mvn -q test", verifyPassInvoker());
        assertEquals(VerificationOutcome.PASS, pass.outcome);
        String report = new String(Files.readAllBytes(pass.report), StandardCharsets.UTF_8);
        assertTrue(report.contains("coverage_gap: frontend"), report);
        assertTrue(report.contains("disclosure_only=true"), report);
        assertTrue(report.contains("## Coverage disclosure"), report);
        assertEquals(WorkflowStage.VERIFICATION, StoryWorkflowMachine.load(temp, "fe-gap").stage());
    }

    @Test
    void explicitQualityGateFailsBeforeReviewAndReturnsToDevelopment() throws Exception {
        readyAtVerification("quality-gate");
        Files.write(
                temp.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\n"
                        + "test:\n  - mvn -q test\n"
                        + "quality:\n  - ./scripts/check-changed-source-style.sh\n")
                        .getBytes(StandardCharsets.UTF_8));

        VerificationControl.VerificationRecord result = VerificationControl.run(
                temp,
                "quality-gate",
                "mvn -q test",
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.ok(""),
                        SequenceProcessInvoker.ok("tests pass"),
                        SequenceProcessInvoker.exit(1, "", "style gate failed"),
                        SequenceProcessInvoker.ok("")));

        assertEquals(VerificationOutcome.FAIL, result.outcome);
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.load(temp, "quality-gate").stage());
        String report = new String(Files.readAllBytes(result.report), StandardCharsets.UTF_8);
        assertTrue(report.contains("quality_gate_count: 1"), report);
        assertTrue(report.contains("quality_gates_passed: false"), report);
        String defect = new String(Files.readAllBytes(result.defectOrNull), StandardCharsets.UTF_8);
        assertTrue(defect.contains("failing_command=./scripts/check-changed-source-style.sh"), defect);
    }

    private static ProcessInvoker verifyFailInvoker() {
        return new SequenceProcessInvoker(
                SequenceProcessInvoker.ok(""),
                SequenceProcessInvoker.exit(1, "", "FAIL"),
                SequenceProcessInvoker.ok(""));
    }

    private static ProcessInvoker verifyPassInvoker() {
        return new SequenceProcessInvoker(
                SequenceProcessInvoker.ok(""),
                SequenceProcessInvoker.ok("TESTS OK"),
                SequenceProcessInvoker.ok(""));
    }

    private void freezeOneProbe(String storyId) throws Exception {
        Path root = temp.resolve(".ai4se/acceptance-probes").resolve(storyId);
        Files.createDirectories(root);
        Path probe = root.resolve("ac1.sh");
        Files.write(probe, "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
        String sha = AcceptanceProbeSet.sha256(probe);
        Files.write(
                root.resolve("probes.properties"),
                ("ac.count=1\n"
                        + "ac.1.path=.ai4se/acceptance-probes/" + storyId + "/ac1.sh\n"
                        + "ac.1.sha256=" + sha + "\n"
                        + "ac.1.command=sh .ai4se/acceptance-probes/" + storyId + "/ac1.sh\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static Path latestDevManifest(Path workspace, String storyId) throws Exception {
        Path root = workspace.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("development");
        Path latest = null;
        int max = -1;
        for (Path p : Files.newDirectoryStream(root)) {
            String name = p.getFileName().toString();
            if (name.startsWith("round-")) {
                int n = Integer.parseInt(name.substring("round-".length()));
                Path m = p.resolve("manifest.md");
                if (n > max && Files.isRegularFile(m)) {
                    max = n;
                    latest = m;
                }
            }
        }
        return latest;
    }

    private void readyAtVerification(String id) throws Exception {
        readyAtVerificationWithChanges(id, Collections.singletonList("src/A.java"));
    }

    private void readyAtVerificationWithChanges(String id, java.util.List<String> changed) throws Exception {
        Files.createDirectories(temp.resolve(".ai4se/repository"));
        Files.createDirectories(temp.resolve(".ai4se/index"));
        Files.createDirectories(temp.resolve(".story").resolve(id).resolve("packages"));
        Files.write(
                temp.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                temp.resolve(".ai4se/repository/baseline.md"),
                "# facts\n".getBytes(StandardCharsets.UTF_8));
        Files.write(
                temp.resolve(".ai4se/index/knowledge.yaml"),
                "entries: []\n".getBytes(StandardCharsets.UTF_8));
        Files.write(
                temp.resolve(".story").resolve(id).resolve("requirement.md"),
                ("## acceptance\n- isEnabled returns false when off\n").getBytes(StandardCharsets.UTF_8));
        StoryWorkflowMachine.start(temp, id);
        DiscoveryRecords.writeReport(temp, id, "facts");
        GapRecords.write(temp, id, GapStatus.CLEAR, 0, "ok");
        StoryWorkflowMachine.advance(temp, id);
        PlanRecords.writeFormalPlan(temp, id, "d", changed);
        ApprovalRecords.approvePlan(temp, id, "r", "ok");
        StoryWorkflowMachine.advance(temp, id);
        DevelopmentRecords.recordDeclaredChanges(temp, id, changed, "implement");
        DevPackageBuilder.build(temp, id);
        StoryWorkflowMachine.advance(temp, id);
        assertEquals(WorkflowStage.VERIFICATION, StoryWorkflowMachine.load(temp, id).stage());
    }
}
