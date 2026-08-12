package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OrphanLocalCommitRecoveryTest {

    @TempDir
    Path temp;

    @Test
    void recoversWhenCommitSucceededButDeliveryMdMissing() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "orphan");
        String storyId = "story-orphan-commit";
        Path seed = ResumeFixtures.seed(temp, "orphan");
        com.ai4se.context.story.StoryOpener.open(ws, storyId, seed);
        DiscoveryRecords.writeReport(ws, storyId, "## 摸底\n- A\n");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "clear");
        PlanRecords.writeFormalPlan(
                ws, storyId, "plan", Collections.singletonList(ResumeFixtures.ALLOWED));
        ApprovalRecords.approvePlan(ws, storyId, "t", "ok");
        StoryWorkflowMachine.save(
                ws,
                new StoryWorkflowState(storyId, WorkflowStage.REVIEW, WorkflowStatus.RUNNING, null));
        Path verifyDir = ws.resolve(".story").resolve(storyId).resolve("verification");
        Files.createDirectories(verifyDir);
        Files.write(
                verifyDir.resolve("report-round-1.md"),
                ("# Verify\n\n- outcome: PASS\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ReviewRecords.write(ws, storyId, "通过", null, ReviewRecords.SOURCE_ADAPTER);
        StoryWorkflowMachine.advance(ws, storyId); // → DELIVERY

        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        Files.write(
                ws.resolve(ResumeFixtures.ALLOWED),
                "class A { int delivered=1; }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DevelopmentRecords.recordObservedChanges(ws, storyId, "ready", invoker);

        String message = "ai4se: orphan-recovery";
        String headBefore = WorkspaceGit.headSha(ws, invoker);
        DeliveryRecords.writeCommitIntent(
                ws, storyId, headBefore, message, Collections.singletonList(ResumeFixtures.ALLOWED));
        String sha = WorkspaceGit.commitLocal(
                ws, invoker, Collections.singletonList(ResumeFixtures.ALLOWED), message);
        assertNotNull(sha);
        assertTrue(!DeliveryRecords.hasLocalCommit(ws, storyId), "simulate crash before delivery.md");
        assertTrue(Files.isRegularFile(
                DeliveryRecords.deliveryDir(ws, storyId).resolve(DeliveryRecords.INTENT_FILE)));

        // Retry Delivery — git has nothing to commit, but intent proves HEAD advanced.
        String recovered = DeliveryRecords.commitLocalAndRecord(ws, storyId, message, invoker);
        assertEquals(sha, recovered);
        assertTrue(DeliveryRecords.hasLocalCommit(ws, storyId));
        assertEquals(sha, DeliveryRecords.readCommitShaOrNull(ws, storyId));
        String body = new String(Files.readAllBytes(
                DeliveryRecords.deliveryDir(ws, storyId).resolve(DeliveryRecords.FILE)),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("committed_now: false"), body);
        assertTrue(body.contains("observed: true"), body);
        assertTrue(!Files.isRegularFile(
                DeliveryRecords.deliveryDir(ws, storyId).resolve(DeliveryRecords.INTENT_FILE)));
    }

    @Test
    void refusesOrphanRecoveryWhenHeadSubjectMatchesButWorkspaceStillDirty() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "orphan-dirty");
        String storyId = "story-orphan-dirty";
        Path seed = ResumeFixtures.seed(temp, "orphan-dirty");
        com.ai4se.context.story.StoryOpener.open(ws, storyId, seed);
        DiscoveryRecords.writeReport(ws, storyId, "## 摸底\n- A\n");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "clear");
        PlanRecords.writeFormalPlan(
                ws, storyId, "plan", Collections.singletonList(ResumeFixtures.ALLOWED));
        ApprovalRecords.approvePlan(ws, storyId, "t", "ok");
        StoryWorkflowMachine.save(
                ws,
                new StoryWorkflowState(storyId, WorkflowStage.REVIEW, WorkflowStatus.RUNNING, null));
        Path verifyDir = ws.resolve(".story").resolve(storyId).resolve("verification");
        Files.createDirectories(verifyDir);
        Files.write(
                verifyDir.resolve("report-round-1.md"),
                ("# Verify\n\n- outcome: PASS\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ReviewRecords.write(ws, storyId, "通过", null, ReviewRecords.SOURCE_ADAPTER);
        StoryWorkflowMachine.advance(ws, storyId);

        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        String message = "ai4se: same-subject";
        Files.write(
                ws.resolve(ResumeFixtures.ALLOWED),
                "class A { int committed=1; }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DevelopmentRecords.recordObservedChanges(ws, storyId, "ready", invoker);
        WorkspaceGit.commitLocal(
                ws, invoker, Collections.singletonList(ResumeFixtures.ALLOWED), message);
        // Old HEAD subject == intended message. Hook/timeout failed: tree still dirty.
        Files.write(
                ws.resolve(ResumeFixtures.ALLOWED),
                "class A { int dirty=2; }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DevelopmentRecords.recordObservedChanges(ws, storyId, "still dirty", invoker);
        String headAtFailedAttempt = WorkspaceGit.headSha(ws, invoker);
        DeliveryRecords.writeCommitIntent(
                ws,
                storyId,
                headAtFailedAttempt, // commit never advanced past this baseline
                message,
                Collections.singletonList(ResumeFixtures.ALLOWED));

        assertEquals(
                null,
                DeliveryRecords.recoverOrphanLocalCommit(ws, storyId, message, invoker),
                "same HEAD subject must not recover when HEAD did not advance / paths still dirty");
        assertTrue(!DeliveryRecords.hasLocalCommit(ws, storyId),
                "must not backfill old HEAD SHA while business paths remain dirty");

        // Even if HEAD had advanced under the same subject, dirty intent paths must refuse recovery.
        String priorHead = headAtFailedAttempt;
        Files.write(
                ws.resolve(ResumeFixtures.ALLOWED),
                "class A { int committed=3; }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String advanced = WorkspaceGit.commitLocal(
                ws, invoker, Collections.singletonList(ResumeFixtures.ALLOWED), message);
        Files.write(
                ws.resolve(ResumeFixtures.ALLOWED),
                "class A { int dirty-again=4; }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DeliveryRecords.writeCommitIntent(
                ws, storyId, priorHead, message, Collections.singletonList(ResumeFixtures.ALLOWED));
        assertTrue(!advanced.equalsIgnoreCase(priorHead));
        assertEquals(
                null,
                DeliveryRecords.recoverOrphanLocalCommit(ws, storyId, message, invoker),
                "advanced HEAD with matching subject still refuses when intent paths are dirty");
        assertTrue(!DeliveryRecords.hasLocalCommit(ws, storyId));
    }

    @Test
    void doesNotRecoverWhenHeadMessageDoesNotMatch() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "orphan-mismatch");
        String storyId = "story-orphan-mismatch";
        Path seed = ResumeFixtures.seed(temp, "orphan-mismatch");
        com.ai4se.context.story.StoryOpener.open(ws, storyId, seed);
        DiscoveryRecords.writeReport(ws, storyId, "## 摸底\n- A\n");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "clear");
        PlanRecords.writeFormalPlan(
                ws, storyId, "plan", Collections.singletonList(ResumeFixtures.ALLOWED));
        ApprovalRecords.approvePlan(ws, storyId, "t", "ok");
        StoryWorkflowMachine.save(
                ws,
                new StoryWorkflowState(storyId, WorkflowStage.REVIEW, WorkflowStatus.RUNNING, null));
        Path verifyDir = ws.resolve(".story").resolve(storyId).resolve("verification");
        Files.createDirectories(verifyDir);
        Files.write(
                verifyDir.resolve("report-round-1.md"),
                ("# Verify\n\n- outcome: PASS\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ReviewRecords.write(ws, storyId, "通过", null, ReviewRecords.SOURCE_ADAPTER);
        StoryWorkflowMachine.advance(ws, storyId);

        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        Files.write(
                ws.resolve(ResumeFixtures.ALLOWED),
                "class A { int x=2; }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DevelopmentRecords.recordObservedChanges(ws, storyId, "ready", invoker);
        WorkspaceGit.commitLocal(
                ws, invoker, Collections.singletonList(ResumeFixtures.ALLOWED), "unrelated commit");

        StageGateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                StageGateException.class,
                () -> DeliveryRecords.commitLocalAndRecord(
                        ws, storyId, "ai4se: expected-message", invoker));
        assertTrue(ex.getMessage().toLowerCase().contains("commit")
                || ex.getMessage().toLowerCase().contains("nothing"), ex.getMessage());
        assertTrue(!DeliveryRecords.hasLocalCommit(ws, storyId));
    }
}
