package com.ai4se.runtime.demo.analysis;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes Delivery Bundle files (analysis only — no patches/). */
public final class DeliveryBundleWriter {

    private DeliveryBundleWriter() {
    }

    public static void write(
            Path bundleDir,
            String requirement,
            RepositoryFacts facts,
            RepositoryContext context,
            GapReport gap,
            ClarificationSession clarification,
            String planMarkdown,
            String profileId) throws IOException {
        write(bundleDir, requirement, facts, context, gap, clarification, planMarkdown, profileId, null, 1);
    }

    public static void write(
            Path bundleDir,
            String requirement,
            RepositoryFacts facts,
            RepositoryContext context,
            GapReport gap,
            ClarificationSession clarification,
            String planMarkdown,
            String profileId,
            StopCondition.Result stop,
            int clarificationRound) throws IOException {
        Files.createDirectories(bundleDir);
        write(bundleDir.resolve("requirement.md"), "# Requirement\n\n" + requirement.trim() + "\n");
        write(bundleDir.resolve("analysis-context.md"), renderContext(context, facts));
        write(bundleDir.resolve("gap-report.md"), renderGap(gap));
        write(bundleDir.resolve("clarification.md"), clarification.toMarkdown());
        Path planFile = bundleDir.resolve("plan.md");
        if (planMarkdown != null) {
            write(planFile, planMarkdown);
        } else if (Files.exists(planFile)) {
            Files.delete(planFile);
        }
        Path stopFile = bundleDir.resolve("stop-decision.md");
        if (stop != null && stop.isStop()) {
            write(stopFile, StopCondition.toMarkdown(stop, clarificationRound));
        } else if (Files.exists(stopFile)) {
            Files.delete(stopFile);
        }
        write(bundleDir.resolve("verify.yaml"), "command: mvn -f pom.xml -q test\n");
        write(bundleDir.resolve("profile.yaml"),
                "id: " + profileId + "\n"
                        + "typeLabel: pilot-requirement-analysis\n"
                        + "projectId: pilot-analysis\n");
        write(bundleDir.resolve("facts.md"), renderFacts(facts));
        write(bundleDir.resolve("BUNDLE_STATUS.md"),
                "# Bundle Status\n\n"
                        + "gap_status: " + gap.getStatus() + "\n"
                        + "may_plan: " + gap.mayPlan() + "\n"
                        + "has_plan: " + (planMarkdown != null) + "\n"
                        + "stop: " + (stop != null && stop.isStop() ? "STOP_HUMAN" : "CONTINUE") + "\n"
                        + "note: Analysis pilot — no patches/, no Execution.\n");
    }

    private static void write(Path file, String body) throws IOException {
        Files.write(file, body.getBytes(Charset.forName("UTF-8")));
    }

    private static String renderContext(RepositoryContext context, RepositoryFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Repository Context\n\n");
        sb.append("confidence: ").append(context.getConfidence()).append("\n\n");
        sb.append("## Candidate Files\n\n");
        for (String f : context.getCandidateFiles()) {
            sb.append("- `").append(f).append("`\n");
        }
        if (context.getCandidateFiles().isEmpty()) {
            sb.append("- (none)\n");
        }
        sb.append("\n## Relevant Modules\n\n");
        for (String m : context.getRelevantModules()) {
            sb.append("- ").append(m).append("\n");
        }
        if (context.getRelevantModules().isEmpty()) {
            sb.append("- (none)\n");
        }
        sb.append("\n## TopK\n\n");
        for (String t : context.getTopK()) {
            sb.append("- ").append(t).append("\n");
        }
        sb.append("\n## Unknown\n\n");
        for (String u : context.getUnknown()) {
            sb.append("- ").append(u).append("\n");
        }
        if (context.getUnknown().isEmpty()) {
            sb.append("- (none)\n");
        }
        sb.append("\n## Need Clarification\n\n");
        for (String n : context.getNeedClarification()) {
            sb.append("- ").append(n).append("\n");
        }
        if (context.getNeedClarification().isEmpty()) {
            sb.append("- (none)\n");
        }
        sb.append("\n## Facts Ref (summary)\n\n");
        sb.append("- strategy: ").append(facts.getMeta().get("strategy")).append("\n");
        sb.append("- truncated: ").append(facts.getMeta().get("truncated")).append("\n");
        return sb.toString();
    }

    private static String renderGap(GapReport gap) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Gap Report\n\n");
        sb.append("gap_status: ").append(gap.getStatus()).append("\n");
        sb.append("blocking_gap_count: ").append(gap.getBlockingGapCount()).append("\n");
        sb.append("assumable_gap_count: ").append(gap.getAssumableGapCount()).append("\n\n");
        section(sb, "Known", gap.getKnown());
        section(sb, "Unknown", gap.getUnknown());
        section(sb, "Assumption", gap.getAssumptions());
        section(sb, "Risk", gap.getRisks());
        section(sb, "Decision Needed", gap.getDecisionNeeded());
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, java.util.List<String> items) {
        sb.append("## ").append(title).append("\n\n");
        if (items.isEmpty()) {
            sb.append("- (none)\n\n");
            return;
        }
        for (String i : items) {
            sb.append("- ").append(i).append("\n");
        }
        sb.append('\n');
    }

    private static String renderFacts(RepositoryFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Repository Facts\n\n");
        sb.append("## Modules\n\n");
        for (String m : facts.getModules()) {
            sb.append("- ").append(m).append("\n");
        }
        sb.append("\n## Build Files\n\n");
        for (String b : facts.getBuildFiles()) {
            sb.append("- ").append(b).append("\n");
        }
        sb.append("\n## Hits\n\n");
        for (RepositoryFacts.Hit h : facts.getHits()) {
            sb.append("- `").append(h.getPath()).append("` score=")
                    .append(h.getScore()).append(" — ").append(h.getExcerpt()).append("\n");
        }
        sb.append("\n## Facts\n\n");
        for (String f : facts.getFacts()) {
            sb.append("- ").append(f).append("\n");
        }
        return sb.toString();
    }
}
