package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.engine.api.RuntimeResult;
import java.io.File;
import java.util.List;

/**
 * Delivery Review — Sprint C path consumes {@link VerificationMatrix}.
 * Legacy {@link #format} kept for First Production / stress (pre-matrix).
 */
final class ReviewReportFormatter {

    private ReviewReportFormatter() {
    }

    /** Legacy: mvn-centric review (pre Sprint C). Prefer {@link #formatConsumingMatrix}. */
    static String format(String requirement, List<DeliveryStageResult> stages, File workspace) {
        boolean delivered = stages.size() >= 4
                && "VERIFICATION".equals(stages.get(3).getStage())
                && stages.get(3).getResult().isSuccess();
        StringBuilder sb = new StringBuilder();
        sb.append("# Delivery Review Report\n\n");
        sb.append("## Requirement\n\n").append(requirement).append("\n\n");
        sb.append("## Actual Execution Steps\n\n");
        appendStages(sb, stages);
        sb.append("\n## Verification\n\n");
        sb.append("Verification stage ran `mvn -f pom.xml -q test` via ShellWorker against ")
                .append(workspace.getAbsolutePath())
                .append(".\n\n");
        sb.append("## Acceptance\n\n");
        sb.append("- VERIFICATION SUCCEEDED: ").append(delivered).append('\n');
        sb.append("\n## Delivery Completed?\n\n");
        sb.append(delivered ? "YES — requirement verified by Maven test.\n\n"
                : "NO — verification or earlier stage failed.\n\n");
        sb.append("## Note\n\n");
        sb.append("Legacy review (no Verification Matrix). Sprint C path uses formatConsumingMatrix.\n");
        return sb.toString();
    }

    /**
     * Sprint C: Review lists each Acceptance from the matrix; does not invent a separate PASS summary.
     */
    static ReviewVerdict formatConsumingMatrix(
            String requirement,
            List<DeliveryStageResult> stages,
            File workspace,
            VerificationMatrix matrix) {
        boolean shellOk = false;
        for (DeliveryStageResult stage : stages) {
            if ("VERIFICATION".equals(stage.getStage())) {
                shellOk = stage.getResult().isSuccess();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Delivery Review Report\n\n");
        sb.append("## Requirement\n\n").append(requirement).append("\n\n");
        sb.append("## Consumer chain\n\n");
        sb.append("Plan Acceptance → Verification Matrix → **this Review** → Delivery\n\n");
        sb.append("## Actual Execution Steps\n\n");
        appendStages(sb, stages);
        sb.append("\n## Shell Verification (necessary, not sufficient)\n\n");
        sb.append("- workspace: `").append(workspace.getAbsolutePath()).append("`\n");
        sb.append("- mvn/shell success: ").append(shellOk).append("\n\n");
        sb.append("## Acceptance verdicts (from verification-matrix only)\n\n");
        if (matrix == null || matrix.rows.isEmpty()) {
            sb.append("- **FAIL** — no Verification Matrix rows to consume\n\n");
        } else {
            for (VerificationMatrix.Row row : matrix.rows) {
                String passFail = row.status == VerificationMatrix.Status.COVERED ? "PASS" : "FAIL";
                sb.append("- **").append(row.id).append(" ").append(passFail).append("** — ")
                        .append(row.criterion)
                        .append(" _(evidence: `").append(row.evidenceRef).append("`, matrix=")
                        .append(row.status).append(")_\n");
            }
            sb.append('\n');
        }
        List<String> idSet = matrix == null ? java.util.Collections.<String>emptyList() : matrix.acceptanceIds();
        sb.append(AcceptanceProvenanceGate.renderIdSetSection(idSet));
        boolean matrixOk = matrix != null && matrix.complete;
        boolean deliveryPass = shellOk && matrixOk;
        sb.append("## Review decision\n\n");
        if (deliveryPass) {
            sb.append("- **Delivery recommendation: PASS**\n");
            sb.append("- Reason: shell Verification OK and all Acceptance IDs COVERED in matrix.\n\n");
        } else {
            sb.append("- **Delivery recommendation: FAIL**\n");
            if (!shellOk) {
                sb.append("- Reason: shell Verification failed.\n");
            }
            if (!matrixOk) {
                sb.append("- Reason: Verification Matrix incomplete (MISSING Acceptance) — ")
                        .append(matrix == null ? "matrix null" : ("missing=" + matrix.missing))
                        .append(".\n");
            }
            sb.append('\n');
        }
        sb.append("## Provenance discipline\n\n");
        sb.append("- Review **must not** invent Acceptance IDs; only consume Plan → Matrix IDs.\n");
        sb.append("- Delivery **must not** invent Acceptance IDs.\n");
        sb.append("## Remaining risks (recorded, not solved here)\n\n");
        sb.append("- Matrix dual-maintenance when a parallel verification-mapping.txt is used ")
                .append("(Sprint B fixture path); Plan→Matrix path is provenance-gated separately.\n");
        sb.append("- Patch Coverage ≠ Acceptance coverage.\n\n");
        sb.append("## Runtime boundary\n\n");
        sb.append("- Review assembled in Demo/Delivery layer; Runtime Kernel unchanged.\n");
        return new ReviewVerdict(shellOk, matrix, sb.toString());
    }

    private static void appendStages(StringBuilder sb, List<DeliveryStageResult> stages) {
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            sb.append("1. **").append(stage.getStage()).append("** — ")
                    .append(stage.getRequirementNote())
                    .append(" → ").append(r.getTaskStatus())
                    .append(" (").append(r.getWorkerId()).append(", ")
                    .append(r.getDurationMs()).append("ms)\n");
        }
    }
}
