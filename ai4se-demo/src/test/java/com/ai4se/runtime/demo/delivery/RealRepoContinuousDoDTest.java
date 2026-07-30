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

class RealRepoContinuousDoDTest {

    @Test
    void firstDeliveryWorkspace_continuousChain_configServiceNotOrderPilot() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File analysisOut = new File(module, "target/real-repo-continuous-test/analysis");
        Map<String, String> answers = new LinkedHashMap<String, String>();
        answers.put("timeout", "app.timeout.ms=5000");

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                new File(module, "first-delivery-workspace").toPath(),
                RequirementAnalysisPipeline.FIRST_DELIVERY_REQUIREMENT,
                analysisOut.toPath(),
                answers,
                false,
                "real-repo-continuous-test");

        assertTrue(analysis.gap.mayPlan());
        String plan = new String(Files.readAllBytes(new File(analysisOut, "plan.md").toPath()),
                Charset.forName("UTF-8"));
        assertTrue(plan.contains("ConfigService"));
        assertFalse(plan.contains("OrderApi.timeoutMs"));

        List<PlanAcceptance.Item> items = PlanAcceptance.parseItems(plan);
        Map<String, String> patches = ProductionInputLoader.readPatches(
                new File(module, "real-repo-continuous-input/patches").toPath());

        File probe = WorkspaceBootstrap.prepareRunWorkspace(
                module, "first-delivery-workspace", "real-repo-continuous-test-probe");
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path t = probe.toPath().resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
        VerificationMatrix matrix = VerificationMatrix.buildFromPlan(items, probe.toPath());
        assertTrue(matrix.complete);

        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "first-delivery-workspace", "real-repo-continuous-test-work");
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", plan.endsWith("\n") ? plan : plan + "\n");
        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, new InputDeliveryScenario(
                "real-repo-continuous-test",
                "first-delivery",
                "real-repo-continuous-test",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches), matrix);

        assertTrue(stageOk(stages, "VERIFICATION"));
        assertTrue(stageOk(stages, "DELIVERY"));
        String delivery = new String(Files.readAllBytes(new File(workDir, "DELIVERY_REPORT.md").toPath()),
                Charset.forName("UTF-8"));
        assertTrue(delivery.contains("Delivery Result: PASS"));
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
