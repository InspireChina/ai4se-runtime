package com.ai4se.runtime.demo.delivery;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.analysis.PlanAcceptance;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.demo.input.InputDeliveryScenario;
import com.ai4se.runtime.demo.input.ProductionInputLoader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoopReExecutionTest {

    @Test
    void shellFail_stillWritesDeliveryWithExecutionReturn_thenFixPasses() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File out = new File(module, "target/loop-reexecution-test/analysis");
        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                new File(module, "pilot-workspace").toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                out.toPath(),
                null,
                false,
                "loop-reexecution-test");
        String plan = new String(Files.readAllBytes(new File(out, "plan.md").toPath()),
                Charset.forName("UTF-8"));
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", plan + "\n");
        Map<String, String> noop = new LinkedHashMap<String, String>();
        noop.put("README.md", "# intentionally incomplete — does not fix timeout\n");

        File failWork = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "loop-reexecution-test-fail");
        VerificationMatrix failMatrix = VerificationMatrix.buildFromPlan(
                PlanAcceptance.parseItems(plan), failWork.toPath());
        SerialDeliveryRunner.run(failWork, new InputDeliveryScenario(
                "t-fail", "loop", "t-fail", analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY, "mvn -f pom.xml -q test",
                planFiles, noop), failMatrix);
        String failDelivery = new String(Files.readAllBytes(
                new File(failWork, "DELIVERY_REPORT.md").toPath()), Charset.forName("UTF-8"));
        assertTrue(failDelivery.contains("Delivery Result: FAIL"));
        assertTrue(failDelivery.contains("Recommended return: Execution"));

        Map<String, String> good = ProductionInputLoader.readPatches(
                new File(module, "execution-path-sprint-a-input/patches").toPath());
        File passWork = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "loop-reexecution-test-pass");
        for (Map.Entry<String, String> e : good.entrySet()) {
            java.nio.file.Path t = passWork.toPath().resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
        VerificationMatrix passMatrix = VerificationMatrix.buildFromPlan(
                PlanAcceptance.parseItems(plan), passWork.toPath());
        passWork = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "loop-reexecution-test-pass");
        SerialDeliveryRunner.run(passWork, new InputDeliveryScenario(
                "t-pass", "loop", "t-pass", analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY, "mvn -f pom.xml -q test",
                planFiles, good), passMatrix);
        String passDelivery = new String(Files.readAllBytes(
                new File(passWork, "DELIVERY_REPORT.md").toPath()), Charset.forName("UTF-8"));
        assertTrue(passDelivery.contains("Delivery Result: PASS"));
    }
}
