package com.ai4se.context.packagebuild;

import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.context.rules.ApplicableRuleAssembler;
import com.ai4se.context.rules.CustomerRuleLoader;
import com.ai4se.context.rules.RuleDocument;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 02 Context Builder (minimal) — Planning role.
 * Feeds Discovery + Acceptance + optional Allowed hint; Adapter must write formal plan.md.
 */
public final class PlanningPackageBuilder {

    public static final String ROLE = "Planning";

    private PlanningPackageBuilder() {
    }

    public static ContextPackageResult build(Path workspace, String storyId) throws IOException {
        return build(workspace, storyId, Collections.<String>emptyList(), PackageBudget.UNLIMITED);
    }

    public static ContextPackageResult build(
            Path workspace, String storyId, List<String> allowedHint) throws IOException {
        return build(workspace, storyId, allowedHint, PackageBudget.UNLIMITED);
    }

    public static ContextPackageResult build(
            Path workspace, String storyId, List<String> allowedHint, PackageBudget budget) throws IOException {
        if (budget == null) {
            budget = PackageBudget.UNLIMITED;
        }
        StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
        AcceptanceGate.requireUsable(requirement);

        Path packageDir = workspace.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("planning");
        Files.createDirectories(packageDir);
        Path slices = packageDir.resolve("slices");
        Files.createDirectories(slices);

        Path requirementSlice = slices.resolve("requirement.md");
        Files.copy(
                StoryRequirementReader.requirementPath(workspace, storyId),
                requirementSlice,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path acceptanceSlice = slices.resolve("acceptance.md");
        StringBuilder acc = new StringBuilder("# Acceptance\n\n");
        for (String a : requirement.acceptance()) {
            acc.append("- ").append(a).append('\n');
        }
        Files.write(acceptanceSlice, acc.toString().getBytes(StandardCharsets.UTF_8));

        Path discoveryDir = workspace.resolve(".story").resolve(storyId).resolve("analysis");
        Path discoveryReport = discoveryDir.resolve("discovery.report.md");
        Path discoverySkip = discoveryDir.resolve("discovery.skip.md");
        Path discoverySlice = slices.resolve("discovery.md");
        if (Files.isRegularFile(discoveryReport)) {
            Files.copy(discoveryReport, discoverySlice, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else if (Files.isRegularFile(discoverySkip)) {
            Files.copy(discoverySkip, discoverySlice, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.write(discoverySlice, ("# Discovery\n\n(missing — Control should have blocked)\n")
                    .getBytes(StandardCharsets.UTF_8));
        }

        List<String> p1 = new ArrayList<String>();
        p1.add("slices/requirement.md");
        p1.add("slices/acceptance.md");
        p1.add("slices/discovery.md");

        Path gap = discoveryDir.resolve("gap.report.properties");
        if (Files.isRegularFile(gap)) {
            Files.copy(gap, slices.resolve("gap.report.properties"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            p1.add("slices/gap.report.properties");
        }
        Path clarification = discoveryDir.resolve("clarification.resolved.md");
        if (Files.isRegularFile(clarification)) {
            Files.copy(clarification, slices.resolve("clarification.resolved.md"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            p1.add("slices/clarification.resolved.md");
        }

        StringBuilder hint = new StringBuilder("# Allowed hint (optional constraint)\n\n");
        List<String> hints = new ArrayList<String>();
        if (allowedHint != null) {
            for (String f : allowedHint) {
                if (!Strings.isBlank(f)) {
                    hints.add(f.trim());
                    hint.append("- ").append(f.trim()).append('\n');
                }
            }
        }
        if (hints.isEmpty()) {
            hint.append("- (none — derive Allowed from Discovery + Acceptance)\n");
        }
        Path hintSlice = slices.resolve("allowed-hint.md");
        Files.write(hintSlice, hint.toString().getBytes(StandardCharsets.UTF_8));
        p1.add("slices/allowed-hint.md");

        List<RuleDocument> applicable = CustomerRuleLoader.loadApplicable(workspace, ROLE);
        long baseBytes = Files.size(requirementSlice) + Files.size(acceptanceSlice)
                + Files.size(discoverySlice) + Files.size(hintSlice);
        if (Files.isRegularFile(slices.resolve("gap.report.properties"))) {
            baseBytes += Files.size(slices.resolve("gap.report.properties"));
        }
        if (Files.isRegularFile(slices.resolve("clarification.resolved.md"))) {
            baseBytes += Files.size(slices.resolve("clarification.resolved.md"));
        }
        ApplicableRuleAssembler.requireFitOrRefuse(baseBytes, applicable, budget);
        List<String> ruleIds = ApplicableRuleAssembler.installIntoPackage(packageDir, applicable, p1);

        Path manifest = packageDir.resolve("manifest.md");
        StringBuilder m = new StringBuilder();
        m.append("# Context Package Manifest\n\n");
        m.append("- role: ").append(ROLE).append('\n');
        m.append("- story_id: ").append(storyId).append('\n');
        m.append("\n## priority1\n\n");
        for (String item : p1) {
            m.append("- ").append(item).append('\n');
        }
        m.append("\n## output_required\n\n");
        m.append("- .story/").append(storyId).append("/planning/plan.md\n");
        m.append("- plan must include ## Allowed Files with at least one path\n");
        m.append("- applicable_rules: ").append(ruleIds.size()).append('\n');
        Files.write(manifest, m.toString().getBytes(StandardCharsets.UTF_8));
        ModelInputEnvelope.write(
                packageDir,
                ROLE,
                storyId,
                "Create a reviewable implementation plan with Design and syntactically valid Allowed Files. "
                        + "Do not modify business source or claim verification passed.",
                p1,
                budget);

        return new ContextPackageResult(ROLE, storyId, packageDir, manifest, p1);
    }
}
