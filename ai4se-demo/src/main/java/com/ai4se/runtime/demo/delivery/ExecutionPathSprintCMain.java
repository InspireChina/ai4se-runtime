package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.demo.analysis.PlanAcceptance;
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
 * Playbook §2.1.1 Sprint C — Review/Delivery consume Verification Matrix (consumer chain).
 * Does not expand Runtime / Claude / Graph. Does not re-prove Sprint A/B.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.ExecutionPathSprintCMain
 * </pre>
 */
public final class ExecutionPathSprintCMain {

    private ExecutionPathSprintCMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspaceSource = new File(module, "pilot-workspace");
        Path outRoot = new File(module, "target/execution-path-sprint-c").toPath();
        Path analysisOut = outRoot.resolve("analysis-bundle");
        Path patchesDir = new File(module, "execution-path-sprint-a-input/patches").toPath();
        Path mappingFile = new File(module, "execution-path-sprint-a-input/verification-mapping.txt").toPath();
        Path reportFile = outRoot.resolve("evidence-delta.md");
        Files.createDirectories(outRoot);

        StringBuilder evidence = new StringBuilder();
        evidence.append("# Evidence Delta — Execution Path Sprint C\n\n");
        evidence.append("playbook: docs/build-pathway-playbook.md §2.1.1\n");
        evidence.append("question: Do Review and Delivery strictly consume Verification Matrix?\n\n");

        int fails = 0;

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                workspaceSource.toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                analysisOut,
                null,
                false,
                "execution-path-sprint-c");

        String planBody = new String(Files.readAllBytes(analysisOut.resolve("plan.md")),
                Charset.forName("UTF-8"));
        Map<String, String> acceptance = PlanAcceptance.parse(planBody);
        Map<String, String> patches = ProductionInputLoader.readPatches(patchesDir);
        Map<String, String> mapping = VerificationMapping.load(mappingFile);

        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", planBody.endsWith("\n") ? planBody : planBody + "\n");
        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "execution-path-sprint-c-work");

        // Build matrix against post-execution workspace: run stages via consumer-chain path.
        // Pre-build matrix after a dry copy+patch is complex; build after EXECUTION by running
        // full chain: matrix built on workDir after we need files present — so build matrix
        // from mapping against workDir AFTER applying patches via runner... 
        // Runner writes matrix from provided object. Build matrix on a prepared work copy first.
        // Actually: apply path = run with matrix built from intended post-patch state.
        // Simplest: copy patches conceptually — VerificationMatrix checks test files in workDir
        // after EXECUTION. Runner receives matrix built before run against source+we need post-patch.
        // Fix: prepare workDir, manually we'd need patches applied. Runner applies patches.
        // So build matrix AFTER execution inside a two-phase approach OR build matrix expecting
        // patches already in workDir by pre-applying... 
        // Clean approach used in Sprint B: run SerialDeliveryRunner first without matrix, then
        // build matrix, then format review — but Sprint C wants runner to consume matrix.
        // Build matrix against patchesDir content indexed? checkEvidence looks at workspace.
        // Pre-seed workDir by running execution-only... Easiest: 
        // 1) run legacy to apply patches (wasteful) OR
        // 2) build matrix using a temp dir with patches copied onto workspace copy before run
        seedPatches(workDir.toPath(), patches);
        VerificationMatrix matrix = VerificationMatrix.build(acceptance, mapping, workDir.toPath());
        // Reset workDir to clean fixture then run full chain with matrix
        workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "execution-path-sprint-c-work");

        InputDeliveryScenario scenario = new InputDeliveryScenario(
                "execution-path-sprint-c",
                "consumer-chain",
                "execution-path-sprint-c",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches);

        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, scenario, matrix);

        evidence.append("## Runtime stages\n\n");
        boolean allRuntimeOk = true;
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            evidence.append("- ").append(stage.getStage()).append(": success=").append(r.isSuccess()).append('\n');
            if (!r.isSuccess()) {
                allRuntimeOk = false;
            }
        }
        evidence.append("- all Runtime stages success: ").append(allRuntimeOk).append("\n\n");

        String review = read(workDir, "REVIEW_REPORT.md");
        String delivery = read(workDir, "DELIVERY_REPORT.md");
        String matrixFile = read(workDir, "verification-matrix.md");

        boolean reviewConsumes = review.contains("Acceptance verdicts (from verification-matrix only)")
                && review.contains("A1 PASS")
                && review.contains("A2 PASS")
                && review.contains("A3 PASS")
                && review.contains("Delivery recommendation: PASS")
                && !review.contains("YES — requirement verified by Maven test");
        evidence.append("## Review consumes Matrix\n\n");
        evidence.append("- per-Acceptance PASS lines present: ").append(reviewConsumes).append('\n');
        evidence.append("- status: **").append(reviewConsumes ? "PASS" : "FAIL").append("**\n\n");
        if (!reviewConsumes) {
            fails++;
        }

        boolean deliveryConsumes = delivery.contains("Consumer chain")
                && delivery.contains("Delivery Result: PASS")
                && delivery.contains("informational only")
                && delivery.contains("Review consumption");
        evidence.append("## Delivery consumes Review\n\n");
        evidence.append("- Delivery Result from ReviewVerdict: ").append(deliveryConsumes).append('\n');
        evidence.append("- status: **").append(deliveryConsumes ? "PASS" : "FAIL").append("**\n\n");
        if (!deliveryConsumes) {
            fails++;
        }

        // Negative: incomplete matrix → Delivery FAIL even if we only check verdict logic
        Map<String, String> badAcceptance = new LinkedHashMap<String, String>(acceptance);
        badAcceptance.put("A99", "unmapped on purpose");
        VerificationMatrix incomplete = VerificationMatrix.build(badAcceptance, mapping, workDir.toPath());
        ReviewVerdict failVerdict = ReviewReportFormatter.formatConsumingMatrix(
                analysis.requirement, stages, workDir, incomplete);
        String failDelivery = DeliveryReportFormatter.formatConsumingReview(
                analysis.requirement, stages, workDir, failVerdict);
        boolean failPropagates = !failVerdict.deliveryPass
                && failDelivery.contains("Delivery Result: FAIL")
                && failVerdict.reviewMarkdown.contains("A99 FAIL")
                && failVerdict.reviewMarkdown.contains("Delivery recommendation: FAIL");
        evidence.append("## Negative: Matrix MISSING ⇒ Review FAIL ⇒ Delivery FAIL\n\n");
        evidence.append("- Runtime may still be green; Delivery must FAIL: ").append(failPropagates).append('\n');
        evidence.append("- status: **").append(failPropagates ? "PASS" : "FAIL").append("**\n\n");
        if (!failPropagates) {
            fails++;
        }

        boolean risksRecorded = review.contains("Matrix dual-maintenance")
                && review.contains("Patch Coverage ≠ Acceptance coverage");
        evidence.append("## Risks recorded (not solved)\n\n");
        evidence.append("- dual-maintenance + patch≠acceptance noted in Review: ")
                .append(risksRecorded).append('\n');
        evidence.append("- status: **").append(risksRecorded ? "PASS" : "FAIL").append("**\n\n");
        if (!risksRecorded) {
            fails++;
        }

        boolean matrixPresent = matrixFile.contains("COVERED") && matrix.complete;
        evidence.append("## Matrix artifact on workspace\n\n");
        evidence.append("- verification-matrix.md written by runner: ").append(matrixPresent).append('\n');
        evidence.append("- status: **").append(matrixPresent ? "PASS" : "FAIL").append("**\n\n");
        if (!matrixPresent) {
            fails++;
        }

        evidence.append("## Evidence Delta (new this round only)\n\n");
        evidence.append("| New evidence | Result |\n");
        evidence.append("|--------------|--------|\n");
        evidence.append("| Review lists A1/A2/A3 from Matrix (not mvn summary) | ")
                .append(reviewConsumes ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Delivery Result follows ReviewVerdict | ")
                .append(deliveryConsumes ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| MISSING Acceptance ⇒ Delivery FAIL (Runtime SUCCESS ignored) | ")
                .append(failPropagates ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Dual-maintenance / patch≠acceptance risks recorded | ")
                .append(risksRecorded ? "PASS" : "FAIL").append(" |\n");
        evidence.append("\nNot claimed: single-source Plan→Matrix generation, Acceptance→Code Change mapping, Claude.\n");
        evidence.append("Not re-scored: Sprint A/B matrix existence / Phase 1 Analysis.\n\n");
        evidence.append("## Sprint C verdict\n\n");
        if (fails == 0) {
            evidence.append("**PASS** — Verification → Review → Delivery is a strict consumer chain.\n");
        } else {
            evidence.append("**FAIL** — ").append(fails).append(" check group(s) failed.\n");
        }

        Files.write(reportFile, evidence.toString().getBytes(Charset.forName("UTF-8")));
        Path docsReport = new File(module, "../docs/execution-path-sprint-c-evidence.md").toPath().normalize();
        if (docsReport.getParent() != null && Files.isDirectory(docsReport.getParent())) {
            Files.write(docsReport, evidence.toString().getBytes(Charset.forName("UTF-8")));
        }

        System.out.println(evidence.toString());
        System.out.println("report = " + reportFile.toAbsolutePath());
        if (fails > 0) {
            System.err.println("Execution Path Sprint C FAILED");
            System.exit(1);
        }
        System.out.println("Execution Path Sprint C OK");
    }

    private static void seedPatches(Path workDir, Map<String, String> patches) throws Exception {
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path target = workDir.resolve(e.getKey());
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
    }

    private static String read(File workDir, String name) throws Exception {
        Path p = workDir.toPath().resolve(name);
        if (!Files.isRegularFile(p)) {
            return "";
        }
        return new String(Files.readAllBytes(p), Charset.forName("UTF-8"));
    }
}
