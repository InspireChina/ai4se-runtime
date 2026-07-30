package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.demo.analysis.PlanDeclaredTargets;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.demo.input.InputDeliveryScenario;
import com.ai4se.runtime.demo.input.ProductionInputLoader;
import com.ai4se.runtime.engine.api.RuntimeResult;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Playbook §2.1.1 Sprint A — continuous Analysis → Planning → Execution → Verification.
 * Human patches allowed; must be ⊆ Plan declared targets. No Claude. No Runtime expansion.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.ExecutionPathSprintAMain
 * </pre>
 */
public final class ExecutionPathSprintAMain {

    private ExecutionPathSprintAMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspaceSource = new File(module, "pilot-workspace");
        Path analysisOut = new File(module, "target/execution-path-sprint-a/analysis-bundle").toPath();
        Path patchesDir = new File(module, "execution-path-sprint-a-input/patches").toPath();
        Path reportFile = new File(module, "target/execution-path-sprint-a/evidence-delta.md").toPath();
        Files.createDirectories(reportFile.getParent());

        StringBuilder evidence = new StringBuilder();
        evidence.append("# Evidence Delta — Execution Path Sprint A\n\n");
        evidence.append("playbook: docs/build-pathway-playbook.md §2.1.1\n");
        evidence.append("question: Plan declared targets → Execution ⊆ targets → mvn test?\n\n");

        int fails = 0;

        // --- Analysis + Clarification + Planning (continuous; do not skip) ---
        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                workspaceSource.toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                analysisOut,
                null,
                false,
                "execution-path-sprint-a");

        evidence.append("## Continuous front-half\n\n");
        boolean mayPlan = analysis.gap.mayPlan() && analysis.planWritten;
        boolean noPatchesInAnalysis = !Files.exists(analysisOut.resolve("patches"));
        Path planPath = analysisOut.resolve("plan.md");
        String planBody = Files.exists(planPath)
                ? new String(Files.readAllBytes(planPath), Charset.forName("UTF-8"))
                : "";
        List<String> declared = PlanDeclaredTargets.parse(planBody);
        boolean hasDeclared = !declared.isEmpty();
        evidence.append("- gap_status: ").append(analysis.gap.getStatus()).append('\n');
        evidence.append("- plan written: ").append(analysis.planWritten).append('\n');
        evidence.append("- analysis has no patches/: ").append(noPatchesInAnalysis).append('\n');
        evidence.append("- declared targets count: ").append(declared.size()).append('\n');
        for (String t : declared) {
            evidence.append("  - `").append(t).append("`\n");
        }
        boolean frontOk = mayPlan && noPatchesInAnalysis && hasDeclared;
        evidence.append("- status: **").append(frontOk ? "PASS" : "FAIL").append("**\n\n");
        if (!frontOk) {
            fails++;
        }

        // --- Patch ⊆ declared gate ---
        Map<String, String> patches = ProductionInputLoader.readPatches(patchesDir);
        List<String> undeclared = PlanDeclaredTargets.findUndeclaredPatches(declared, patches);
        evidence.append("## Execution bound to Plan\n\n");
        evidence.append("- human patch files: ").append(patches.size()).append('\n');
        evidence.append("- undeclared patches: ").append(undeclared.isEmpty() ? "(none)" : undeclared.toString())
                .append('\n');
        boolean boundOk = undeclared.isEmpty() && !patches.isEmpty();
        evidence.append("- status: **").append(boundOk ? "PASS" : "FAIL").append("**\n\n");
        if (!boundOk) {
            fails++;
        }

        // --- Execution + Verification on compilable workspace copy ---
        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "execution-path-sprint-a-work");
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", planBody.endsWith("\n") ? planBody : planBody + "\n");
        InputDeliveryScenario scenario = new InputDeliveryScenario(
                "execution-path-sprint-a",
                "timeout-config",
                "execution-path-sprint-a",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches);

        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, scenario);
        evidence.append("## Runtime stages\n\n");
        boolean execOk = false;
        boolean verifyOk = false;
        boolean allOk = stages.size() == 6;
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            evidence.append("- ").append(stage.getStage()).append(": success=")
                    .append(r.isSuccess()).append(" worker=").append(r.getWorkerId())
                    .append(" durationMs=").append(r.getDurationMs()).append('\n');
            if ("EXECUTION".equals(stage.getStage())) {
                execOk = r.isSuccess();
            }
            if ("VERIFICATION".equals(stage.getStage())) {
                verifyOk = r.isSuccess();
            }
            if (!r.isSuccess()) {
                allOk = false;
                evidence.append("  failure=").append(r.getMessage()).append('\n');
            }
        }
        boolean stageOk = execOk && verifyOk && allOk;
        evidence.append("- status: **").append(stageOk ? "PASS" : "FAIL").append("**\n\n");
        if (!stageOk) {
            fails++;
        }

        evidence.append("## Evidence Delta (new this round only)\n\n");
        evidence.append("| New evidence | Result |\n");
        evidence.append("|--------------|--------|\n");
        evidence.append("| Plan emits Declared modification targets | ")
                .append(hasDeclared ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Human patches ⊆ declared targets | ")
                .append(boundOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Execution applies patches on compilable pilot-workspace | ")
                .append(execOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Verification `mvn -f pom.xml -q test` | ")
                .append(verifyOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("\nNot claimed this round: Review depth, Delivery approval, Promotion domain, Claude.\n");
        evidence.append("Not re-proven as main score: Facts/Gap/Boundary (Phase 1).\n\n");
        evidence.append("## Sprint A verdict\n\n");
        if (fails == 0) {
            evidence.append("**PASS** — Planning → Execution (bound) → Verification holds on compilable fixture.\n");
        } else {
            evidence.append("**FAIL** — ").append(fails).append(" check group(s) failed.\n");
        }

        Files.write(reportFile, evidence.toString().getBytes(Charset.forName("UTF-8")));
        // Also refresh docs copy for Reviewer (canonical narrative lives under docs/).
        Path docsReport = new File(module, "../docs/execution-path-sprint-a-evidence.md").toPath().normalize();
        if (docsReport.getParent() != null && Files.isDirectory(docsReport.getParent())) {
            Files.write(docsReport, evidence.toString().getBytes(Charset.forName("UTF-8")));
        }

        System.out.println(evidence.toString());
        System.out.println("report = " + reportFile.toAbsolutePath());
        if (fails > 0) {
            System.err.println("Execution Path Sprint A FAILED");
            System.exit(1);
        }
        System.out.println("Execution Path Sprint A OK");
    }
}
