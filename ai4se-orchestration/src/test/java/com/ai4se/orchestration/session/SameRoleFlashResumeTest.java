package com.ai4se.orchestration.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.execution.support.SplitProcessInvoker;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.orchestration.verification.VerificationOutcome;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Appendix A · 闪断 Resume：杀进程后续跑，不丢 Allowed/Defect. */
final class SameRoleFlashResumeTest {

    @TempDir
    Path temp;

    @Test
    void resumeAfterDevInterruptKeepsAllowedAndSessionId() throws Exception {
        Path ws = driveToDevelopment("flash-dev");
        String sessionBefore = readSessionId(ws, "flash-dev");

        // simulate process death: only .story remains; no in-memory Control
        SameRoleFlashResume.ResumeSnapshot snap = SameRoleFlashResume.resumeAfterInterrupt(
                ws, "flash-dev", "jvm killed mid-Development");

        assertEquals(WorkflowStage.DEVELOPMENT, snap.stage);
        assertEquals(sessionBefore, snap.sessionId);
        assertTrue(snap.allowedFiles.contains("src/A.java"));
        assertNotNull(snap.rebuiltPackageOrNull);
        assertTrue(Files.isRegularFile(snap.auditFile));
        String audit = new String(Files.readAllBytes(snap.auditFile), StandardCharsets.UTF_8);
        assertTrue(audit.contains("decision: resume"));
        assertTrue(audit.contains("allowed_files:"));
        assertTrue(audit.contains("checkpoint_product: false"));

        // workflow still RUNNING at DEVELOPMENT
        assertEquals(WorkflowStatus.RUNNING, StoryWorkflowMachine.load(ws, "flash-dev").status());
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.load(ws, "flash-dev").stage());
    }

    @Test
    void resumeAfterDefectLoopKeepsDefectInRebuiltDevPackage() throws Exception {
        Path ws = driveToDevelopment("flash-loop");
        gitInit(ws);
        Files.write(ws.resolve("src/A.java"), "// a\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(SequenceProcessInvoker.exit(1, "", "FAIL")));
        DevelopmentRecords.recordObservedChanges(ws, "flash-loop", "impl", invoker);
        StoryWorkflowMachine.advance(ws, "flash-loop"); // → VERIFICATION
        VerificationControl.VerificationRecord fail =
                VerificationControl.run(ws, "flash-loop", "mvn -q test", invoker);
        assertEquals(VerificationOutcome.FAIL, fail.outcome);
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.load(ws, "flash-loop").stage());
        assertNotNull(fail.defectOrNull);
        assertTrue(Files.isRegularFile(fail.defectOrNull));

        // flash interrupt while back on Development with Defect
        SameRoleFlashResume.ResumeSnapshot snap = SameRoleFlashResume.resumeAfterInterrupt(
                ws, "flash-loop", "killed after Verify FAIL loop");

        assertEquals(WorkflowStage.DEVELOPMENT, snap.stage);
        assertNotNull(snap.defectOrNull);
        assertTrue(Files.isRegularFile(snap.defectOrNull));
        assertTrue(snap.allowedFiles.contains("src/A.java"));
        String manifest = new String(
                Files.readAllBytes(snap.rebuiltPackageOrNull.resolve("manifest.md")),
                StandardCharsets.UTF_8);
        assertTrue(manifest.toLowerCase().contains("defect"));
    }

    @Test
    void refuseResumeWhenCompleted() throws Exception {
        Path ws = driveToDevelopment("flash-done");
        // force COMPLETED illegally for gate test — stop instead
        StoryWorkflowMachine.stop(ws, "flash-done", "budget exhausted");
        assertThrows(StageGateException.class, () ->
                SameRoleFlashResume.resumeAfterInterrupt(ws, "flash-done", "nope"));
    }

    @Test
    void refuseResumeIfAllowedDeletedFromDisk() throws Exception {
        Path ws = driveToDevelopment("flash-lost");
        Files.delete(ws.resolve(".story/flash-lost/planning/plan.md"));
        StageGateException ex = assertThrows(StageGateException.class, () ->
                SameRoleFlashResume.resumeAfterInterrupt(ws, "flash-lost", "plan wiped"));
        assertTrue(ex.getMessage().toLowerCase().contains("plan")
                || ex.getMessage().toLowerCase().contains("allowed"));
    }

    private Path driveToDevelopment(String storyId) throws Exception {
        Path ws = temp.resolve(storyId + "-ws");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path seed = temp.resolve(storyId + "-seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac-1\n")
                .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, storyId, seed);
        StoryWorkflowMachine.start(ws, storyId);
        DiscoveryRecords.writeSkip(ws, storyId, "fixture", "t");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "c");
        StoryWorkflowMachine.advance(ws, storyId);
        PlanRecords.writeFormalPlan(ws, storyId, "p", Arrays.asList("src/A.java"));
        ApprovalRecords.approvePlan(ws, storyId, "t", "ok");
        StoryWorkflowMachine.advance(ws, storyId);
        Files.createDirectories(ws.resolve("src"));
        return ws;
    }

    private static String readSessionId(Path ws, String storyId) throws Exception {
        Path cur = SessionDecisionRecorder.sessionsDir(ws, storyId).resolve("current.properties");
        for (String line : Files.readAllLines(cur, StandardCharsets.UTF_8)) {
            if (line.startsWith("session_id=")) {
                return line.substring("session_id=".length()).trim();
            }
        }
        throw new AssertionError("no session_id");
    }

    private static void gitInit(Path ws) throws Exception {
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "--allow-empty", "-m", "init");
    }

    private static void run(ProcessInvoker invoker, Path cwd, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome o = invoker.run(
                Arrays.asList(argv), cwd, null, java.time.Duration.ofMinutes(1));
        if (o.exitCode != 0) {
            throw new IllegalStateException(Arrays.toString(argv) + "\n" + o.stderr);
        }
    }
}
