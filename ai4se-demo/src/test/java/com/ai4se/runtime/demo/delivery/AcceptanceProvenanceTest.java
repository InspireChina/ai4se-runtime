package com.ai4se.runtime.demo.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcceptanceProvenanceTest {

    @Test
    void planVerifyBindings_driveMatrix_andProvenanceGate() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File analysisOut = new File(module, "target/acceptance-provenance-test/analysis");
        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                new File(module, "pilot-workspace").toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                analysisOut.toPath(),
                null,
                false,
                "acceptance-provenance-test");

        String plan = new String(Files.readAllBytes(new File(analysisOut, "plan.md").toPath()),
                Charset.forName("UTF-8"));
        List<PlanAcceptance.Item> items = PlanAcceptance.parseItems(plan);
        assertEquals(3, items.size());
        assertTrue(items.get(0).verifyRef.contains("OrderApiTest#"));
        List<String> planIds = PlanAcceptance.idsFromItems(items);

        Map<String, String> patches = ProductionInputLoader.readPatches(
                new File(module, "execution-path-sprint-a-input/patches").toPath());
        File probe = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "acceptance-provenance-test-probe");
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path t = probe.toPath().resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
        VerificationMatrix matrix = VerificationMatrix.buildFromPlan(items, probe.toPath());
        assertTrue(matrix.complete);
        assertEquals(planIds, matrix.acceptanceIds());

        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "acceptance-provenance-test-work");
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", plan.endsWith("\n") ? plan : plan + "\n");
        SerialDeliveryRunner.run(workDir, new InputDeliveryScenario(
                "acceptance-provenance-test",
                "provenance",
                "acceptance-provenance-test",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches), matrix);

        String review = new String(Files.readAllBytes(new File(workDir, "REVIEW_REPORT.md").toPath()),
                Charset.forName("UTF-8"));
        String delivery = new String(Files.readAllBytes(new File(workDir, "DELIVERY_REPORT.md").toPath()),
                Charset.forName("UTF-8"));
        List<String> reviewIds = AcceptanceProvenanceGate.extractProvenanceIds(review);
        List<String> deliveryIds = AcceptanceProvenanceGate.extractProvenanceIds(delivery);
        AcceptanceProvenanceGate.Result gate = AcceptanceProvenanceGate.check(
                planIds, matrix.acceptanceIds(), reviewIds, deliveryIds);
        assertTrue(gate.pass, gate.message);
        assertTrue(AcceptanceProvenanceGate.findInventedIds(review, planIds).isEmpty());
    }

    @Test
    void gateFailsWhenMatrixDropsId_andDetectsInventedReviewId() {
        List<String> plan = Arrays.asList("A1", "A2", "A3");
        List<String> matrix = Arrays.asList("A1", "A2");
        AcceptanceProvenanceGate.Result r = AcceptanceProvenanceGate.check(plan, matrix, plan, plan);
        assertFalse(r.pass);

        String forged = AcceptanceProvenanceGate.renderIdSetSection(plan) + "\n- **A99 PASS**\n";
        assertTrue(AcceptanceProvenanceGate.findInventedIds(forged, plan).contains("A99"));
    }
}
