package com.ai4se.runtime.demo.analysis;

import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;

/**
 * Engineering Validation — Promotion Analysis must STOP at Clarification when UNKNOWN.
 * Playbook §2.1: BLOCKED forbids Planning. No patches / no Runtime changes.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.analysis.EngineeringValidationMain
 * </pre>
 */
public final class EngineeringValidationMain {

    private EngineeringValidationMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace-promotion");
        File out = new File(module, "pilot-delivery-bundle");

        System.out.println("=== Engineering Validation: Promotion Analysis (§2.1 gate) ===");
        System.out.println("workspace = " + workspace.getAbsolutePath());
        System.out.println("out       = " + out.getAbsolutePath());

        RequirementAnalysisPipeline.Result result = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                out.toPath(),
                RequirementAnalysisPipeline.promotionUnknownAnswers(),
                true,
                "engineering-promotion");

        System.out.println("gap_status = " + result.gap.getStatus());
        System.out.println("blocking   = " + result.gap.getBlockingGapCount());
        System.out.println("plan       = " + result.planWritten);
        System.out.println("needClarify= " + result.context.getNeedClarification().size());

        if (result.gap.getStatus() != GapReport.Status.BLOCKED) {
            System.err.println("FAILED: UNKNOWN answers must keep gap_status=BLOCKED, got "
                    + result.gap.getStatus());
            System.exit(2);
        }
        if (result.planWritten || result.gap.mayPlan()) {
            System.err.println("FAILED: Planning forbidden while BLOCKED (playbook §2.1).");
            System.exit(3);
        }
        if (result.context.getNeedClarification().isEmpty()) {
            System.err.println("FAILED: expected clarification questions from Unknown.");
            System.exit(4);
        }
        if (new File(out, "plan.md").exists()) {
            System.err.println("FAILED: plan.md must not exist when BLOCKED.");
            System.exit(5);
        }
        if (new File(out, "patches").exists()) {
            System.err.println("FAILED: patches/ must not exist in Analysis.");
            System.exit(6);
        }
        System.out.println("Engineering Validation OK — Analysis→Clarification stop; no Plan under UNKNOWN.");
    }
}
