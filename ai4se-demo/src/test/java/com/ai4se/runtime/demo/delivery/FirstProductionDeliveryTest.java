package com.ai4se.runtime.demo.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FirstProductionDeliveryTest {

    @Test
    @Timeout(600)
    void firstProductionDelivery_runsFullPipeline() throws Exception {
        File moduleRoot = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = WorkspaceBootstrap.prepareRunWorkspace(moduleRoot);

        List<DeliveryStageResult> stages = FirstProductionDeliveryPipeline.run(workspace);

        assertEquals(6, stages.size(), "expected Discovery→Plan→Execution→Verification→Review→Delivery");
        for (DeliveryStageResult stage : stages) {
            assertTrue(stage.getResult().isSuccess(),
                    stage.getStage() + " failed: " + stage.getResult().getMessage());
            assertTrue(stage.getResult().getCheckpointId().isPresent(),
                    stage.getStage() + " missing checkpointId on RuntimeResult");
            assertTrue(stage.getResult().getDurationMs() >= 0L);
        }
        assertEquals("DISCOVERY", stages.get(0).getStage());
        assertEquals("VERIFICATION", stages.get(3).getStage());
        assertEquals("DELIVERY", stages.get(5).getStage());

        assertTrue(new File(workspace, "src/main/java/com/example/delivery/TimeoutSource.java").isFile());
        String config = new String(Files.readAllBytes(
                new File(workspace, "src/main/java/com/example/delivery/ConfigService.java").toPath()),
                Charset.forName("UTF-8"));
        assertTrue(config.contains("app.timeout.ms"));
        assertTrue(new File(workspace, "REVIEW_REPORT.md").isFile());
        assertTrue(new File(workspace, "DELIVERY_REPORT.md").isFile());

        File reportDir = new File(moduleRoot, "target");
        FirstProductionDeliveryPipeline.writeCanonicalReport(reportDir, stages, workspace);
        File report = new File(reportDir, "first-production-delivery-report.md");
        assertTrue(report.isFile());
        String body = new String(Files.readAllBytes(report.toPath()), Charset.forName("UTF-8"));
        assertTrue(body.contains("【A】本次 Requirement"));
        assertTrue(body.contains("# ChatGPT Review Package"));
        assertTrue(body.contains("Overall: PASS"));
    }
}
