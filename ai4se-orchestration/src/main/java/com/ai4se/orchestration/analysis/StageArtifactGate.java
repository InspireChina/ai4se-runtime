package com.ai4se.orchestration.analysis;

import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Hang-in gates for stage transitions (W5–W8).
 * Control calls this before advancing — not the model.
 */
public final class StageArtifactGate {

    private StageArtifactGate() {
    }

    public static void requireAdvanceAllowed(
            Path workspace, String storyId, WorkflowStage current, WorkflowStage next)
            throws IOException {
        if (current == WorkflowStage.ANALYSIS && next == WorkflowStage.PLANNING) {
            DiscoveryRecords.requireReportOrSkip(workspace, storyId);
            GapRecords.requireNotBlocked(workspace, storyId);
            return;
        }
        if (current == WorkflowStage.PLANNING && next == WorkflowStage.DEVELOPMENT) {
            PlanRecords.requireFormalPlanWithAllowed(workspace, storyId);
            ApprovalRecords.requireApproved(workspace, storyId);
            return;
        }
        if (current == WorkflowStage.DEVELOPMENT) {
            if (next != WorkflowStage.VERIFICATION) {
                throw new StageGateException(
                        "Development next stage must be Verification, was " + next);
            }
            DevelopmentRecords.requireReadyForVerification(workspace, storyId);
            return;
        }
        if (current == WorkflowStage.VERIFICATION && next == WorkflowStage.REVIEW) {
            VerificationControl.requirePassBeforeReview(workspace, storyId);
            return;
        }
        if (current == WorkflowStage.REVIEW && next == WorkflowStage.DELIVERY) {
            ReviewRecords.requirePresent(workspace, storyId);
            if (ReviewRecords.isRejected(workspace, storyId)) {
                throw new StageGateException("Review 驳回 — cannot advance to Delivery");
            }
            return;
        }
        if (current == WorkflowStage.DELIVERY) {
            DeliveryRecords.requireReady(workspace, storyId);
        }
    }
}
