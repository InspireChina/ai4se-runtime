package com.ai4se.orchestration.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryOpener;
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
import com.ai4se.execution.support.ProcessInvoker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W9 Session decision audit — role switch = new; resume|new fields auditable. */
final class PathwayW9SessionCompressIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void roleSwitchWritesNewSessionDecisions() throws Exception {
        Path ws = readyWorkspace("story-w9");
        StoryWorkflowMachine.start(ws, "story-w9");
        DiscoveryRecords.writeSkip(ws, "story-w9", "known fixture", "tester");
        GapRecords.write(ws, "story-w9", GapStatus.CLEAR, 0, "clear");
        StoryWorkflowMachine.advance(ws, "story-w9"); // → PLANNING
        PlanRecords.writeFormalPlan(ws, "story-w9", "plan", java.util.Arrays.asList("src/A.java"));
        ApprovalRecords.approvePlan(ws, "story-w9", "tester", "ok");
        StoryWorkflowMachine.advance(ws, "story-w9"); // → DEVELOPMENT

        SessionDecisionRecorder.requireAuditableDecisions(ws, "story-w9");
        SessionDecisionRecorder.requireRoleSwitchesAreNew(ws, "story-w9");
        assertTrue(SessionDecisionRecorder.decisionCount(ws, "story-w9") >= 3);

        String log = new String(
                Files.readAllBytes(SessionDecisionRecorder.sessionsDir(ws, "story-w9")
                        .resolve("decisions.md")),
                StandardCharsets.UTF_8);
        assertTrue(log.contains("decision: new") || log.contains(": new |"));
        assertTrue(log.contains("Analysis"));
        assertTrue(log.contains("Planning"));
        assertTrue(log.contains("Development"));
    }

    @Test
    void roleSwitchCannotResume() {
        assertThrows(StageGateException.class, () ->
                SessionDecisionRecorder.record(
                        temp,
                        "x",
                        WorkflowStage.DEVELOPMENT,
                        WorkflowStage.VERIFICATION,
                        SessionAction.RESUME,
                        "illegal"));
    }

    @Test
    void sameRoleResumeKeepsSessionId() throws Exception {
        Path ws = readyWorkspace("story-resume");
        SessionDecisionRecorder.openNew(
                ws, "story-resume", WorkflowStage.DEVELOPMENT, "open Dev");
        SessionDecisionRecorder.SessionDecision r1 =
                SessionDecisionRecorder.resumeSameRole(
                        ws, "story-resume", WorkflowStage.DEVELOPMENT, "flash interrupt resume");
        assertEquals(SessionAction.RESUME, r1.action);
        String current = new String(
                Files.readAllBytes(SessionDecisionRecorder.sessionsDir(ws, "story-resume")
                        .resolve("current.properties")),
                StandardCharsets.UTF_8);
        assertTrue(current.contains("last_decision=resume"));
        assertTrue(current.contains("session_id=" + r1.sessionId));
    }

    @Test
    void defectLoopRecordsNewSessionOnReturnToDev() throws Exception {
        Path ws = readyWorkspace("story-loop");
        // drive to VERIFICATION
        StoryWorkflowMachine.start(ws, "story-loop");
        DiscoveryRecords.writeSkip(ws, "story-loop", "skip", "t");
        GapRecords.write(ws, "story-loop", GapStatus.CLEAR, 0, "c");
        StoryWorkflowMachine.advance(ws, "story-loop");
        PlanRecords.writeFormalPlan(ws, "story-loop", "p", java.util.Arrays.asList("src/A.java"));
        ApprovalRecords.approvePlan(ws, "story-loop", "t", "ok");
        StoryWorkflowMachine.advance(ws, "story-loop"); // → DEVELOPMENT
        Path src = ws.resolve("src/A.java");
        Files.createDirectories(src.getParent());
        Files.write(src, "// a\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        // need git for porcelain — init quick
        gitInit(ws);
        DevelopmentRecords.recordObservedChanges(ws, "story-loop", "impl", invoker);
        DevPackageBuilder.build(ws, "story-loop");
        StoryWorkflowMachine.advance(ws, "story-loop"); // → VERIFICATION

        int before = SessionDecisionRecorder.decisionCount(ws, "story-loop");
        StoryWorkflowMachine.returnToDevelopment(ws, "story-loop", "Verify FAIL");
        int after = SessionDecisionRecorder.decisionCount(ws, "story-loop");
        assertEquals(before + 1, after);

        Path latest = SessionDecisionRecorder.listHopFiles(ws, "story-loop").stream()
                .sorted()
                .reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no hops"));
        String text = new String(Files.readAllBytes(latest), StandardCharsets.UTF_8);
        assertTrue(text.contains("decision: new"));
        assertTrue(text.contains("from_role: Verification"));
        assertTrue(text.contains("to_role: Development"));
        SessionDecisionRecorder.requireRoleSwitchesAreNew(ws, "story-loop");
    }

    private Path readyWorkspace(String storyId) throws Exception {
        Path ws = temp.resolve(storyId + "-ws");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve(storyId + "-seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac-1\n")
                .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, storyId, seed);
        return ws;
    }

    private static void gitInit(Path ws) throws Exception {
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "--allow-empty", "-m", "init");
    }

    private static void run(ProcessInvoker invoker, Path cwd, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome o = invoker.run(
                java.util.Arrays.asList(argv), cwd, null, java.time.Duration.ofMinutes(1));
        if (o.exitCode != 0) {
            throw new IllegalStateException("cmd failed: " + java.util.Arrays.toString(argv)
                    + "\n" + o.stderr);
        }
    }
}
