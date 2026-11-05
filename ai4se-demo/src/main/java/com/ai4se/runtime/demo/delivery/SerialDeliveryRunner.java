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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Demo-only serial delivery (NOT Runtime StageRunner).
 * Reuses existing Runtime.submit + Workers; seeds StageGate kinds via {@link DemoGateBundle}.
 */
public final class SerialDeliveryRunner {

    private SerialDeliveryRunner() {
    }

    public static List<DeliveryStageResult> run(File workspace, DeliveryScenario scenario) {
        return run(workspace, scenario, null);
    }

    /**
     * @param matrix if non-null, Review/Delivery consume Verification Matrix (Sprint C).
     *               if null, legacy mvn-centric Review/Delivery (First Production / stress).
     */
    public static List<DeliveryStageResult> run(
            File workspace,
            DeliveryScenario scenario,
            VerificationMatrix matrix) {
        DemoGateBundle gate = new DemoGateBundle();
        List<String> acceptanceIds = matrix != null && !matrix.acceptanceIds().isEmpty()
                ? matrix.acceptanceIds()
                : DemoGateBundle.defaultAcceptanceIds();

        List<DeliveryStageResult> stages = new ArrayList<DeliveryStageResult>();
        String root = workspace.getAbsolutePath();
        String projectId = scenario.projectId();
        if (projectId == null || projectId.trim().isEmpty()) {
            projectId = "delivery-" + scenario.id();
        }

        // Probe only — not Engine DISCOVERY (MapSearch); see DemoGateBundle.PROBE_GOAL_TYPE.
        stages.add(submit(gate, projectId, "DISCOVERY", "Discover workspace state.",
                new ShellWorker(), root, shellParams(scenario.discoveryCommand()),
                DemoGateBundle.PROBE_GOAL_TYPE, Duration.ofMinutes(1),
                scenario.verifyCommand(), acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        stages.add(submit(gate, projectId, "PLAN", "Write PLAN.md.",
                new FileEditWorker(), root, fileParams(scenario.planFiles()), "PLAN",
                Duration.ofMinutes(1), scenario.verifyCommand(), acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        stages.add(submit(gate, projectId, "EXECUTION", "Apply requirement patches.",
                new FileEditWorker(), root, fileParams(scenario.executionFiles()), "EXECUTION",
                Duration.ofMinutes(1), scenario.verifyCommand(), acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        stages.add(submit(gate, projectId, "VERIFICATION", "Run verification command.",
                DemoGateBundle.citing(new ShellWorker(), acceptanceIds), root,
                shellParams(scenario.verifyCommand()), "VERIFICATION",
                Duration.ofMinutes(10), scenario.verifyCommand(), acceptanceIds));
        boolean verifyOk = lastOk(stages);
        if (!verifyOk) {
            if (matrix == null) {
                return stages;
            }
            // §2.2: shell FAIL must still produce consumable Delivery + local return (not drop reports).
            return finishMatrixConsumerChain(
                    gate, projectId, workspace, root, scenario, matrix, stages, acceptanceIds);
        }

        return finishMatrixConsumerChain(
                gate, projectId, workspace, root, scenario, matrix, stages, acceptanceIds);
    }

    private static List<DeliveryStageResult> finishMatrixConsumerChain(
            DemoGateBundle gate,
            String projectId,
            File workspace,
            String root,
            DeliveryScenario scenario,
            VerificationMatrix matrix,
            List<DeliveryStageResult> stages,
            List<String> acceptanceIds) {
        ReviewVerdict verdict = null;
        String reviewBody;
        if (matrix != null) {
            Map<String, String> matrixFiles = new LinkedHashMap<String, String>();
            matrixFiles.put("verification-matrix.md", matrix.toMarkdown());
            stages.add(submit(gate, projectId, "MATRIX", "Write verification-matrix.md.",
                    new FileEditWorker(), root, fileParams(matrixFiles), "MATRIX",
                    Duration.ofMinutes(1), scenario.verifyCommand(), acceptanceIds));
            if (!lastOk(stages)) {
                return stages;
            }
            verdict = ReviewReportFormatter.formatConsumingMatrix(
                    scenario.requirement(), stages, workspace, matrix);
            reviewBody = verdict.reviewMarkdown;
        } else {
            reviewBody = ReviewReportFormatter.format(scenario.requirement(), stages, workspace);
        }

        Map<String, String> reviewFiles = new LinkedHashMap<String, String>();
        reviewFiles.put("REVIEW_REPORT.md", reviewBody);
        stages.add(submit(gate, projectId, "REVIEW", "Write review report.",
                new FileEditWorker(), root, fileParams(reviewFiles), "REVIEW", Duration.ofMinutes(1),
                scenario.verifyCommand(), acceptanceIds));
        if (!lastOk(stages)) {
            return stages;
        }

        String deliveryBody = matrix != null
                ? DeliveryReportFormatter.formatConsumingReview(
                        scenario.requirement(), stages, workspace, verdict)
                : DeliveryReportFormatter.format(scenario.requirement(), stages, workspace);
        Map<String, String> deliveryFiles = new LinkedHashMap<String, String>();
        deliveryFiles.put("DELIVERY_REPORT.md", deliveryBody);
        stages.add(submit(gate, projectId, "DELIVERY", "Write delivery report.",
                new FileEditWorker(), root, fileParams(deliveryFiles), "DELIVERY", Duration.ofMinutes(1),
                scenario.verifyCommand(), acceptanceIds));
        return stages;
    }

    private static boolean lastOk(List<DeliveryStageResult> stages) {
        return !stages.isEmpty() && stages.get(stages.size() - 1).getResult().isSuccess();
    }

    private static DeliveryStageResult submit(
            DemoGateBundle gate,
            String projectId,
            String stage,
            String note,
            Worker worker,
            String workspaceRoot,
            Map<String, Object> goalParams,
            String goalType,
            Duration wallClock,
            String verifyCommand,
            List<String> acceptanceIds) {
        List<ArtifactId> inputs = gate.inputsFor(goalType, verifyCommand, acceptanceIds);
        Runtime runtime = gate.runtime(worker);
        RuntimeResult result = runtime.submit(
                request(projectId, workspaceRoot, goalParams, goalType, wallClock, inputs));
        return new DeliveryStageResult(stage, note, result);
    }

    private static RuntimeRequest request(
            String projectId,
            String workspaceRoot,
            Map<String, Object> goalParams,
            String goalType,
            Duration wallClock,
            List<ArtifactId> inputArtifactIds) {
        return RuntimeRequest.builder()
                .projectId(projectId)
                .profileId("stress.delivery")
                .profileRevision("1")
                .workflowId("boundary.stress.delivery")
                .goal(new GoalSpec(
                        goalType,
                        "acc-boundary-stress",
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
