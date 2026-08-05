package com.ai4se.context.packagebuild;

import com.ai4se.context.compress.CompressionRetention;
import com.ai4se.context.rules.ApplicableRuleAssembler;
import com.ai4se.context.rules.CustomerRuleLoader;
import com.ai4se.context.rules.RuleDocument;
import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 02 Context Builder (minimal) — Analysis role.
 * Reads Story seed; refuses without usable Acceptance; loads applicable customer Rules into P1.
 * Appendix A: tiny budget + applicable Rule → FAIL (never silently drop Rules).
 */
public final class AnalysisPackageBuilder {

    public static final String ROLE = "Analysis";

    private AnalysisPackageBuilder() {
    }

    public static ContextPackageResult build(Path workspace, String storyId) throws IOException {
        return build(workspace, storyId, PackageBudget.UNLIMITED);
    }

    public static ContextPackageResult build(Path workspace, String storyId, PackageBudget budget)
            throws IOException {
        if (budget == null) {
            budget = PackageBudget.UNLIMITED;
        }
        StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
        AcceptanceGate.requireUsable(requirement);

        Path packageDir = workspace.resolve(".story").resolve(storyId).resolve("packages").resolve("analysis");
        Files.createDirectories(packageDir);
        Path slices = packageDir.resolve("slices");
        Files.createDirectories(slices);

        Path requirementSlice = slices.resolve("requirement.md");
        Files.copy(
                StoryRequirementReader.requirementPath(workspace, storyId),
                requirementSlice,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path acceptanceSlice = slices.resolve("acceptance.md");
        byte[] acceptanceBytes = renderAcceptance(requirement).getBytes(StandardCharsets.UTF_8);
        Files.write(acceptanceSlice, acceptanceBytes);

        List<String> p1 = new ArrayList<String>();
        p1.add("slices/requirement.md");
        p1.add("slices/acceptance.md");

        List<RuleDocument> applicable = CustomerRuleLoader.loadApplicable(workspace, ROLE);
        long baseBytes = Files.size(requirementSlice) + acceptanceBytes.length;
        ApplicableRuleAssembler.requireFitOrRefuse(baseBytes, applicable, budget);
        List<String> ruleIds = ApplicableRuleAssembler.installIntoPackage(packageDir, applicable, p1);

        Path manifest = packageDir.resolve("manifest.md");
        String manifestText = renderManifest(storyId, p1, requirement, ruleIds, budget, applicable.size());
        Files.write(manifest, manifestText.getBytes(StandardCharsets.UTF_8));
        CompressionRetention.requireRetainedInManifest(ROLE, manifestText, false);
        CompressionRetention.recordRebuild(
                workspace,
                storyId,
                ROLE,
                packageDir,
                Arrays.asList("acceptance", "conclusions", "unknown", "knowledge_ids"),
                CompressionRetention.MUST_DISCARD);

        return new ContextPackageResult(ROLE, storyId, packageDir, manifest, p1);
    }

    private static String renderAcceptance(StoryRequirement requirement) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Acceptance (P1)\n\n");
        for (String item : requirement.acceptance()) {
            sb.append("- ").append(item).append('\n');
        }
        return sb.toString();
    }

    private static String renderManifest(
            String storyId,
            List<String> p1,
            StoryRequirement requirement,
            List<String> ruleIds,
            PackageBudget budget,
            int applicableRuleCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Context Package Manifest\n\n");
        sb.append("- role: ").append(ROLE).append('\n');
        sb.append("- story_id: ").append(storyId).append('\n');
        sb.append("- story_refs: .story/").append(storyId).append("/requirement.md\n");
        sb.append("- goal: ").append(oneLine(requirement.goal())).append('\n');
        sb.append('\n');
        sb.append("## priority1\n\n");
        for (String item : p1) {
            sb.append("- ").append(item).append('\n');
        }
        sb.append('\n');
        sb.append("## priority2\n\n");
        sb.append("- (none for W2 minimal)\n\n");
        sb.append("## forbidden_filtered\n\n");
        sb.append("- whole repository tree\n");
        sb.append("- unrelated stories\n");
        sb.append("- silently dropping applicable Rules under budget pressure\n\n");
        sb.append("## ids\n\n");
        if (ruleIds.isEmpty()) {
            sb.append("- (no applicable rules)\n\n");
        } else {
            for (String id : ruleIds) {
                sb.append("- ").append(id).append('\n');
            }
            sb.append('\n');
        }
        sb.append("## budget\n\n");
        if (budget.isLimited()) {
            sb.append("- max_bytes: ").append(budget.maxBytes()).append('\n');
            sb.append("- applicable_rules: ").append(applicableRuleCount).append('\n');
            sb.append("- policy: applicable Rules never dropped — FAIL or expand budget\n");
        } else {
            sb.append("- status: unlimited\n");
            sb.append("- applicable_rules: ").append(applicableRuleCount).append('\n');
            sb.append("- note: P1 Acceptance + applicable Rules; CLI must not open if Builder refused\n");
        }
        return sb.toString();
    }

    private static String oneLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').trim();
    }
}
