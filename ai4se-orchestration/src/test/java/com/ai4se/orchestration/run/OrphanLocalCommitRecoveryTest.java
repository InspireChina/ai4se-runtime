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
        String sha = WorkspaceGit.commitLocal(
                ws, invoker, Collections.singletonList(ResumeFixtures.ALLOWED), message);
        assertNotNull(sha);
        assertTrue(!DeliveryRecords.hasLocalCommit(ws, storyId), "simulate crash before delivery.md");

        // Retry Delivery — git has nothing to commit, but HEAD subject matches.
        String recovered = DeliveryRecords.commitLocalAndRecord(ws, storyId, message, invoker);
        assertEquals(sha, recovered);
        assertTrue(DeliveryRecords.hasLocalCommit(ws, storyId));
        assertEquals(sha, DeliveryRecords.readCommitShaOrNull(ws, storyId));
        String body = new String(Files.readAllBytes(
                DeliveryRecords.deliveryDir(ws, storyId).resolve(DeliveryRecords.FILE)),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("committed_now: false"), body);
        assertTrue(body.contains("observed: true"), body);
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
