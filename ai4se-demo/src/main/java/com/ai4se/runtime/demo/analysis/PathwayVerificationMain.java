package com.ai4se.runtime.demo.analysis;

import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Playbook §2.1 pathway verification — stop-gate + resume-path.
 * Wave A: UNKNOWN → BLOCKED (can stop).
 * Wave B: concrete answers → Gap re-check → ASSUMABLE → Planning (can continue).
 * Wave C: fuzzy answers → still BLOCKED (answer quality).
 * Does not invent Graph/Scheduler. Does not claim Execution.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.analysis.PathwayVerificationMain
 * </pre>
 */
public final class PathwayVerificationMain {

    private PathwayVerificationMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace-promotion");
        Path reportDir = new File(module, "target/pathway-verification").toPath();
        Files.createDirectories(reportDir);
        Path bundleOut = reportDir.resolve("analysis-bundle");

        StringBuilder report = new StringBuilder();
        report.append("# Pathway Verification Report (generated)\n\n");
        report.append("playbook: docs/build-pathway-playbook.md §2.1–§2.3\n");
        report.append("workspace: `").append(workspace.getPath()).append("`\n");
        report.append("note: controllable fixture — stop-gate + greenfield resume + fuzzy reject\n\n");

        int fails = 0;
        String requirement = RequirementAnalysisPipeline.PROMOTION_REQUIREMENT;

        // --- 1 Requirement ---
        report.append("## 1. Requirement\n\n");
        report.append("- status: **PASS** (input present)\n");
        report.append("- evidence: engineering promotion requirement constant\n\n");

        // --- 2 Analysis ---
        RequirementAnalysisPipeline.Result emptyAnswers = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                requirement,
                bundleOut,
                Collections.<String, String>emptyMap(),
                true,
                "pathway-verify");

        report.append("## 2. Analysis\n\n");
        boolean factsOk = !containsInventedSchema(bundleOut.resolve("facts.md"));
        boolean noPatches = !Files.exists(bundleOut.resolve("patches"));
        boolean hasFacts = Files.exists(bundleOut.resolve("facts.md"));
        boolean hasContext = Files.exists(bundleOut.resolve("analysis-context.md"));
        boolean analysisPass = factsOk && noPatches && hasFacts && hasContext
                && emptyAnswers.gap.getStatus() == GapReport.Status.BLOCKED
                && !emptyAnswers.planWritten;
        report.append("- facts honest (no invented repository): ").append(factsOk).append('\n');
        report.append("- no patches/: ").append(noPatches).append('\n');
        report.append("- gap without answers: ").append(emptyAnswers.gap.getStatus()).append('\n');
        report.append("- plan written: ").append(emptyAnswers.planWritten).append('\n');
        report.append("- status: **").append(analysisPass ? "PASS" : "FAIL").append("**\n\n");
        if (!analysisPass) {
            fails++;
        }

        // --- 3a Clarification stop-gate (UNKNOWN) ---
        Path clarifyBundle = reportDir.resolve("clarification-bundle-unknown");
        RequirementAnalysisPipeline.Result unknownAnswers = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                requirement,
                clarifyBundle,
                RequirementAnalysisPipeline.promotionUnknownAnswers(),
                true,
                "pathway-verify-unknown");

        report.append("## 3a. Clarification stop-gate (UNKNOWN → BLOCKED)\n\n");
        boolean stillBlocked = unknownAnswers.gap.getStatus() == GapReport.Status.BLOCKED;
        boolean noPlanUnknown = !unknownAnswers.planWritten && !Files.exists(clarifyBundle.resolve("plan.md"));
        boolean hasQuestions = !unknownAnswers.context.getNeedClarification().isEmpty();
        boolean stopGatePass = stillBlocked && noPlanUnknown && hasQuestions;
        report.append("- questions present: ").append(hasQuestions).append('\n');
        report.append("- answers: UNKNOWN literals\n");
        report.append("- gap: ").append(unknownAnswers.gap.getStatus()).append('\n');
        report.append("- plan forbidden: ").append(noPlanUnknown).append('\n');
        report.append("- status: **").append(stopGatePass ? "PASS (can stop)" : "FAIL").append("**\n\n");
        if (!stopGatePass) {
            fails++;
        }

        // --- 3b Clarification answer-quality (FUZZY must stay BLOCKED) ---
        Path fuzzyBundle = reportDir.resolve("clarification-bundle-fuzzy");
        RequirementAnalysisPipeline.Result fuzzyAnswers = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                requirement,
                fuzzyBundle,
                RequirementAnalysisPipeline.promotionFuzzyAnswers(),
                true,
                "pathway-verify-fuzzy");

        report.append("## 3b. Clarification answer-quality (FUZZY → BLOCKED)\n\n");
        boolean fuzzyBlocked = fuzzyAnswers.gap.getStatus() == GapReport.Status.BLOCKED;
        boolean noPlanFuzzy = !fuzzyAnswers.planWritten && !Files.exists(fuzzyBundle.resolve("plan.md"));
        boolean fuzzyPass = fuzzyBlocked && noPlanFuzzy;
        report.append("- answers: fuzzy hedges (应该有吧 / 应该已经有了 / …)\n");
        report.append("- gap: ").append(fuzzyAnswers.gap.getStatus()).append('\n');
        report.append("- plan forbidden: ").append(noPlanFuzzy).append('\n');
        report.append("- status: **").append(fuzzyPass ? "PASS (fuzzy ≠ CLEAR)" : "FAIL").append("**\n\n");
        if (!fuzzyPass) {
            fails++;
        }

        // --- 3c + 4 Resume: concrete → Gap re-check → ASSUMABLE → Planning ---
        Path resumeBundle = reportDir.resolve("clarification-bundle-resume");
        RequirementAnalysisPipeline.Result concrete = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                requirement,
                resumeBundle,
                RequirementAnalysisPipeline.promotionConcreteGreenfieldAnswers(),
                true,
                "pathway-verify-resume");

        report.append("## 3c. Clarification resume (concrete → Gap re-check)\n\n");
        boolean mayPlan = concrete.gap.mayPlan();
        boolean assumableOrClear = concrete.gap.getStatus() == GapReport.Status.ASSUMABLE
                || concrete.gap.getStatus() == GapReport.Status.CLEAR;
        boolean planWritten = concrete.planWritten && Files.exists(resumeBundle.resolve("plan.md"));
        boolean planFromContext = planWritten && planMentionsClarification(resumeBundle.resolve("plan.md"));
        boolean noInventedFacts = !containsInventedSchema(resumeBundle.resolve("facts.md"));
        boolean resumePass = mayPlan && assumableOrClear && planWritten && planFromContext && noInventedFacts;
        report.append("- answers: concrete greenfield (可控场景，非假造已有表)\n");
        report.append("- gap after re-check: ").append(concrete.gap.getStatus()).append('\n');
        report.append("- mayPlan: ").append(mayPlan).append('\n');
        report.append("- plan.md written: ").append(planWritten).append('\n');
        report.append("- facts still honest: ").append(noInventedFacts).append('\n');
        report.append("- status: **").append(resumePass ? "PASS (can continue to Planning)" : "FAIL").append("**\n\n");
        if (!resumePass) {
            fails++;
        }

        report.append("## 4. Planning\n\n");
        report.append("- continuous path after concrete answers: **")
                .append(planWritten ? "PASS (draft written)" : "FAIL").append("**\n");
        report.append("- note: Planning PASS ≠ Execution ready (fixture still placeholder)\n\n");

        report.append("## 5. Execution\n\n- status: **NOT RUN** (Evidence Missing — need real coding workspace)\n");
        report.append("- side-check: ProductionRuntimeMain + sample-input (not continuous DoD)\n");
        report.append("- **Execution Engine PASS ≠ Delivery Path PASS**\n\n");
        report.append("## 6. Verification\n\n- status: **NOT RUN** (Evidence Missing)\n\n");
        report.append("## 7. Delivery\n\n- status: **NOT RUN** (Evidence Missing)\n\n");

        report.append("## Continuous seven-step DoD\n\n");
        report.append("- result: **FAIL** (expected: Execution→Delivery not run)\n");
        report.append("- wave A (stop): verified\n");
        report.append("- wave B (resume to Planning): verified on controllable greenfield answers\n");
        report.append("- wave C (fuzzy reject): verified\n\n");

        report.append("## Next unique action\n\n");
        report.append("Attach a real coding workspace (or grow fixture into compilable module) ");
        report.append("and verify Planning → Execution → Verification — still without expanding Runtime.\n");

        Path reportFile = reportDir.resolve("pathway-verification-report.md");
        Files.write(reportFile, report.toString().getBytes(Charset.forName("UTF-8")));

        System.out.println(report.toString());
        System.out.println("report = " + reportFile.toAbsolutePath());
        if (fails > 0) {
            System.err.println("Pathway verification FAILED (" + fails + " check(s))");
            System.exit(1);
        }
        System.out.println("Pathway verification OK — stop + resume-to-Planning + fuzzy reject.");
    }

    private static boolean planMentionsClarification(Path planFile) throws IOException {
        String body = new String(Files.readAllBytes(planFile), Charset.forName("UTF-8"));
        return body.contains("Clarification") || body.contains("绿场") || body.contains("Task Breakdown");
    }

    private static boolean containsInventedSchema(Path factsFile) throws IOException {
        if (!Files.exists(factsFile)) {
            return true;
        }
        String body = new String(Files.readAllBytes(factsFile), Charset.forName("UTF-8"));
        return body.contains("PromotionRepository")
                || body.contains("CREATE TABLE promotion")
                || body.contains("invented");
    }
}
