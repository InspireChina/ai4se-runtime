package com.ai4se.orchestration.development;

import com.ai4se.context.compress.CompressionRetention;
import com.ai4se.context.packagebuild.ModelInputEnvelope;
import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.context.rules.ApplicableRuleAssembler;
import com.ai4se.context.rules.CustomerRuleLoader;
import com.ai4se.context.rules.RuleDocument;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.EffectiveConstraintBundle;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.verification.DefectPackageWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Development Context Package — every Dev / re-Dev must have a package.
 * When Defect exists (Verify FAIL loop), Defect is P1.
 */
public final class DevPackageBuilder {

    public static final String ROLE = "Development";

    private DevPackageBuilder() {
    }

    public static Path packageDir(Path workspace, String storyId, int round) {
        return workspace.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("development").resolve("round-" + round);
    }

    public static int nextRound(Path workspace, String storyId) throws IOException {
        Path root = workspace.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("development");
        if (!Files.isDirectory(root)) {
            return 1;
        }
        int max = 0;
        for (Path p : Files.newDirectoryStream(root)) {
            String name = p.getFileName().toString();
            if (name.startsWith("round-")) {
                try {
                    max = Math.max(max, Integer.parseInt(name.substring("round-".length())));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        return max + 1;
    }

    public static Path latestManifest(Path workspace, String storyId) throws IOException {
        Path root = workspace.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("development");
        if (!Files.isDirectory(root)) {
            return null;
        }
        Path latest = null;
        int max = -1;
        for (Path p : Files.newDirectoryStream(root)) {
            String name = p.getFileName().toString();
            if (!name.startsWith("round-")) {
                continue;
            }
            try {
                int n = Integer.parseInt(name.substring("round-".length()));
                Path m = p.resolve("manifest.md");
                if (n > max && Files.isRegularFile(m)) {
                    max = n;
                    latest = m;
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return latest;
    }

    public static boolean hasPackage(Path workspace, String storyId) throws IOException {
        return latestManifest(workspace, storyId) != null;
    }

    /**
     * Build Dev package. Includes latest Defect in P1 when present (re-Dev after Verify FAIL).
     * Applicable customer Rules are P1; tiny budget refuses rather than dropping them.
     */
    public static Path build(Path workspace, String storyId) throws IOException {
        return build(workspace, storyId, PackageBudget.UNLIMITED);
    }

    public static Path build(Path workspace, String storyId, PackageBudget budget) throws IOException {
        if (budget == null) {
            budget = PackageBudget.UNLIMITED;
        }
        List<String> allowed = PlanRecords.readAllowedFiles(workspace, storyId);
        if (allowed.isEmpty()) {
            throw new StageGateException("Dev Package requires Allowed Files from Plan");
        }
        Path defect = DefectPackageWriter.latest(workspace, storyId);
        int round = nextRound(workspace, storyId);
        Path dir = packageDir(workspace, storyId, round);
        Files.createDirectories(dir.resolve("slices"));

        StringBuilder allowedBody = new StringBuilder("# Allowed Files\n\n");
        for (String f : allowed) {
            allowedBody.append("- ").append(f).append('\n');
        }
        byte[] allowedBytes = allowedBody.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(dir.resolve("slices/allowed-files.md"), allowedBytes);

        com.ai4se.context.story.StoryRequirement requirement =
                com.ai4se.context.story.StoryRequirementReader.read(workspace, storyId);
        if (requirement.acceptance().isEmpty()) {
            throw new StageGateException("Dev Package P1 requires Acceptance from requirement");
        }
        StringBuilder accBody = new StringBuilder("# Acceptance (P1)\n\n");
        for (String a : requirement.acceptance()) {
            accBody.append("- ").append(a).append('\n');
        }
        byte[] acceptanceBytes = accBody.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(dir.resolve("slices/acceptance.md"), acceptanceBytes);

        Path planFile = PlanRecords.planningDir(workspace, storyId).resolve(PlanRecords.PLAN_FILE);
        byte[] planBytes = new byte[0];
        if (Files.isRegularFile(planFile)) {
            Files.copy(
                    planFile,
                    dir.resolve("slices/plan-summary.md"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            planBytes = Files.readAllBytes(dir.resolve("slices/plan-summary.md"));
        }

        Path gapReport = workspace.resolve(".story").resolve(storyId)
                .resolve("analysis").resolve("gap.report.md");
        byte[] gapBytes = new byte[0];
        if (Files.isRegularFile(gapReport)) {
            Files.copy(
                    gapReport,
                    dir.resolve("slices/gap-ref.md"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            gapBytes = Files.readAllBytes(dir.resolve("slices/gap-ref.md"));
        }

        Path changed = workspace.resolve(".story").resolve(storyId)
                .resolve("development").resolve("changed-files.md");
        String diffRef = Files.isRegularFile(changed)
                ? ("See " + changed + "\n")
                : "(no changes recorded yet — first Dev)\n";
        byte[] diffBytes = ("# Diff ref\n\n" + diffRef).getBytes(StandardCharsets.UTF_8);
        Files.write(dir.resolve("slices/diff-ref.md"), diffBytes);

        List<String> p1 = new ArrayList<String>();
        p1.add("slices/allowed-files.md");
        p1.add("slices/acceptance.md");
        if (planBytes.length > 0) {
            p1.add("slices/plan-summary.md");
        }
        if (gapBytes.length > 0) {
            p1.add("slices/gap-ref.md");
        }
        Path constraints = EffectiveConstraintBundle.markdownPath(workspace, storyId);
        byte[] constraintBytes = new byte[0];
        if (Files.isRegularFile(constraints)) {
            Files.copy(constraints, dir.resolve("slices/effective-constraints.md"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            constraintBytes = Files.readAllBytes(dir.resolve("slices/effective-constraints.md"));
            p1.add("slices/effective-constraints.md");
        }
        p1.add("slices/diff-ref.md");
        long baseBytes = allowedBytes.length + acceptanceBytes.length + planBytes.length
                + gapBytes.length + diffBytes.length + constraintBytes.length;
        if (defect != null) {
            byte[] defectRefBytes = ("# Defect source\n\n- " + defect + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            Files.write(dir.resolve("slices/defect-ref.md"), defectRefBytes);
            p1.add("slices/defect-ref.md");
            Files.copy(defect, dir.resolve("slices/defect.md"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            byte[] defectBytes = Files.readAllBytes(dir.resolve("slices/defect.md"));
            p1.add("slices/defect.md");
            baseBytes += defectRefBytes.length + defectBytes.length;
        }

        List<RuleDocument> applicable = CustomerRuleLoader.loadApplicable(workspace, ROLE);
        ApplicableRuleAssembler.requireFitOrRefuse(baseBytes, applicable, budget);
        List<String> ruleIds = ApplicableRuleAssembler.installIntoPackage(dir, applicable, p1);

        StringBuilder manifest = new StringBuilder();
        manifest.append("# Context Package Manifest\n\n");
        manifest.append("- role: ").append(ROLE).append('\n');
        manifest.append("- story_id: ").append(storyId).append('\n');
        manifest.append("- round: ").append(round).append('\n');
        manifest.append('\n').append("## priority1\n\n");
        for (String item : p1) {
            manifest.append("- ").append(item).append('\n');
        }
        if (defect != null) {
            manifest.append("\n## defects\n\n");
            manifest.append("- ").append(defect).append('\n');
        } else {
            manifest.append("\n## defects\n\n");
            manifest.append("- (none)\n");
        }
        manifest.append("\n## ids\n\n");
        if (ruleIds.isEmpty()) {
            manifest.append("- (no applicable rules)\n");
        } else {
            for (String id : ruleIds) {
                manifest.append("- ").append(id).append('\n');
            }
        }
        manifest.append("\n## forbidden_filtered\n\n");
        manifest.append("- files outside Allowed\n");
        manifest.append("- self-verify green claims\n");
        manifest.append("- silently dropping applicable Rules under budget pressure\n");
        if (budget.isLimited()) {
            manifest.append("\n## budget\n\n");
            manifest.append("- max_bytes: ").append(budget.maxBytes()).append('\n');
            manifest.append("- policy: applicable Rules never dropped — FAIL or expand budget\n");
        }
        String manifestText = manifest.toString();
        Files.write(dir.resolve("manifest.md"), manifestText.getBytes(StandardCharsets.UTF_8));
        ModelInputEnvelope.write(
                dir,
                ROLE,
                storyId,
                defect == null
                        ? "Implement the approved plan within Allowed Files. Inspect nearby code/tests before editing."
                        : "Repair the current Defect within the original Allowed Files. Preserve every Acceptance and Rule.",
                p1,
                budget);
        CompressionRetention.requireRetainedInManifest(ROLE, manifestText, defect != null);
        List<String> retained = new ArrayList<String>(Arrays.asList(
                "allowed_files", "conclusions", "unknown", "knowledge_ids"));
        if (defect != null) {
            retained.add("defect");
        }
        CompressionRetention.recordRebuild(
                workspace, storyId, ROLE, dir, retained, CompressionRetention.MUST_DISCARD);
        return dir;
    }

    public static void requirePresent(Path workspace, String storyId) throws IOException {
        if (!hasPackage(workspace, storyId)) {
            throw new StageGateException("Development Package missing — build before Verification");
        }
        Path defect = DefectPackageWriter.latest(workspace, storyId);
        if (defect != null) {
            Path manifest = latestManifest(workspace, storyId);
            if (manifest == null) {
                throw new StageGateException("Dev Package missing after Defect");
            }
            String text = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
            if (!text.contains("defect") && !text.contains(defect.getFileName().toString())) {
                throw new StageGateException(
                        "Re-Dev after FAIL requires Defect in Dev Package P1");
            }
        }
    }
}
