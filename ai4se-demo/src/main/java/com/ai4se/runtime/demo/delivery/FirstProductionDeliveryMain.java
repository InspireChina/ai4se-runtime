package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.engine.api.RuntimeResult;
import java.io.File;
import java.util.List;

/**
 * First Production Delivery Demo.
 * <pre>
 * mvn -pl ai4se-demo -am install -DskipTests
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.FirstProductionDeliveryMain
 * </pre>
 */
public final class FirstProductionDeliveryMain {

    private FirstProductionDeliveryMain() {
    }

    public static void main(String[] args) throws Exception {
        File moduleRoot = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = WorkspaceBootstrap.prepareRunWorkspace(moduleRoot);
        System.out.println("=== First Production Delivery ===");
        System.out.println("requirement = " + FirstProductionDeliveryPipeline.REQUIREMENT);
        System.out.println("workspace   = " + workspace.getAbsolutePath());

        List<DeliveryStageResult> stages = FirstProductionDeliveryPipeline.run(workspace);
        boolean ok = true;
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            System.out.println("--- " + stage.getStage() + " ---");
            System.out.println("  success=" + r.isSuccess()
                    + " status=" + r.getTaskStatus()
                    + " worker=" + r.getWorkerId()
                    + " durationMs=" + r.getDurationMs()
                    + " checkpoint="
                    + (r.getCheckpointId().isPresent() ? r.getCheckpointId().get().value() : "-"));
            if (!r.isSuccess()) {
                System.out.println("  failure=" + r.getMessage());
                ok = false;
            }
        }

        File reportDir = new File(moduleRoot, "target");
        FirstProductionDeliveryPipeline.writeCanonicalReport(reportDir, stages, workspace);
        System.out.println("report = " + new File(reportDir, "first-production-delivery-report.md").getAbsolutePath());

        if (!ok || stages.size() < 6) {
            System.err.println("First Production Delivery FAILED");
            System.exit(1);
        }
        System.out.println("First Production Delivery OK");
    }
}
