package com.ai4se.orchestration.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.orchestration.verification.VerifyPackageBuilder;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W7→W8：PASS → Review → Delivery（Commit 不 Push）— observed gates. */
final class PathwayW7W8IntegrationTest {

    @TempDir
    Path temp;

    @Test
    void passReviewLocalCommitCompletes() throws Exception {
        String id = "v3";
        readyThroughDev(id);
        StoryWorkflowMachine.advance(temp, id); // → VERIFICATION
        VerificationControl.run(temp, id, "mvn -q test", verifyPassInvoker());
        assertEquals(WorkflowStage.REVIEW, StoryWorkflowMachine.advance(temp, id).stage());
        ReviewRecords.write(temp, id, "通过", "residual: none");
        assertEquals(WorkflowStage.DELIVERY, StoryWorkflowMachine.advance(temp, id).stage());
        DeliveryRecords.recordLocalCommit(
                temp, id,
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.ok("## main\n"),
                        SequenceProcessInvoker.ok("abc123deadbeef01\n")));
        assertEquals(
                WorkflowStatus.COMPLETED,
                StoryWorkflowMachine.complete(temp, id).status());
        assertTrue(DeliveryRecords.isReady(temp, id));
        String delivery = new String(Files.readAllBytes(
                temp.resolve(".story/" + id + "/delivery/delivery.md")), StandardCharsets.UTF_8);
        assertTrue(delivery.contains("observed: true"));
        assertTrue(delivery.contains("abc123deadbeef01"));
    }

    @Test
    void defectLoopThenPassThenAwaitingCommit() throws Exception {
        String id = "v4";
        readyThroughDev(id);
        StoryWorkflowMachine.advance(temp, id);
        VerificationControl.run(temp, id, "mvn -q test", verifyFailInvoker());
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.load(temp, id).stage());
        DevPackageBuilder.build(temp, id);
        DevelopmentRecords.recordObservedChanges(
                temp, id, "fix defect",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(" M src/A.java")));
        StoryWorkflowMachine.advance(temp, id);
        VerificationControl.run(temp, id, "mvn -q test", verifyPassInvoker());
        assertEquals(2, VerifyPackageBuilder.nextRound(temp, id) - 1);
        StoryWorkflowMachine.advance(temp, id);
        ReviewRecords.write(temp, id, "通过", "monitor flag");
        StoryWorkflowMachine.advance(temp, id);
        DeliveryRecords.recordAwaitingHumanCommit(temp, id);
        StoryWorkflowMachine.complete(temp, id);
        assertTrue(Files.readAllBytes(
                temp.resolve(".story/" + id + "/delivery/delivery.md")).length > 0);
        String remanifest = new String(Files.readAllBytes(
                temp.resolve(".story/" + id + "/packages/development/round-2/manifest.md")),
                StandardCharsets.UTF_8);
        assertTrue(remanifest.contains("defect"));
    }

    @Test
    void pushForbidden() throws Exception {
        String id = "push";
        readyThroughDev(id);
        StoryWorkflowMachine.advance(temp, id);
        VerificationControl.run(temp, id, "mvn -q test", verifyPassInvoker());
        StoryWorkflowMachine.advance(temp, id);
        ReviewRecords.write(temp, id, "通过", "");
        StoryWorkflowMachine.advance(temp, id);
        assertThrows(StageGateException.class, () -> DeliveryRecords.rejectPushAttempt(temp, id));
        assertThrows(
                StageGateException.class,
                () -> DeliveryRecords.recordLocalCommit(
                        temp, id,
                        new SequenceProcessInvoker(
                                SequenceProcessInvoker.ok("## main...origin/main\n"),
                                SequenceProcessInvoker.ok("abc123deadbeef01\n"))));
    }

    @Test
    void reviewWithoutVerifyPassRejected() throws Exception {
        String id = "norev";
        readyThroughDev(id);
        StoryWorkflowMachine.advance(temp, id);
        assertThrows(
                StageGateException.class,
                () -> ReviewRecords.write(temp, id, "通过", ""));
    }

    private static SequenceProcessInvoker verifyPassInvoker() {
        return new SequenceProcessInvoker(
                SequenceProcessInvoker.ok(""),
                SequenceProcessInvoker.ok("ok"),
                SequenceProcessInvoker.ok(""));
    }

    private static SequenceProcessInvoker verifyFailInvoker() {
        return new SequenceProcessInvoker(
                SequenceProcessInvoker.ok(""),
                SequenceProcessInvoker.exit(1, "", "fail"),
                SequenceProcessInvoker.ok(""));
    }

    private void readyThroughDev(String id) throws Exception {
        Files.createDirectories(temp.resolve(".ai4se/repository"));
        Files.createDirectories(temp.resolve(".ai4se/index"));
        Files.createDirectories(temp.resolve(".story").resolve(id).resolve("packages"));
        Files.write(
                temp.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(temp.resolve(".ai4se/repository/baseline.md"), "# f\n".getBytes(StandardCharsets.UTF_8));
        Files.write(temp.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        Files.write(
                temp.resolve(".story").resolve(id).resolve("requirement.md"),
                ("## acceptance\n- ac\n").getBytes(StandardCharsets.UTF_8));
        StoryWorkflowMachine.start(temp, id);
        DiscoveryRecords.writeSkip(temp, id, "known", "o");
        GapRecords.write(temp, id, GapStatus.CLEAR, 0, "ok");
        StoryWorkflowMachine.advance(temp, id);
        PlanRecords.writeFormalPlan(temp, id, "d", Arrays.asList("src/A.java"));
        ApprovalRecords.approvePlan(temp, id, "r", "ok");
        StoryWorkflowMachine.advance(temp, id);
        DevelopmentRecords.recordDeclaredChanges(
                temp, id, Collections.singletonList("src/A.java"), "impl");
        DevPackageBuilder.build(temp, id);
    }
}
