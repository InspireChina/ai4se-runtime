package com.ai4se.runtime.demo.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import com.ai4se.runtime.demo.delivery.DeliveryStageResult;
import com.ai4se.runtime.demo.delivery.SerialDeliveryRunner;
import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ProductionInputLoaderTest {

    @Test
    void load_sampleInput_withoutJavaScenarioClass() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        Path input = new File(module, "sample-input").toPath();
        DeliveryScenario scenario = ProductionInputLoader.load(input);

        assertEquals("sample-config", scenario.id());
        assertEquals("production-input", scenario.projectId());
        assertTrue(scenario.requirement().contains("FeatureFlags"));
        assertEquals("git status", scenario.discoveryCommand());
        assertEquals("mvn -f pom.xml -q test", scenario.verifyCommand());
        assertTrue(scenario.planFiles().containsKey("PLAN.md"));
        assertTrue(scenario.executionFiles().containsKey(
                "src/main/java/com/example/config/FeatureFlags.java"));
        assertFalse(scenario.executionFiles().isEmpty());
    }

    @Test
    @Timeout(600)
    void productionInput_drivesFullDeliveryPipeline() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        DeliveryScenario scenario = ProductionInputLoader.load(new File(module, "sample-input").toPath());
        File workspace = WorkspaceBootstrap.prepareRunWorkspace(
                module, "sample-workspace", "production-input-work");

        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workspace, scenario);
        assertEquals(6, stages.size());
        for (DeliveryStageResult stage : stages) {
            assertTrue(stage.getResult().isSuccess(),
                    stage.getStage() + ": " + stage.getResult().getMessage());
        }
        assertTrue(new File(workspace, "PLAN.md").isFile());
        assertTrue(new File(workspace, "DELIVERY_REPORT.md").isFile());
        assertTrue(new File(workspace, "src/main/java/com/example/config/FeatureFlags.java")
                .isFile());
    }
}
