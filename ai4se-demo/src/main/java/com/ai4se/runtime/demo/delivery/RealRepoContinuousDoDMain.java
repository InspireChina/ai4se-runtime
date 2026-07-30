package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.demo.analysis.PlanAcceptance;
import com.ai4se.runtime.demo.analysis.PlanDeclaredTargets;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.demo.input.InputDeliveryScenario;
import com.ai4se.runtime.demo.input.ProductionInputLoader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real-repo continuous DoD — first-delivery-workspace (not pilot-order fixture).
 * Reuses existing consumer chain. No new Gate / Matrix V2 / Claude / Runtime expand.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.RealRepoContinuousDoDMain
 * </pre>
 */
public final class RealRepoContinuousDoDMain {

    private RealRepoContinuousDoDMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspaceSource = new File(module, "first-delivery-workspace");
        Path outRoot = new File(module, "target/real-repo-continuous").toPath();
        Path analysisOut = outRoot.resolve("analysis-bundle");
        Path patchesDir = new File(module, "real-repo-continuous-input/patches").toPath();
        Path reportFile = outRoot.resolve("evidence-delta.md");
        Files.createDirectories(outRoot);

        StringBuilder evidence = new StringBuilder();
        evidence.append("# Evidence Delta — Real-repo continuous DoD\n\n");
        evidence.append("playbook: docs/build-pathway-playbook.md §2.1.1\n");
        evidence.append("question: Does the consumer chain run on a non-pilot compilable workspace?\n");
        evidence.append("workspace: `first-delivery-workspace`\n\n");

        int fails = 0;

        Map<String, String> answers = new LinkedHashMap<String, String>();
        answers.put("timeout", "app.timeout.ms=5000");

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                workspaceSource.toPath(),
                RequirementAnalysisPipeline.FIRST_DELIVERY_REQUIREMENT,
                analysisOut,
                answers,
                false,
                "real-repo-continuous");

        String planBody = new String(Files.readAllBytes(analysisOut.resolve("plan.md")),
                Charset.forName("UTF-8"));
        List<PlanAcceptance.Item> planItems = PlanAcceptance.parseItems(planBody);
        List<String> declared = PlanDeclaredTargets.parse(planBody);
        Map<String, String> patches = ProductionInputLoader.readPatches(patchesDir);
        PatchCoverage coverage = PatchCoverage.compute(declared, patches);

        evidence.append("## Front-half (Analysis→Plan)\n\n");
        boolean frontOk = analysis.gap.mayPlan() && analysis.planWritten
                && planBody.contains("ConfigService")
                && !planBody.contains("OrderApi.timeoutMs")
                && !Files.exists(analysisOut.resolve("patches"));
        evidence.append("- gap: ").append(analysis.gap.getStatus()).append('\n');
        evidence.append("- plan targets ConfigService (not OrderApi pilot): ").append(frontOk).append('\n');
        evidence.append("- status: **").append(frontOk ? "PASS" : "FAIL").append("**\n\n");
        if (!frontOk) {
            fails++;
        }

        evidence.append("## Patches ⊆ declared\n\n");
        evidence.append("- coverage: ").append(coverage.ratio())
                .append(" undeclared=").append(coverage.undeclaredCount).append('\n');
        boolean boundOk = coverage.undeclaredCount == 0 && coverage.patchedCount > 0;
        evidence.append("- status: **").append(boundOk ? "PASS" : "FAIL").append("**\n\n");
        if (!boundOk) {
            fails++;
        }

        File probe = WorkspaceBootstrap.prepareRunWorkspace(
                module, "first-delivery-workspace", "real-repo-continuous-probe");
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path t = probe.toPath().resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
        VerificationMatrix matrix = VerificationMatrix.buildFromPlan(planItems, probe.toPath());

        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "first-delivery-workspace", "real-repo-continuous-work");
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", planBody.endsWith("\n") ? planBody : planBody + "\n");
        InputDeliveryScenario scenario = new InputDeliveryScenario(
                "real-repo-continuous",
                "first-delivery-continuous",
                "real-repo-continuous",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches);

        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, scenario, matrix);
        boolean verifyOk = false;
        boolean allOk = true;
        evidence.append("## Stages\n\n");
        for (DeliveryStageResult stage : stages) {
            evidence.append("- ").append(stage.getStage()).append(": ")
                    .append(stage.getResult().isSuccess()).append('\n');
            if ("VERIFICATION".equals(stage.getStage())) {
                verifyOk = stage.getResult().isSuccess();
            }
            if (!stage.getResult().isSuccess()) {
                allOk = false;
            }
        }
        evidence.append("- status: **").append(verifyOk && allOk ? "PASS" : "FAIL").append("**\n\n");
        if (!verifyOk || !allOk) {
            fails++;
        }

        String review = read(workDir, "REVIEW_REPORT.md");
        String delivery = read(workDir, "DELIVERY_REPORT.md");
        List<String> planIds = PlanAcceptance.idsFromItems(planItems);
        AcceptanceProvenanceGate.Result gate = AcceptanceProvenanceGate.check(
                planIds,
                matrix.acceptanceIds(),
                AcceptanceProvenanceGate.extractProvenanceIds(review),
                AcceptanceProvenanceGate.extractProvenanceIds(delivery));
        boolean deliveryPass = delivery.contains("Delivery Result: PASS");
        evidence.append("## Provenance + Delivery\n\n");
        evidence.append("- gate: ").append(gate.message).append('\n');
        evidence.append("- Delivery Result PASS: ").append(deliveryPass).append('\n');
        evidence.append("- matrix complete: ").append(matrix.complete).append('\n');
        boolean chainOk = gate.pass && deliveryPass && matrix.complete;
        evidence.append("- status: **").append(chainOk ? "PASS" : "FAIL").append("**\n\n");
        if (!chainOk) {
            fails++;
        }

        boolean notSampleSkip = !workspaceSource.getName().equals("sample-workspace");
        evidence.append("## Evidence Delta (new only)\n\n");
        evidence.append("| New evidence | Result |\n|--------------|--------|\n");
        evidence.append("| Continuous chain on `first-delivery-workspace` (≠ pilot-order) | ")
                .append(fails == 0 ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Plan/Acceptance match ConfigService surface | ")
                .append(frontOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Not sample-input skip-Analysis path | ")
                .append(notSampleSkip ? "PASS" : "FAIL").append(" |\n");
        evidence.append("\nNot claimed: second language/repo, Claude, new Gate.\n");
        evidence.append("Not re-scored: Phase 1/2 fixture proofs.\n\n");
        evidence.append("## Verdict\n\n");
        if (fails == 0) {
            evidence.append("**PASS** — consumer chain holds on a non-pilot compilable workspace.\n");
        } else {
            evidence.append("**FAIL** — ").append(fails).append(" check group(s) failed.\n");
        }

        Files.write(reportFile, evidence.toString().getBytes(Charset.forName("UTF-8")));
        Path docs = new File(module, "../docs/real-repo-continuous-evidence.md").toPath().normalize();
        if (docs.getParent() != null && Files.isDirectory(docs.getParent())) {
            Files.write(docs, evidence.toString().getBytes(Charset.forName("UTF-8")));
        }
        System.out.println(evidence.toString());
        System.out.println("report = " + reportFile.toAbsolutePath());
        if (fails > 0) {
            System.err.println("Real-repo continuous DoD FAILED");
            System.exit(1);
        }
        System.out.println("Real-repo continuous DoD OK");
    }

    private static String read(File workDir, String name) throws Exception {
        Path p = workDir.toPath().resolve(name);
        if (!Files.isRegularFile(p)) {
            return "";
        }
        return new String(Files.readAllBytes(p), Charset.forName("UTF-8"));
    }
}
