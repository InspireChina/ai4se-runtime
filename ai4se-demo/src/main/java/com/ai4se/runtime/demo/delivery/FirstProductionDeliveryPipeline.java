package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.engine.Runtime;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.workers.FileEditWorker;
import com.ai4se.runtime.workers.ShellWorker;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates Requirement → Discovery → Plan → Execution → Verification → Review → Delivery
 * as sequential Runtime.submit calls (no Workflow / Scheduler domain).
 * Seeds StageGate kinds via {@link DemoGateBundle} (same contract as SerialDeliveryRunner).
 */
public final class FirstProductionDeliveryPipeline {

    public static final String REQUIREMENT = ""
            + "Fix first-delivery-sample so ConfigService reads app.timeout.ms from "
            + "application.properties (expected 5000), expose TimeoutSource, and document "
            + "the config contract in README. Verify with Maven tests.";

    private static final String VERIFY_CMD = "mvn -f pom.xml -q test";

    private FirstProductionDeliveryPipeline() {
    }

    public static List<DeliveryStageResult> run(File workspace) {
        DemoGateBundle gate = new DemoGateBundle();
        List<String> acceptanceIds = DemoGateBundle.defaultAcceptanceIds();
        List<DeliveryStageResult> stages = new ArrayList<DeliveryStageResult>();
        String root = workspace.getAbsolutePath();

        stages.add(submit(gate, "DISCOVERY", "Discover workspace state (git status).",
                new ShellWorker(), root, shellParams("git status"),
                DemoGateBundle.PROBE_GOAL_TYPE, Duration.ofMinutes(1), acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        stages.add(submit(gate, "PLAN", "Write PLAN.md describing fix steps.",
                new FileEditWorker(), root, fileParams(DeliveryPatches.planFiles()), "PLAN",
                Duration.ofMinutes(1), acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        stages.add(submit(gate, "EXECUTION", "Apply TimeoutSource + ConfigService fix + README.",
                new FileEditWorker(), root, fileParams(DeliveryPatches.executionFiles()), "EXECUTION",
                Duration.ofMinutes(1), acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        stages.add(submit(gate, "VERIFICATION", "Run mvn -f pom.xml -q test in sample workspace.",
                DemoGateBundle.citing(new ShellWorker(), acceptanceIds), root,
                shellParams(VERIFY_CMD), "VERIFICATION",
                Duration.ofMinutes(10), acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        String reviewBody = ReviewReportFormatter.format(REQUIREMENT, stages, workspace);
        Map<String, String> reviewFiles = new LinkedHashMap<String, String>();
        reviewFiles.put("REVIEW_REPORT.md", reviewBody);
        stages.add(submit(gate, "REVIEW", "Write Delivery Review Report artifact.",
                new FileEditWorker(), root, fileParams(reviewFiles), "REVIEW", Duration.ofMinutes(1),
                acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        String deliveryBody = DeliveryReportFormatter.format(REQUIREMENT, stages, workspace);
        Map<String, String> deliveryFiles = new LinkedHashMap<String, String>();
        deliveryFiles.put("DELIVERY_REPORT.md", deliveryBody);
        stages.add(submit(gate, "DELIVERY", "Write Runtime Delivery Report artifact.",
                new FileEditWorker(), root, fileParams(deliveryFiles), "DELIVERY", Duration.ofMinutes(1),
                acceptanceIds));
        return stages;
    }

    public static void writeCanonicalReport(File docsDir, List<DeliveryStageResult> stages, File workspace)
            throws Exception {
        if (!docsDir.exists() && !docsDir.mkdirs()) {
            throw new IllegalStateException("cannot create docs dir: " + docsDir);
        }
        String body = FirstProductionDeliveryReportFormatter.format(REQUIREMENT, stages, workspace);
        File out = new File(docsDir, "first-production-delivery-report.md");
        Files.write(out.toPath(), body.getBytes(Charset.forName("UTF-8")));
    }

    private static boolean lastOk(List<DeliveryStageResult> stages) {
        return !stages.isEmpty() && stages.get(stages.size() - 1).getResult().isSuccess();
    }

    private static DeliveryStageResult submit(
            DemoGateBundle gate,
            String stage,
            String note,
            Worker worker,
            String workspaceRoot,
            Map<String, Object> goalParams,
            String goalType,
            Duration wallClock,
            List<String> acceptanceIds) {
        List<ArtifactId> inputs = gate.inputsFor(goalType, VERIFY_CMD, acceptanceIds);
        Runtime runtime = gate.runtime(worker);
        RuntimeResult result = runtime.submit(
                request(workspaceRoot, goalParams, goalType, wallClock, inputs));
        return new DeliveryStageResult(stage, note, result);
    }

    private static RuntimeRequest request(
            String workspaceRoot,
            Map<String, Object> goalParams,
            String goalType,
            Duration wallClock,
            List<ArtifactId> inputArtifactIds) {
        return RuntimeRequest.builder()
                .projectId("first-delivery")
                .profileId("first.delivery")
                .profileRevision("1")
                .workflowId("first.production.delivery")
                .goal(new GoalSpec(
                        goalType,
                        "acc-first-delivery",
                        inputArtifactIds,
                        goalParams))
                .workspaceRef(new WorkspaceRef(workspaceRoot, Optional.<String>empty()))
                .budget(new Budget(5, 1, 1000L, wallClock, 0L))
                .build();
    }

    private static Map<String, Object> shellParams(String command) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("command", command);
        params.put("operation", command);
        return params;
    }

    private static Map<String, Object> fileParams(Map<String, String> files) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("operation", "file.edit");
        params.put("files", files);
        return params;
    }
}
