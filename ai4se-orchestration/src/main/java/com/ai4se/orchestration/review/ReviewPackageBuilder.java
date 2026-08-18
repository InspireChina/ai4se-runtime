package com.ai4se.orchestration.review;

import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.packagebuild.ModelInputEnvelope;
import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Review Context Package — P1 embeds Acceptance, latest Verify report, Diff, Plan summary.
 * Problem class: Review short-circuit — fixture decision without package/adapter is not the main path.
 */
public final class ReviewPackageBuilder {

    public static final String ROLE = "Review";

    private ReviewPackageBuilder() {
    }

    public static Path packageDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("packages").resolve("review");
    }

    public static ContextPackageResult build(Path workspace, String storyId) throws IOException {
        VerificationControl.requirePassBeforeReview(workspace, storyId);
        StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
        if (requirement.acceptance().isEmpty()) {
            throw new StageGateException("Review Package P1 requires Acceptance");
        }

        Path dir = packageDir(workspace, storyId);
        Files.createDirectories(dir.resolve("slices"));

        List<String> p1 = new ArrayList<String>();

        StringBuilder acc = new StringBuilder("# Acceptance (P1)\n\n");
        for (String item : requirement.acceptance()) {
            acc.append("- ").append(item).append('\n');
        }
        Files.write(dir.resolve("slices/acceptance.md"), acc.toString().getBytes(StandardCharsets.UTF_8));
        p1.add("slices/acceptance.md");

        Path verifyReport = latestPassReport(workspace, storyId);
        if (verifyReport == null) {
            throw new StageGateException("Review Package requires Verification PASS report");
        }
        Files.copy(verifyReport, dir.resolve("slices/verify-report.md"), StandardCopyOption.REPLACE_EXISTING);
        p1.add("slices/verify-report.md");

        Path changed = workspace.resolve(".story").resolve(storyId)
                .resolve("development").resolve("changed-files.md");
        String diffBody = Files.isRegularFile(changed)
                ? new String(Files.readAllBytes(changed), StandardCharsets.UTF_8)
                : "(no changed-files.md)\n";
        Files.write(
                dir.resolve("slices/diff.md"),
                ("# Diff\n\n" + diffBody).getBytes(StandardCharsets.UTF_8));
        p1.add("slices/diff.md");

        Path planFile = PlanRecords.planningDir(workspace, storyId).resolve(PlanRecords.PLAN_FILE);
        if (Files.isRegularFile(planFile)) {
            Files.copy(planFile, dir.resolve("slices/plan-summary.md"), StandardCopyOption.REPLACE_EXISTING);
            p1.add("slices/plan-summary.md");
        }

        Path requirementSrc = StoryRequirementReader.requirementPath(workspace, storyId);
        Files.copy(requirementSrc, dir.resolve("slices/requirement.md"), StandardCopyOption.REPLACE_EXISTING);
        p1.add("slices/requirement.md");

        StringBuilder acTemplate = new StringBuilder();
        acTemplate.append("# AC evidence template (fill per item)\n\n");
        acTemplate.append("| AC | verdict | evidence |\n|----|---------|----------|\n");
        int i = 1;
        for (String item : requirement.acceptance()) {
            acTemplate.append("| AC").append(i++).append(" | ? | ").append(oneLine(item)).append(" |\n");
        }
        Files.write(
                dir.resolve("slices/ac-evidence-template.md"),
                acTemplate.toString().getBytes(StandardCharsets.UTF_8));
        p1.add("slices/ac-evidence-template.md");

        Path manifest = dir.resolve("manifest.md");
        StringBuilder m = new StringBuilder();
        m.append("# Context Package Manifest\n\n");
        m.append("- role: ").append(ROLE).append('\n');
        m.append("- story_id: ").append(storyId).append('\n');
        m.append("- goal: ").append(oneLine(requirement.goal())).append('\n');
        m.append('\n');
        m.append("## priority1\n\n");
        for (String item : p1) {
            m.append("- ").append(item).append('\n');
        }
        m.append('\n');
        m.append("## priority2\n\n");
        m.append("- (none)\n\n");
        m.append("## forbidden_filtered\n\n");
        m.append("- re-running full Verification as Review substitute\n");
        m.append("- silent fixture \"通过\" without review_source disclosure\n");
        String manifestText = m.toString();
        Files.write(manifest, manifestText.getBytes(StandardCharsets.UTF_8));
        ModelInputEnvelope.write(
                dir,
                ROLE,
                storyId,
                "Review the final diff against Acceptance and frozen Verification evidence. Write only a "
                        + "structured PASS, CONDITIONAL or REJECT result; do not modify business source.",
                p1,
                PackageBudget.PRODUCTION_P1);

        return new ContextPackageResult(ROLE, storyId, dir, manifest, p1);
    }

    static Path latestPassReport(Path workspace, String storyId) throws IOException {
        Path dir = VerificationControl.reportsDir(workspace, storyId);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Path latest = null;
        int max = -1;
        for (Path p : Files.newDirectoryStream(dir, "report-round-*.md")) {
            String name = p.getFileName().toString();
            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            if (!text.contains("outcome: PASS")) {
                continue;
            }
            try {
                int n = Integer.parseInt(name.substring("report-round-".length(), name.length() - 3));
                if (n > max) {
                    max = n;
                    latest = p;
                }
            } catch (NumberFormatException ignored) {
                if (latest == null) {
                    latest = p;
                }
            }
        }
        return latest;
    }

    private static String oneLine(String value) {
        if (Strings.isBlank(value)) {
            return "";
        }
        return value.replace('\n', ' ').trim();
    }
}
