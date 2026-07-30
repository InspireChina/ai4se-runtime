package com.ai4se.runtime.demo.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.analysis.PlanDeclaredTargets;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.demo.input.InputDeliveryScenario;
import com.ai4se.runtime.demo.input.ProductionInputLoader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecutionPathSprintATest {

    @Test
    void continuous_analysis_to_verification_patchesBoundToPlan() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace");
        File analysisOut = new File(module, "target/execution-path-sprint-a-test/analysis");
        File patchesDir = new File(module, "execution-path-sprint-a-input/patches");

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                analysisOut.toPath(),
                null,
                false,
                "execution-path-sprint-a-test");

        assertTrue(analysis.gap.mayPlan(), "front-half must allow planning: " + analysis.gap.getStatus());
        assertTrue(analysis.planWritten);
        assertFalse(new File(analysisOut, "patches").exists());

        String plan = new String(Files.readAllBytes(new File(analysisOut, "plan.md").toPath()),
                Charset.forName("UTF-8"));
        List<String> declared = PlanDeclaredTargets.parse(plan);
        assertFalse(declared.isEmpty(), "plan must declare modification targets");
        assertTrue(declared.contains("src/main/java/com/example/order/OrderApi.java"));

        Map<String, String> patches = ProductionInputLoader.readPatches(patchesDir.toPath());
        assertTrue(PlanDeclaredTargets.findUndeclaredPatches(declared, patches).isEmpty());

        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "execution-path-sprint-a-test-work");
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", plan.endsWith("\n") ? plan : plan + "\n");
        InputDeliveryScenario scenario = new InputDeliveryScenario(
                "execution-path-sprint-a-test",
                "timeout-config",
                "execution-path-sprint-a-test",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches);

        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, scenario);
        assertEquals(6, stages.size());
        assertTrue(stageOk(stages, "EXECUTION"));
        assertTrue(stageOk(stages, "VERIFICATION"));
        assertTrue(new File(workDir, "src/main/java/com/example/order/OrderApi.java").isFile());
        String api = new String(Files.readAllBytes(
                new File(workDir, "src/main/java/com/example/order/OrderApi.java").toPath()),
                Charset.forName("UTF-8"));
        assertTrue(api.contains("app.order.timeout.ms") || api.contains("timeoutMs"));
    }

    @Test
    void undeclaredPatch_isRejectedByGate() {
        List<String> declared = Collections.singletonList("src/main/java/com/example/order/OrderApi.java");
        Map<String, String> patches = new LinkedHashMap<String, String>();
        patches.put("src/main/java/com/example/order/OrderApi.java", "ok");
        patches.put("src/main/java/com/example/evil/Hack.java", "no");
        List<String> bad = PlanDeclaredTargets.findUndeclaredPatches(declared, patches);
        assertEquals(1, bad.size());
        assertTrue(bad.get(0).contains("Hack.java"));
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
