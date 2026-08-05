package com.ai4se.runtime.demo.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class EngineeringValidationPipelineTest {

    @Test
    void promotionRequirement_honestUnknown_staysBlocked_noPlan() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace");
        File out = new File(module, "target/engineering-promotion-bundle-test");


        RequirementAnalysisPipeline.Result result = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                out.toPath(),
                RequirementAnalysisPipeline.promotionUnknownAnswers(),
                true,
                "engineering-promotion");

        assertEquals(GapReport.Status.BLOCKED, result.gap.getStatus());
        assertFalse(result.planWritten);
        assertFalse(result.gap.mayPlan());
        assertFalse(result.context.getNeedClarification().isEmpty());
        assertTrue(result.context.getNeedClarification().stream()
                .anyMatch(q -> q.contains("Promotion") || q.contains("促销") || q.contains("满减")));

        String gap = new String(Files.readAllBytes(new File(out, "gap-report.md").toPath()),
                Charset.forName("UTF-8"));
        assertTrue(gap.contains("gap_status: BLOCKED"));
        assertTrue(gap.contains("UNKNOWN") || gap.contains("must not invent"));

        assertFalse(new File(out, "plan.md").exists());
        assertFalse(new File(out, "patches").exists());

        String facts = new String(Files.readAllBytes(new File(out, "facts.md").toPath()),
                Charset.forName("UTF-8"));
        assertFalse(facts.contains("PromotionRepository"));
    }
}
