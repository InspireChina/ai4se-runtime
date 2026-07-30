package com.ai4se.runtime.demo.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.analysis.PlanAcceptance;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.demo.input.InputDeliveryScenario;
import com.ai4se.runtime.demo.input.ProductionInputLoader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecutionPathSprintCTest {

    @Test
    void reviewAndDelivery_consumeMatrix_notRuntimeAlone() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File analysisOut = new File(module, "target/execution-path-sprint-c-test/analysis");
        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                new File(module, "pilot-workspace").toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                analysisOut.toPath(),
                null,
                false,
                "execution-path-sprint-c-test");

        String plan = new String(Files.readAllBytes(new File(analysisOut, "plan.md").toPath()),
                Charset.forName("UTF-8"));
        Map<String, String> acceptance = PlanAcceptance.parse(plan);
        Map<String, String> patches = ProductionInputLoader.readPatches(
                new File(module, "execution-path-sprint-a-input/patches").toPath());
        Map<String, String> mapping = VerificationMapping.load(
                new File(module, "execution-path-sprint-a-input/verification-mapping.txt").toPath());

        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "execution-path-sprint-c-test-work");
        // Seed patched sources so matrix evidence checks resolve before runner re-copies... 
        // Runner starts from clean workDir; matrix built after seeding a probe dir.
        File probe = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "execution-path-sprint-c-test-probe");
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path t = probe.toPath().resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
        VerificationMatrix matrix = VerificationMatrix.build(acceptance, mapping, probe.toPath());
        assertTrue(matrix.complete);

        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", plan.endsWith("\n") ? plan : plan + "\n");
        InputDeliveryScenario scenario = new InputDeliveryScenario(
                "execution-path-sprint-c-test",
                "consumer-chain",
                "execution-path-sprint-c-test",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches);

        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, scenario, matrix);
        assertTrue(stageOk(stages, "REVIEW"));
        assertTrue(stageOk(stages, "DELIVERY"));

        String review = new String(Files.readAllBytes(new File(workDir, "REVIEW_REPORT.md").toPath()),
                Charset.forName("UTF-8"));
        String delivery = new String(Files.readAllBytes(new File(workDir, "DELIVERY_REPORT.md").toPath()),
                Charset.forName("UTF-8"));

        assertTrue(review.contains("A1 PASS"));
        assertTrue(review.contains("from verification-matrix only"));
        assertTrue(review.contains("Delivery recommendation: PASS"));
        assertFalse(review.contains("YES — requirement verified by Maven test"));

        assertTrue(delivery.contains("Delivery Result: PASS"));
        assertTrue(delivery.contains("informational only"));
        assertTrue(new File(workDir, "verification-matrix.md").isFile());

        Map<String, String> withMissing = new LinkedHashMap<String, String>(acceptance);
        withMissing.put("A99", "missing on purpose");
        VerificationMatrix incomplete = VerificationMatrix.build(withMissing, mapping, workDir.toPath());
        ReviewVerdict verdict = ReviewReportFormatter.formatConsumingMatrix(
                analysis.requirement, stages, workDir, incomplete);
        String failDelivery = DeliveryReportFormatter.formatConsumingReview(
                analysis.requirement, stages, workDir, verdict);
        assertFalse(verdict.deliveryPass);
        assertTrue(verdict.reviewMarkdown.contains("A99 FAIL"));
        assertTrue(failDelivery.contains("Delivery Result: FAIL"));
    }

    private static boolean stageOk(List<DeliveryStageResult> stages, String name) {
        for (DeliveryStageResult s : stages) {
            if (name.equals(s.getStage())) {
                return s.getResult().isSuccess();
            }
        }
        return false;
    }
}
