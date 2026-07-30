package com.ai4se.runtime.demo.delivery.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import com.ai4se.runtime.demo.delivery.DeliveryStageResult;
import com.ai4se.runtime.demo.delivery.SerialDeliveryRunner;
import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BoundaryStressTest {

    @Test
    @Timeout(900)
    void threeDifferentDeliveries_shareStableRuntimeBoundary() throws Exception {
        File moduleRoot = WorkspaceBootstrap.resolveDemoModuleRoot();
        List<DeliveryScenario> scenarios = Arrays.<DeliveryScenario>asList(
                new ConfigChangeScenario(),
                new RestApiScenario(),
                new CrossModuleScenario());

        List<BoundaryStressMain.StressDeliveryOutcome> outcomes =
                new java.util.ArrayList<BoundaryStressMain.StressDeliveryOutcome>();

        for (DeliveryScenario scenario : scenarios) {
            File workspace = WorkspaceBootstrap.prepareRunWorkspace(
                    moduleRoot, scenario.fixtureDirName(), "stress-" + scenario.id());
            List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workspace, scenario);
            assertEquals(6, stages.size(), scenario.id() + " stage count");
            for (DeliveryStageResult stage : stages) {
                assertTrue(stage.getResult().isSuccess(),
                        scenario.id() + " " + stage.getStage() + ": " + stage.getResult().getMessage());
                assertTrue(stage.getResult().getCheckpointId().isPresent());
            }
            assertEquals("DISCOVERY", stages.get(0).getStage());
            assertEquals("DELIVERY", stages.get(5).getStage());
            outcomes.add(new BoundaryStressMain.StressDeliveryOutcome(scenario, workspace, stages, true));
        }

        // Discovery divergence: REST uses pwd, others git status — still same Runtime path.
        assertEquals("git status", scenarios.get(0).discoveryCommand());
        assertEquals("pwd", scenarios.get(1).discoveryCommand());

        File docsDir = new File(moduleRoot.getParentFile(), "docs");
        String report = BoundaryStressReportFormatter.format(outcomes);
        File out = new File(docsDir, "runtime-boundary-stress-report.md");
        Files.write(out.toPath(), report.getBytes(Charset.forName("UTF-8")));
        assertTrue(out.isFile());
        assertTrue(report.contains("## 1 Runtime Stable Boundary"));
        assertTrue(report.contains("Overall: PASS"));
    }
}
