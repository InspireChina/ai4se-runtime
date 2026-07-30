package com.ai4se.runtime.demo.delivery.stress;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import com.ai4se.runtime.demo.delivery.DeliveryStageResult;
import com.ai4se.runtime.demo.delivery.SerialDeliveryRunner;
import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import com.ai4se.runtime.engine.api.RuntimeResult;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sprint-9 Boundary Stress — three different real deliveries, no new Runtime.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.stress.BoundaryStressMain
 * </pre>
 */
public final class BoundaryStressMain {

    private BoundaryStressMain() {
    }

    public static void main(String[] args) throws Exception {
        File moduleRoot = WorkspaceBootstrap.resolveDemoModuleRoot();
        List<DeliveryScenario> scenarios = Arrays.<DeliveryScenario>asList(
                new ConfigChangeScenario(),
                new RestApiScenario(),
                new CrossModuleScenario());

        List<StressDeliveryOutcome> outcomes = new ArrayList<StressDeliveryOutcome>();
        boolean allOk = true;

        System.out.println("=== Sprint-9 Runtime Boundary Stress Test ===");
        for (DeliveryScenario scenario : scenarios) {
            File workspace = WorkspaceBootstrap.prepareRunWorkspace(
                    moduleRoot,
                    scenario.fixtureDirName(),
                    "stress-" + scenario.id());
            System.out.println();
            System.out.println("### " + scenario.id() + " [" + scenario.typeLabel() + "]");
            System.out.println("requirement = " + scenario.requirement());
            System.out.println("workspace   = " + workspace.getAbsolutePath());

            List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workspace, scenario);
            boolean ok = stages.size() == 6;
            for (DeliveryStageResult stage : stages) {
                RuntimeResult r = stage.getResult();
                System.out.println("  " + stage.getStage()
                        + " success=" + r.isSuccess()
                        + " worker=" + r.getWorkerId()
                        + " goal=" + r.getGoalType()
                        + " cp=" + (r.getCheckpointId().isPresent() ? r.getCheckpointId().get().value() : "-")
                        + " ms=" + r.getDurationMs());
                if (!r.isSuccess()) {
                    ok = false;
                    System.out.println("    failure=" + r.getMessage());
                }
            }
            outcomes.add(new StressDeliveryOutcome(scenario, workspace, stages, ok));
            allOk &= ok;
        }

        File docsDir = new File(moduleRoot.getParentFile(), "docs");
        String report = BoundaryStressReportFormatter.format(outcomes);
        File out = new File(docsDir, "runtime-boundary-stress-report.md");
        if (!docsDir.exists()) {
            docsDir.mkdirs();
        }
        Files.write(out.toPath(), report.getBytes(Charset.forName("UTF-8")));
        System.out.println();
        System.out.println("report = " + out.getAbsolutePath());
        if (!allOk) {
            System.err.println("Sprint-9 Boundary Stress FAILED");
            System.exit(1);
        }
        System.out.println("Sprint-9 Boundary Stress OK — 3 deliveries passed; architecture unchanged.");
    }

    static final class StressDeliveryOutcome {
        final DeliveryScenario scenario;
        final File workspace;
        final List<DeliveryStageResult> stages;
        final boolean success;

        StressDeliveryOutcome(
                DeliveryScenario scenario,
                File workspace,
                List<DeliveryStageResult> stages,
                boolean success) {
            this.scenario = scenario;
            this.workspace = workspace;
            this.stages = stages;
            this.success = success;
        }
    }
}
