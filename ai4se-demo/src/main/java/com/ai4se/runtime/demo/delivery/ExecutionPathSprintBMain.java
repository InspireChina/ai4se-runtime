package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.demo.analysis.PlanAcceptance;
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
 * Playbook §2.1.1 Sprint B — Acceptance → Verification mapping (not mere mvn exit 0).
 * Permanent artifact: verification-matrix.md. No JaCoCo / mutation / E2E. No Runtime expand.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.ExecutionPathSprintBMain
 * </pre>
 */
public final class ExecutionPathSprintBMain {

    private ExecutionPathSprintBMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspaceSource = new File(module, "pilot-workspace");
        Path analysisOut = new File(module, "target/execution-path-sprint-b/analysis-bundle").toPath();
        Path patchesDir = new File(module, "execution-path-sprint-a-input/patches").toPath();
        Path mappingFile = new File(module, "execution-path-sprint-a-input/verification-mapping.txt").toPath();
        Path reportFile = new File(module, "target/execution-path-sprint-b/evidence-delta.md").toPath();
        Files.createDirectories(reportFile.getParent());

        StringBuilder evidence = new StringBuilder();
        evidence.append("# Evidence Delta — Execution Path Sprint B\n\n");
        evidence.append("playbook: docs/build-pathway-playbook.md §2.1.1\n");
        evidence.append("question: Does Verification cover all Plan Acceptance IDs?\n\n");

        int fails = 0;

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                workspaceSource.toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                analysisOut,
                null,
                false,
                "execution-path-sprint-b");

        Path planPath = analysisOut.resolve("plan.md");
        String planBody = new String(Files.readAllBytes(planPath), Charset.forName("UTF-8"));
        Map<String, String> acceptance = PlanAcceptance.parse(planBody);
        List<String> declared = PlanDeclaredTargets.parse(planBody);
        Map<String, String> patches = ProductionInputLoader.readPatches(patchesDir);
        PatchCoverage patchCoverage = PatchCoverage.compute(declared, patches);

        evidence.append("## Plan Acceptance\n\n");
        evidence.append("- acceptance IDs: ").append(acceptance.keySet()).append('\n');
        boolean hasAcceptance = !acceptance.isEmpty();
        evidence.append("- status: **").append(hasAcceptance ? "PASS" : "FAIL").append("**\n\n");
        if (!hasAcceptance) {
            fails++;
        }

        evidence.append("## Patch Coverage (Sprint A metric retained)\n\n");
        evidence.append("- declared: ").append(patchCoverage.declaredCount).append('\n');
        evidence.append("- patched: ").append(patchCoverage.patchedCount).append('\n');
        evidence.append("- coverage: ").append(patchCoverage.ratio()).append('\n');
        evidence.append("- undeclared: ").append(patchCoverage.undeclaredCount).append('\n');
        boolean patchOk = patchCoverage.undeclaredCount == 0 && patchCoverage.patchedCount > 0;
        evidence.append("- status: **").append(patchOk ? "PASS" : "FAIL").append("**\n\n");
        if (!patchOk) {
            fails++;
        }

        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", planBody.endsWith("\n") ? planBody : planBody + "\n");
        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "execution-path-sprint-b-work");
        InputDeliveryScenario scenario = new InputDeliveryScenario(
                "execution-path-sprint-b",
                "timeout-acceptance-matrix",
                "execution-path-sprint-b",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches);

        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, scenario);
        boolean verifyShellOk = false;
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            if ("VERIFICATION".equals(stage.getStage())) {
                verifyShellOk = r.isSuccess();
            }
            if (!r.isSuccess()) {
                evidence.append("- stage FAIL: ").append(stage.getStage())
                        .append(" ").append(r.getMessage()).append('\n');
            }
        }
        evidence.append("## Shell Verification (necessary, not sufficient)\n\n");
        evidence.append("- mvn test success: ").append(verifyShellOk).append('\n');
        evidence.append("- status: **").append(verifyShellOk ? "PASS" : "FAIL").append("**\n\n");
        if (!verifyShellOk) {
            fails++;
        }

        Map<String, String> mapping = VerificationMapping.load(mappingFile);
        VerificationMatrix matrix = VerificationMatrix.build(acceptance, mapping, workDir.toPath());
        Path matrixInWork = workDir.toPath().resolve("verification-matrix.md");
        Files.write(matrixInWork, matrix.toMarkdown().getBytes(Charset.forName("UTF-8")));
        Path matrixInBundle = analysisOut.resolve("verification-matrix.md");
        Files.write(matrixInBundle, matrix.toMarkdown().getBytes(Charset.forName("UTF-8")));

        evidence.append("## Acceptance → Verification Matrix\n\n");
        evidence.append(matrix.toMarkdown()).append('\n');
        boolean matrixOk = matrix.complete;
        evidence.append("- matrix file: `verification-matrix.md`\n");
        evidence.append("- status: **").append(matrixOk ? "PASS" : "FAIL").append("**\n\n");
        if (!matrixOk) {
            fails++;
        }

        // Prove MISSING is detectable (fixture mapping without A99 must stay absent from plan).
        Map<String, String> bogusAcceptance = new LinkedHashMap<String, String>(acceptance);
        bogusAcceptance.put("A99", "intentional unmapped acceptance for gate self-check");
        VerificationMatrix missingProbe = VerificationMatrix.build(bogusAcceptance, mapping, workDir.toPath());
        boolean missingDetected = !missingProbe.complete && missingProbe.missing >= 1;
        evidence.append("## Gate self-check (MISSING must not default PASS)\n\n");
        evidence.append("- injected A99 without mapping → complete=").append(missingProbe.complete).append('\n');
        evidence.append("- status: **").append(missingDetected ? "PASS" : "FAIL").append("**\n\n");
        if (!missingDetected) {
            fails++;
        }

        evidence.append("## Evidence Delta (new this round only)\n\n");
        evidence.append("| New evidence | Result |\n");
        evidence.append("|--------------|--------|\n");
        evidence.append("| Plan emits Acceptance IDs (A1…) | ")
                .append(hasAcceptance ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| verification-mapping + verification-matrix.md | ")
                .append(matrixOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Unmapped Acceptance → MISSING (not silent PASS) | ")
                .append(missingDetected ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Patch Coverage metric (declared/patched/undeclared) | ")
                .append(patchOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("\nNot claimed: JaCoCo, mutation, E2E, Promotion domain, Claude, Review depth.\n");
        evidence.append("Not re-scored as main: Sprint A Execution bound / Phase 1 Analysis.\n\n");
        evidence.append("## Sprint B verdict\n\n");
        if (fails == 0) {
            evidence.append("**PASS** — Verification covers Plan Acceptance via explicit matrix; ")
                    .append("mvn test alone is no longer the definition of Verification.\n");
        } else {
            evidence.append("**FAIL** — ").append(fails).append(" check group(s) failed.\n");
        }

        Files.write(reportFile, evidence.toString().getBytes(Charset.forName("UTF-8")));
        Path docsReport = new File(module, "../docs/execution-path-sprint-b-evidence.md").toPath().normalize();
        if (docsReport.getParent() != null && Files.isDirectory(docsReport.getParent())) {
            Files.write(docsReport, evidence.toString().getBytes(Charset.forName("UTF-8")));
        }

        System.out.println(evidence.toString());
        System.out.println("report = " + reportFile.toAbsolutePath());
        System.out.println("matrix = " + matrixInWork.toAbsolutePath());
        if (fails > 0) {
            System.err.println("Execution Path Sprint B FAILED");
            System.exit(1);
        }
        System.out.println("Execution Path Sprint B OK");
    }
}
