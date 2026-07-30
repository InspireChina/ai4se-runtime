package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.engine.api.RuntimeResult;
import java.io.File;
import java.util.List;

/**
 * Delivery report — Sprint C path consumes {@link ReviewVerdict}, not Runtime SUCCESS alone.
 */
final class DeliveryReportFormatter {

    private DeliveryReportFormatter() {
    }

    /** Legacy: Runtime-stage summary only. Prefer {@link #formatConsumingReview}. */
    static String format(String requirement, List<DeliveryStageResult> stages, File workspace) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Runtime Delivery Report\n\n");
        sb.append("## Goal\n\n").append(requirement).append("\n\n");
        sb.append("## Workspace\n\n").append(workspace.getAbsolutePath()).append("\n\n");
        sb.append("## Stages\n\n");
        long totalMs = 0L;
        boolean allOk = true;
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            totalMs += r.getDurationMs();
            allOk &= r.isSuccess();
            appendStage(sb, stage, r);
        }
        sb.append("## Summary\n\n");
        sb.append("- AllStagesSucceeded: ").append(allOk).append('\n');
        sb.append("- TotalDurationMs: ").append(totalMs).append('\n');
        sb.append("- Note: Legacy delivery (no ReviewVerdict). Sprint C uses formatConsumingReview.\n");
        return sb.toString();
    }

    /**
     * Sprint C: Delivery Result follows Review recommendation (matrix-backed).
     * Runtime stage success is reported but cannot alone set Delivery PASS.
     */
    static String formatConsumingReview(
            String requirement,
            List<DeliveryStageResult> stages,
            File workspace,
            ReviewVerdict verdict) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Delivery Report\n\n");
        sb.append("## Goal\n\n").append(requirement).append("\n\n");
        sb.append("## Workspace\n\n").append(workspace.getAbsolutePath()).append("\n\n");
        sb.append("## Consumer chain\n\n");
        sb.append("Plan Acceptance → Verification Matrix → Review → **this Delivery**\n\n");
        sb.append("## Runtime stages (transport only)\n\n");
        long totalMs = 0L;
        boolean allRuntimeOk = true;
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            totalMs += r.getDurationMs();
            allRuntimeOk &= r.isSuccess();
            appendStage(sb, stage, r);
        }
        sb.append("## Review consumption\n\n");
        if (verdict == null) {
            sb.append("- ReviewVerdict: **missing** → Delivery Result FORCE FAIL\n\n");
        } else {
            sb.append("- shellVerificationOk: ").append(verdict.shellVerificationOk).append('\n');
            sb.append("- matrixComplete: ").append(verdict.matrixComplete).append('\n');
            sb.append("- Review deliveryPass: ").append(verdict.deliveryPass).append('\n');
            if (verdict.matrix != null) {
                for (VerificationMatrix.Row row : verdict.matrix.rows) {
                    sb.append("- matrix ").append(row.id).append(": ").append(row.status).append('\n');
                }
            }
            sb.append('\n');
        }
        List<String> idSet = (verdict != null && verdict.matrix != null)
                ? verdict.matrix.acceptanceIds()
                : java.util.Collections.<String>emptyList();
        sb.append(AcceptanceProvenanceGate.renderIdSetSection(idSet));
        boolean deliveryPass = verdict != null && verdict.deliveryPass;
        sb.append("## Delivery Result\n\n");
        sb.append("- **Delivery Result: ").append(deliveryPass ? "PASS" : "FAIL").append("**\n");
        sb.append("- Runtime AllStagesSucceeded: ").append(allRuntimeOk)
                .append(" _(informational only; not sufficient)_\n");
        sb.append("- TotalDurationMs: ").append(totalMs).append('\n');
        if (deliveryPass) {
            sb.append("- Basis: Review consumed Verification Matrix; all Acceptance PASS.\n");
        } else {
            sb.append("- Basis: Review recommended FAIL (matrix MISSING and/or shell verify fail), ")
                    .append("or ReviewVerdict absent — Runtime SUCCESS does not override.\n");
            sb.append('\n');
            sb.append(LoopReturnAdvisor.renderForVerdict(verdict));
        }
        sb.append("- Provenance: Delivery must not invent Acceptance IDs beyond Plan.\n");
        return sb.toString();
    }

    private static void appendStage(StringBuilder sb, DeliveryStageResult stage, RuntimeResult r) {
        sb.append("### ").append(stage.getStage()).append("\n\n");
        sb.append("- TaskId: ").append(r.getTaskId().value()).append('\n');
        sb.append("- Worker: ").append(r.getWorkerId()).append('\n');
        sb.append("- Success: ").append(r.isSuccess()).append('\n');
        sb.append("- DurationMs: ").append(r.getDurationMs()).append('\n');
        sb.append("- Checkpoint: ")
                .append(r.getCheckpointId().isPresent() ? r.getCheckpointId().get().value() : "(none)")
                .append('\n');
        sb.append("- Artifacts: ").append(stage.artifactIdValues()).append('\n');
        sb.append('\n');
    }
}
