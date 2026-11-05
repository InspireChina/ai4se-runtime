package com.ai4se.runtime.demo.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class RequirementAnalysisPipelineTest {

    @Test
    void pilot_generatesLegalDeliveryBundle_withoutPatchesOrRuntime() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace");
        File out = new File(module, "target/pilot-delivery-bundle-test");
        if (out.exists()) {
            delete(out);
        }

        RequirementAnalysisPipeline.Result result = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                out.toPath(),
                Collections.<String, String>emptyMap());

        assertTrue(result.gap.mayPlan(), "gap should allow planning: " + result.gap.getStatus());
        assertTrue(result.planWritten);
        assertTrue(new File(out, "requirement.md").isFile());
        assertTrue(new File(out, "analysis-context.md").isFile());
        assertTrue(new File(out, "gap-report.md").isFile());
        assertTrue(new File(out, "clarification.md").isFile());
        assertTrue(new File(out, "plan.md").isFile());
        assertTrue(new File(out, "verify.yaml").isFile());
        assertTrue(new File(out, "profile.yaml").isFile());
        assertFalse(new File(out, "patches").exists(), "Analysis pilot must not write patches/");

        String plan = new String(Files.readAllBytes(new File(out, "plan.md").toPath()), Charset.forName("UTF-8"));
        assertTrue(plan.contains("Task Breakdown"));
        assertTrue(plan.contains("Declared modification targets"));
        assertTrue(plan.contains("src/main/java/com/example/order/OrderApi.java"));
        assertTrue(result.context.getCandidateFiles().stream().anyMatch(p -> p.contains("OrderApi")));

        String gap = new String(Files.readAllBytes(new File(out, "gap-report.md").toPath()), Charset.forName("UTF-8"));
        assertTrue(gap.contains("gap_status:"));
        assertEquals(GapReport.Status.ASSUMABLE, result.gap.getStatus());
    }

    private static void delete(File root) {
        File[] children = root.listFiles();
        if (children != null) {
            for (File c : children) {
                delete(c);
            }
        }
        root.delete();
    }
}
