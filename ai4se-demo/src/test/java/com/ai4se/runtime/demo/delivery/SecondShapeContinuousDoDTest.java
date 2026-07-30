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

class SecondShapeContinuousDoDTest {

    @Test
    void restApiWorkspace_continuousChain_notTimeoutShape() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File analysisOut = new File(module, "target/second-shape-continuous-test/analysis");

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                new File(module, "stress-workspaces/02-rest-api").toPath(),
                RequirementAnalysisPipeline.REST_API_REQUIREMENT,
                analysisOut.toPath(),
                null,
                false,
                "second-shape-continuous-test");

        assertTrue(analysis.gap.mayPlan());
        String plan = new String(Files.readAllBytes(new File(analysisOut, "plan.md").toPath()),
                Charset.forName("UTF-8"));
        assertTrue(plan.contains("UserApi"));
        assertFalse(plan.contains("ConfigService.timeoutMs"));

        List<PlanAcceptance.Item> items = PlanAcceptance.parseItems(plan);
        Map<String, String> patches = ProductionInputLoader.readPatches(
                new File(module, "second-shape-continuous-input/patches").toPath());
        File probe = WorkspaceBootstrap.prepareRunWorkspace(
                module, "stress-workspaces/02-rest-api", "second-shape-continuous-test-probe");
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path t = probe.toPath().resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
        VerificationMatrix matrix = VerificationMatrix.buildFromPlan(items, probe.toPath());
        assertTrue(matrix.complete, matrix.toMarkdown());

        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "stress-workspaces/02-rest-api", "second-shape-continuous-test-work");
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", plan.endsWith("\n") ? plan : plan + "\n");
        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, new InputDeliveryScenario(
                "second-shape-test",
                "rest-api",
                "second-shape-test",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches), matrix);
        assertTrue(stageOk(stages, "VERIFICATION"));
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
