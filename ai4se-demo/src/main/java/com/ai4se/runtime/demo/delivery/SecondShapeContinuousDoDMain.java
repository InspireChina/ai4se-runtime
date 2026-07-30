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
 * Second-shape continuous DoD — REST API workspace (≠ timeout/config).
 * Playbook S9-shaped evidence. No new Gate / Claude / Runtime expand.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.SecondShapeContinuousDoDMain
 * </pre>
 */
public final class SecondShapeContinuousDoDMain {

    private SecondShapeContinuousDoDMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspaceSource = new File(module, "stress-workspaces/02-rest-api");
        Path outRoot = new File(module, "target/second-shape-continuous").toPath();
        Path analysisOut = outRoot.resolve("analysis-bundle");
        Path patchesDir = new File(module, "second-shape-continuous-input/patches").toPath();
        Path reportFile = outRoot.resolve("evidence-delta.md");
        Files.createDirectories(outRoot);

        StringBuilder evidence = new StringBuilder();
        evidence.append("# Evidence Delta — Second-shape continuous DoD\n\n");
        evidence.append("playbook: docs/build-pathway-playbook.md §2.1.1 / S9-shaped\n");
        evidence.append("question: Does the consumer chain hold on a second requirement shape (REST ≠ timeout)?\n");
        evidence.append("workspace: `stress-workspaces/02-rest-api`\n\n");

        int fails = 0;

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                workspaceSource.toPath(),
                RequirementAnalysisPipeline.REST_API_REQUIREMENT,
                analysisOut,
                null,
                false,
                "second-shape-continuous");

        String planBody = new String(Files.readAllBytes(analysisOut.resolve("plan.md")),
                Charset.forName("UTF-8"));
        List<PlanAcceptance.Item> planItems = PlanAcceptance.parseItems(planBody);
        List<String> declared = PlanDeclaredTargets.parse(planBody);
        Map<String, String> patches = ProductionInputLoader.readPatches(patchesDir);
        PatchCoverage coverage = PatchCoverage.compute(declared, patches);

        boolean shapeOk = analysis.gap.mayPlan() && analysis.planWritten
                && planBody.contains("UserApi")
                && planBody.contains("GET /api/users")
                && !planBody.contains("OrderApi.timeoutMs")
                && !planBody.contains("ConfigService.timeoutMs");
        evidence.append("## Front-half\n\n");
        evidence.append("- gap: ").append(analysis.gap.getStatus()).append('\n');
        evidence.append("- REST plan (not timeout/config): ").append(shapeOk).append('\n');
        evidence.append("- status: **").append(shapeOk ? "PASS" : "FAIL").append("**\n\n");
        if (!shapeOk) {
            fails++;
        }

        boolean boundOk = coverage.undeclaredCount == 0 && coverage.patchedCount > 0;
        evidence.append("## Patches ⊆ declared\n\n");
        evidence.append("- coverage: ").append(coverage.ratio())
                .append(" undeclared=").append(coverage.undeclaredCount).append('\n');
        evidence.append("- status: **").append(boundOk ? "PASS" : "FAIL").append("**\n\n");
        if (!boundOk) {
            fails++;
        }

        File probe = WorkspaceBootstrap.prepareRunWorkspace(
                module, "stress-workspaces/02-rest-api", "second-shape-continuous-probe");
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path t = probe.toPath().resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
        VerificationMatrix matrix = VerificationMatrix.buildFromPlan(planItems, probe.toPath());

        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "stress-workspaces/02-rest-api", "second-shape-continuous-work");
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", planBody.endsWith("\n") ? planBody : planBody + "\n");
        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, new InputDeliveryScenario(
                "second-shape-continuous",
                "rest-api",
                "second-shape-continuous",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches), matrix);

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
        AcceptanceProvenanceGate.Result gate = AcceptanceProvenanceGate.check(
                PlanAcceptance.idsFromItems(planItems),
                matrix.acceptanceIds(),
                AcceptanceProvenanceGate.extractProvenanceIds(review),
                AcceptanceProvenanceGate.extractProvenanceIds(delivery));
        boolean deliveryPass = delivery.contains("Delivery Result: PASS");
        boolean chainOk = gate.pass && deliveryPass && matrix.complete;
        evidence.append("## Provenance + Delivery\n\n");
        evidence.append("- ").append(gate.message).append('\n');
        evidence.append("- Delivery PASS: ").append(deliveryPass).append('\n');
        evidence.append("- status: **").append(chainOk ? "PASS" : "FAIL").append("**\n\n");
        if (!chainOk) {
            fails++;
        }

        evidence.append("## Evidence Delta (new only)\n\n");
        evidence.append("| New evidence | Result |\n|--------------|--------|\n");
        evidence.append("| Continuous chain on REST workspace (≠ timeout/config) | ")
                .append(fails == 0 ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Plan/Acceptance are UserApi-shaped | ")
                .append(shapeOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("\nNot claimed: third language, Claude, new Gate, S9 production multi-repo.\n");
        evidence.append("Not re-scored: first-delivery timeout continuous DoD.\n\n");
        evidence.append("## Verdict\n\n");
        if (fails == 0) {
            evidence.append("**PASS** — consumer chain holds on a second requirement shape.\n");
        } else {
            evidence.append("**FAIL** — ").append(fails).append(" check group(s) failed.\n");
        }

        Files.write(reportFile, evidence.toString().getBytes(Charset.forName("UTF-8")));
        Path docs = new File(module, "../docs/second-shape-continuous-evidence.md").toPath().normalize();
        if (docs.getParent() != null && Files.isDirectory(docs.getParent())) {
            Files.write(docs, evidence.toString().getBytes(Charset.forName("UTF-8")));
        }
        System.out.println(evidence.toString());
        System.out.println("report = " + reportFile.toAbsolutePath());
        if (fails > 0) {
            System.err.println("Second-shape continuous DoD FAILED");
            System.exit(1);
        }
        System.out.println("Second-shape continuous DoD OK");
    }

    private static String read(File workDir, String name) throws Exception {
        Path p = workDir.toPath().resolve(name);
        if (!Files.isRegularFile(p)) {
            return "";
        }
        return new String(Files.readAllBytes(p), Charset.forName("UTF-8"));
    }
}
