package com.ai4se.orchestration.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Problem class: ENV_FAIL must not enter Defect Loop; VERIFY_FAIL must. */
final class VerificationEnvVsDefectClassificationTest {

    @TempDir
    Path temp;

    @Test
    void envFailExit127DoesNotWriteDefect() throws Exception {
        readyAtVerification("env-127");
        ProcessInvoker invoker = new SequenceProcessInvoker(
                SequenceProcessInvoker.ok(""),
                SequenceProcessInvoker.exit(127, "", "bash: foobar: command not found"),
                SequenceProcessInvoker.ok(""));
        StageGateException ex = assertThrows(StageGateException.class, () ->
                VerificationControl.run(temp, "env-127", "mvn -q test", invoker));
        assertTrue(ex.getMessage().contains("ENV_FAIL"), ex.getMessage());
        assertNull(DefectPackageWriter.latest(temp, "env-127"));
        assertEquals(WorkflowStage.VERIFICATION, StoryWorkflowMachine.load(temp, "env-127").stage());
    }

    @Test
    void verifyFailWritesDefectAndReturnsToDev() throws Exception {
        readyAtVerification("vf-1");
        ProcessInvoker invoker = new SequenceProcessInvoker(
                SequenceProcessInvoker.ok(""),
                SequenceProcessInvoker.exit(1, "TESTS FAILED", "AssertionError: expected true"),
                SequenceProcessInvoker.ok(""));
        VerificationControl.VerificationRecord rec =
                VerificationControl.run(temp, "vf-1", "mvn -q test", invoker);
        assertEquals(VerificationOutcome.FAIL, rec.outcome);
        assertNotNull(rec.defectOrNull);
        assertTrue(Files.isRegularFile(rec.defectOrNull));
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.load(temp, "vf-1").stage());
    }

    @Test
    void wslStubStderrClassifiedAsEnvFail() {
        ProcessInvoker.ProcessOutcome outcome = new ProcessInvoker.ProcessOutcome(
                1,
                "",
                "Windows Subsystem for Linux has no installed distributions.",
                false);
        assertTrue(VerificationControl.looksLikeEnvFailure(outcome, "true"));
        ProcessInvoker.ProcessOutcome business = new ProcessInvoker.ProcessOutcome(
                1, "FAILURE", "AssertionError at FooTest", false);
        assertFalse(VerificationControl.looksLikeEnvFailure(business, "mvn -q test"));
    }

    private void readyAtVerification(String id) throws Exception {
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
        PlanRecords.writeFormalPlan(temp, id, "d", Arrays.asList("src/A.java"));
        ApprovalRecords.approvePlan(temp, id, "r", "ok");
        StoryWorkflowMachine.advance(temp, id);
        DevelopmentRecords.recordDeclaredChanges(
                temp, id, Collections.singletonList("src/A.java"), "implement");
        DevPackageBuilder.build(temp, id);
        StoryWorkflowMachine.advance(temp, id);
        assertEquals(WorkflowStage.VERIFICATION, StoryWorkflowMachine.load(temp, id).stage());
    }
}
