package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.demo.analysis.GapReport;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.demo.input.InputDeliveryScenario;
import com.ai4se.runtime.demo.input.ProductionInputLoader;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Playbook §2.2 — local rollback after FAIL (not default Requirement).
 * Fixed means: this-gate + re-run PathwayRetrospectiveMain + blueprint ALIGN.
 *
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.LoopReturnPathMain
 */
public final class LoopReturnPathMain {

    private LoopReturnPathMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        Path repoRoot = module.toPath().getParent().normalize();
        Path docs = repoRoot.resolve("docs");
        Path outRoot = new File(module, "target/loop-return-path").toPath();
        Files.createDirectories(outRoot);
        Path reportFile = outRoot.resolve("evidence-delta.md");

        StringBuilder evidence = new StringBuilder();
        evidence.append("# Evidence Delta — Loop return path (§2.2)\n\n");
        evidence.append("playbook: docs/build-pathway-playbook.md §2.2 / §2.1.1 固定手段\n");
        evidence.append("question: On Delivery FAIL, does advice return locally (not default Requirement)?\n");
        evidence.append("role: AGENT_DECLARE only — Reviewer must close.\n\n");

        int fails = 0;

        // --- 1. This gate ---
        evidence.append("## 1. This gate output (§2.2 local rollback)\n\n");

        // 1a Matrix MISSING → Delivery FAIL → return Execution
        File workMissing = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "loop-return-missing");
        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                new File(module, "pilot-workspace").toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                outRoot.resolve("analysis-ok"),
                null,
                false,
                "loop-return-path");
        String plan = new String(Files.readAllBytes(outRoot.resolve("analysis-ok/plan.md")),
                Charset.forName("UTF-8"));
        Map<String, String> patches = ProductionInputLoader.readPatches(
                new File(module, "execution-path-sprint-a-input/patches").toPath());
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", plan.endsWith("\n") ? plan : plan + "\n");
        seedPatches(workMissing.toPath(), patches);
        Map<String, String> badAcceptance = new LinkedHashMap<String, String>();
        badAcceptance.put("A99", "unmapped on purpose");
        Map<String, String> mapping = VerificationMapping.load(
                new File(module, "execution-path-sprint-a-input/verification-mapping.txt").toPath());
        VerificationMatrix incomplete = VerificationMatrix.build(badAcceptance, mapping, workMissing.toPath());
        workMissing = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "loop-return-missing");
        SerialDeliveryRunner.run(workMissing,
                new InputDeliveryScenario(
                        "loop-return-missing",
                        "loop",
                        "loop-return-missing",
                        analysis.requirement,
                        ProductionInputLoader.DEFAULT_DISCOVERY,
                        "mvn -f pom.xml -q test",
                        planFiles,
                        patches),
                incomplete);
        String deliveryMissing = new String(Files.readAllBytes(
                new File(workMissing, "DELIVERY_REPORT.md").toPath()), Charset.forName("UTF-8"));
        boolean missingFail = deliveryMissing.contains("Delivery Result: FAIL")
                && deliveryMissing.contains("Recommended return: Execution")
                && deliveryMissing.contains("Forbidden default: Requirement")
                && !deliveryMissing.contains("Recommended return: Requirement");
        evidence.append("### 1a Matrix MISSING ⇒ FAIL ⇒ return Execution\n\n");
        evidence.append("- Delivery FAIL + Recommended return Execution: ").append(missingFail).append('\n');
        evidence.append("- status: **").append(missingFail ? "PASS" : "FAIL").append("**\n\n");
        if (!missingFail) {
            fails++;
        }

        // 1b Advisor table unit checks (Clarification / Analysis never → Requirement)
        boolean tableOk = "Execution".equals(LoopReturnAdvisor.recommendedReturn(
                LoopReturnAdvisor.FailKind.SHELL_VERIFY))
                && "Execution".equals(LoopReturnAdvisor.recommendedReturn(
                LoopReturnAdvisor.FailKind.MATRIX_MISSING))
                && "Clarification".equals(LoopReturnAdvisor.recommendedReturn(
                LoopReturnAdvisor.FailKind.CLARIFICATION_BLOCKED))
                && "Analysis".equals(LoopReturnAdvisor.recommendedReturn(
                LoopReturnAdvisor.FailKind.ANALYSIS_INVENTED))
                && LoopReturnAdvisor.forbidsDefaultRequirement(LoopReturnAdvisor.FailKind.SHELL_VERIFY);
        evidence.append("### 1b Advisor table (§2.2)\n\n");
        evidence.append("- SHELL/MATRIX→Execution; BLOCKED→Clarification; invented→Analysis: ")
                .append(tableOk).append('\n');
        evidence.append("- status: **").append(tableOk ? "PASS" : "FAIL").append("**\n\n");
        if (!tableOk) {
            fails++;
        }

        // 1c Clarification BLOCKED → no Plan; return Clarification (reuse promotion fixture)
        RequirementAnalysisPipeline.Result blocked = RequirementAnalysisPipeline.run(
                new File(module, "pilot-workspace-promotion").toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                outRoot.resolve("analysis-blocked"),
                RequirementAnalysisPipeline.promotionUnknownAnswers(),
                true,
                "loop-return-blocked");
        boolean blockedOk = blocked.gap.getStatus() == GapReport.Status.BLOCKED
                && !blocked.gap.mayPlan()
                && !blocked.planWritten
                && "Clarification".equals(LoopReturnAdvisor.recommendedReturn(
                LoopReturnAdvisor.FailKind.CLARIFICATION_BLOCKED));
        evidence.append("### 1c Clarification BLOCKED ⇒ stay Clarification (no Plan)\n\n");
        evidence.append("- gap BLOCKED, no plan, return Clarification: ").append(blockedOk).append('\n');
        evidence.append("- status: **").append(blockedOk ? "PASS" : "FAIL").append("**\n\n");
        if (!blockedOk) {
            fails++;
        }

        // Negative: never advise Requirement for verify/matrix fail
        String shellAdvice = LoopReturnAdvisor.renderSection(LoopReturnAdvisor.FailKind.SHELL_VERIFY);
        boolean negOk = !shellAdvice.contains("Recommended return: Requirement");
        evidence.append("### 1d Negative: Verification fail must not advise Requirement\n\n");
        evidence.append("- shell-fail advice omits Requirement as recommended: ").append(negOk).append('\n');
        evidence.append("- status: **").append(negOk ? "PASS" : "FAIL").append("**\n\n");
        if (!negOk) {
            fails++;
        }

        // --- 2. Prior retrospective re-run (covers closed gates) ---
        evidence.append("## 2. Prior closed-gate retrospective (subprocess)\n\n");
        runMvn(repoRoot, Arrays.asList("mvn", "-pl", "ai4se-demo", "-am", "-q", "compile"));
        int retro = runMvn(repoRoot, Arrays.asList(
                "mvn", "-pl", "ai4se-demo", "-q", "exec:java",
                "-Ddemo.mainClass=com.ai4se.runtime.demo.delivery.PathwayRetrospectiveMain"));
        boolean retroOk = retro == 0;
        if (!retroOk) {
            fails++;
        }
        evidence.append("- PathwayRetrospectiveMain exit: ").append(retro).append('\n');
        evidence.append("- status: **").append(retroOk ? "PASS" : "FAIL").append("**\n\n");

        // --- 3. Blueprint ---
        evidence.append("## 3. Blueprint alignment\n\n");
        String handbook = new String(Files.readAllBytes(docs.resolve("build-pathway-playbook.md")),
                Charset.forName("UTF-8"));
        boolean waterOk = handbook.contains("验证通路固定手段")
                && handbook.contains("§2.2")
                && handbook.contains("S8b/c")
                && handbook.contains("❌");
        if (!waterOk) {
            fails++;
        }
        evidence.append("- decision: **ALIGN**\n");
        evidence.append("- facts: §2.2 was written as loop basis but lacked runnable Delivery advice; ")
                .append("this gate adds FAIL→local return text without new Framework Gate / Claude / Runtime expand. ")
                .append("S8b/c remain locked; S4–S8a are ✅ 最小 only.\n");
        evidence.append("- HOLD_SURFACE/DEVIATE: not needed (handbook §2.2 already prescribed; ")
                .append("we only made FAIL reports consumable).\n");
        evidence.append("- status: **").append(waterOk ? "PASS" : "FAIL").append("**\n\n");

        evidence.append("## 4. Anti self-close\n\n");
        evidence.append("- `AGENT_DECLARE`: ").append(fails == 0 ? "PASS" : "FAIL").append('\n');
        evidence.append("- `REVIEWER_CLOSE`: **PENDING**\n\n");

        evidence.append("## Evidence Delta (new only)\n\n");
        evidence.append("| New evidence | Result |\n|--------------|--------|\n");
        evidence.append("| Delivery FAIL embeds §2.2 Recommended return | ")
                .append(missingFail ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Verification/Matrix fail ≠ default Requirement | ")
                .append(negOk && tableOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| BLOCKED stays Clarification | ")
                .append(blockedOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("| Prior retrospective still green | ")
                .append(retroOk ? "PASS" : "FAIL").append(" |\n");
        evidence.append("\nNot claimed: automated re-execution loop, S4 Human-Wait, Claude, Scheduler.\n");
        evidence.append("Not re-scored: continuous DoD green paths.\n\n");

        evidence.append("## Verdict\n\n");
        if (fails == 0) {
            evidence.append("**AGENT_DECLARE: PASS** — §2.2 local rollback is evidenced. ")
                    .append("**Await Reviewer close.**\n");
        } else {
            evidence.append("**AGENT_DECLARE: FAIL** — ").append(fails).append(" check(s) failed.\n");
        }

        Files.write(reportFile, evidence.toString().getBytes(Charset.forName("UTF-8")));
        Files.write(docs.resolve("loop-return-path-evidence.md"),
                evidence.toString().getBytes(Charset.forName("UTF-8")));
        System.out.println(evidence.toString());
        System.out.println("report = " + reportFile.toAbsolutePath());
        if (fails != 0) {
            System.exit(1);
        }
        System.out.println("Loop return path AGENT_DECLARE OK (Reviewer still PENDING)");
    }

    private static void seedPatches(Path workDir, Map<String, String> patches) throws Exception {
        for (Map.Entry<String, String> e : patches.entrySet()) {
            Path t = workDir.resolve(e.getKey());
            Files.createDirectories(t.getParent());
            Files.write(t, e.getValue().getBytes(Charset.forName("UTF-8")));
        }
    }

    private static int runMvn(Path repoRoot, List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sink = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.forName("UTF-8")));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                sink.append(line).append('\n');
            }
        } finally {
            r.close();
        }
        int code = p.waitFor();
        if (code != 0) {
            System.err.println("--- FAIL " + cmd + " exit=" + code + " ---");
            System.err.println(sink.length() > 3000 ? sink.substring(sink.length() - 3000) : sink.toString());
        }
        return code;
    }
}
