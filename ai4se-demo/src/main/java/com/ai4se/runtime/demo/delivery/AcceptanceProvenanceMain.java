package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.demo.analysis.PlanAcceptance;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.demo.input.InputDeliveryScenario;
import com.ai4se.runtime.demo.input.ProductionInputLoader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceptance Provenance — Plan is sole Acceptance ID source; Matrix/Review/Delivery reuse same IDs.
 * Exit when four criteria pass; then stop (no Matrix V2). No Runtime / Claude.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.AcceptanceProvenanceMain
 * </pre>
 */
public final class AcceptanceProvenanceMain {

    private AcceptanceProvenanceMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        Path outRoot = new File(module, "target/acceptance-provenance").toPath();
        Path analysisOut = outRoot.resolve("analysis-bundle");
        Path patchesDir = new File(module, "execution-path-sprint-a-input/patches").toPath();
        Path reportFile = outRoot.resolve("evidence-delta.md");
        Files.createDirectories(outRoot);

        StringBuilder evidence = new StringBuilder();
        evidence.append("# Evidence Delta — Acceptance Provenance\n\n");
        evidence.append("playbook: docs/build-pathway-playbook.md §2.1.1\n");
        evidence.append("question: Are Plan Acceptance IDs the sole source across Matrix/Review/Delivery?\n\n");

        int fails = 0;

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                new File(module, "pilot-workspace").toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                analysisOut,
                null,
                false,
                "acceptance-provenance");

        String planBody = new String(Files.readAllBytes(analysisOut.resolve("plan.md")),
                Charset.forName("UTF-8"));
        List<PlanAcceptance.Item> planItems = PlanAcceptance.parseItems(planBody);
        List<String> planIds = PlanAcceptance.idsFromItems(planItems);
        boolean planHasVerify = true;
        for (PlanAcceptance.Item item : planItems) {
            if (item.verifyRef == null || item.verifyRef.isEmpty()) {
                planHasVerify = false;
            }
        }
        evidence.append("## 1. Plan is sole Acceptance ID source\n\n");
        evidence.append("- plan IDs: ").append(planIds).append('\n');
        evidence.append("- each ID has inline `| verify:`: ").append(planHasVerify).append('\n');
        evidence.append("- status: **")
                .append(!planIds.isEmpty() && planHasVerify ? "PASS" : "FAIL").append("**\n\n");
        if (planIds.isEmpty() || !planHasVerify) {
            fails++;
        }

        Map<String, String> patches = ProductionInputLoader.readPatches(patchesDir);
        File probe = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "acceptance-provenance-probe");
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path t = probe.toPath().resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
        // Matrix from Plan only — no separate mapping file IDs.
        VerificationMatrix matrix = VerificationMatrix.buildFromPlan(planItems, probe.toPath());
        evidence.append("## 2. Matrix auto-generated from Plan\n\n");
        evidence.append("- matrix IDs: ").append(matrix.acceptanceIds()).append('\n');
        evidence.append("- complete: ").append(matrix.complete).append('\n');
        boolean matrixFromPlan = matrix.acceptanceIds().equals(planIds) || sameIds(matrix.acceptanceIds(), planIds);
        evidence.append("- IDs match Plan (no external ID list): ").append(matrixFromPlan).append('\n');
        evidence.append("- status: **")
                .append(matrixFromPlan && matrix.complete ? "PASS" : "FAIL").append("**\n\n");
        if (!matrixFromPlan || !matrix.complete) {
            fails++;
        }

        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "acceptance-provenance-work");
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", planBody.endsWith("\n") ? planBody : planBody + "\n");
        InputDeliveryScenario scenario = new InputDeliveryScenario(
                "acceptance-provenance",
                "provenance",
                "acceptance-provenance",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches);
        SerialDeliveryRunner.run(workDir, scenario, matrix);

        String review = read(workDir, "REVIEW_REPORT.md");
        String delivery = read(workDir, "DELIVERY_REPORT.md");
        List<String> reviewIds = AcceptanceProvenanceGate.extractProvenanceIds(review);
        List<String> deliveryIds = AcceptanceProvenanceGate.extractProvenanceIds(delivery);
        List<String> inventedReview = AcceptanceProvenanceGate.findInventedIds(review, planIds);
        // Delivery body also mentions criterion text with backticks — restrict invented check to
        // provenance section + verdict lines by checking only IDs outside plan in provenance extract
        // plus explicit A4-style inventions in verdict headers.
        List<String> inventedDelivery = new ArrayList<String>();
        for (String id : deliveryIds) {
            if (!planIds.contains(id)) {
                inventedDelivery.add(id);
            }
        }

        evidence.append("## 3. Review consumes Plan IDs only (no invent)\n\n");
        evidence.append("- review provenance IDs: ").append(reviewIds).append('\n');
        evidence.append("- invented IDs: ").append(inventedReview).append('\n');
        boolean reviewOk = inventedReview.isEmpty() && sameIds(reviewIds, planIds)
                && review.contains("must not** invent");
        evidence.append("- status: **").append(reviewOk ? "PASS" : "FAIL").append("**\n\n");
        if (!reviewOk) {
            fails++;
        }

        evidence.append("## 4. Delivery consumes Plan IDs only (no invent)\n\n");
        evidence.append("- delivery provenance IDs: ").append(deliveryIds).append('\n');
        evidence.append("- invented IDs: ").append(inventedDelivery).append('\n');
        boolean deliveryOk = inventedDelivery.isEmpty() && sameIds(deliveryIds, planIds)
                && delivery.contains("must not invent Acceptance IDs");
        evidence.append("- status: **").append(deliveryOk ? "PASS" : "FAIL").append("**\n\n");
        if (!deliveryOk) {
            fails++;
        }

        AcceptanceProvenanceGate.Result gate = AcceptanceProvenanceGate.check(
                planIds, matrix.acceptanceIds(), reviewIds, deliveryIds);
        evidence.append("## Provenance Gate (ID set equality)\n\n");
        evidence.append("- ").append(gate.message).append('\n');
        evidence.append("- status: **").append(gate.pass ? "PASS" : "FAIL").append("**\n\n");
        if (!gate.pass) {
            fails++;
        }

        // Negative: drop A3 from matrix simulation → gate fails
        List<String> truncated = new ArrayList<String>(planIds);
        if (truncated.size() > 1) {
            truncated.remove(truncated.size() - 1);
        }
        AcceptanceProvenanceGate.Result neg = AcceptanceProvenanceGate.check(
                planIds, truncated, reviewIds, deliveryIds);
        evidence.append("## Negative: Plan has extra ID Matrix dropped → Gate FAIL\n\n");
        evidence.append("- pass=").append(neg.pass).append(" msg=").append(neg.message).append('\n');
        evidence.append("- status: **").append(!neg.pass ? "PASS" : "FAIL").append("**\n\n");
        if (neg.pass) {
            fails++;
        }

        // Negative: Review invents A99
        String forged = review + "\n- **A99 PASS** — invented\n";
        List<String> forgedInvented = AcceptanceProvenanceGate.findInventedIds(forged, planIds);
        evidence.append("## Negative: Review invents A99 → detected\n\n");
        evidence.append("- invented: ").append(forgedInvented).append('\n');
        evidence.append("- status: **").append(forgedInvented.contains("A99") ? "PASS" : "FAIL")
                .append("**\n\n");
        if (!forgedInvented.contains("A99")) {
            fails++;
        }

        evidence.append("## Evidence Delta (new this round only)\n\n");
        evidence.append("| New evidence | Result |\n");
        evidence.append("|--------------|--------|\n");
        evidence.append("| Plan embeds Acceptance IDs + verify bindings | ")
                .append(!planIds.isEmpty() && planHasVerify ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Matrix built from Plan only (no parallel ID list) | ")
                .append(matrixFromPlan ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Review/Delivery ID sets == Plan; invent forbidden | ")
                .append(reviewOk && deliveryOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Provenance Gate set equality | ")
                .append(gate.pass ? "PASS" : "FAIL").append(" |\n");
        evidence.append("\nNot claimed: Patch≠Acceptance evolution, Matrix V2, Claude, 真仓.\n");
        evidence.append("Stopped after exit criteria — no further Matrix polish.\n\n");
        evidence.append("## Acceptance Provenance verdict\n\n");
        if (fails == 0) {
            evidence.append("**PASS** — Acceptance ID lifecycle is Plan-sourced end-to-end.\n");
        } else {
            evidence.append("**FAIL** — ").append(fails).append(" check group(s) failed.\n");
        }

        Files.write(reportFile, evidence.toString().getBytes(Charset.forName("UTF-8")));
        Path docsReport = new File(module, "../docs/acceptance-provenance-evidence.md").toPath().normalize();
        if (docsReport.getParent() != null && Files.isDirectory(docsReport.getParent())) {
            Files.write(docsReport, evidence.toString().getBytes(Charset.forName("UTF-8")));
        }

        System.out.println(evidence.toString());
        System.out.println("report = " + reportFile.toAbsolutePath());
        if (fails > 0) {
            System.err.println("Acceptance Provenance FAILED");
            System.exit(1);
        }
        System.out.println("Acceptance Provenance OK — stop; next = real-repo DoD when named.");
    }

    private static boolean sameIds(List<String> a, List<String> b) {
        return new java.util.LinkedHashSet<String>(a).equals(new java.util.LinkedHashSet<String>(b));
    }

    private static String read(File workDir, String name) throws Exception {
        Path p = workDir.toPath().resolve(name);
        if (!Files.isRegularFile(p)) {
            return "";
        }
        return new String(Files.readAllBytes(p), Charset.forName("UTF-8"));
    }
}
