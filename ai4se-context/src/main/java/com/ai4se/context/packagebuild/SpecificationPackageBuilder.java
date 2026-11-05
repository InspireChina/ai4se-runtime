package com.ai4se.context.packagebuild;

import com.ai4se.context.story.RequirementAttachmentSlot;
import com.ai4se.context.story.SpecificationClarification;
import com.ai4se.context.story.StoryIntake;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Builds the pre-development package that converts raw customer input into a candidate spec. */
public final class SpecificationPackageBuilder {

    public static final String ROLE = "Specification";

    private SpecificationPackageBuilder() {
    }

    public static ContextPackageResult build(Path workspace, String storyId, PackageBudget budget)
            throws IOException {
        Path input = StoryIntake.inputDir(workspace, storyId);
        Path raw = input.resolve(StoryIntake.RAW_REQUEST);
        Path intake = input.resolve(StoryIntake.INTAKE_PROPERTIES);
        if (!Files.isRegularFile(raw) || !Files.isRegularFile(intake)) {
            throw new PackageRefuseException(
                    "Specification requires frozen raw input; run intake first for story " + storyId);
        }
        Path packageDir = workspace.resolve(".story").resolve(storyId).resolve("packages")
                .resolve("specification");
        Path slices = packageDir.resolve("slices");
        Files.createDirectories(slices);
        List<String> p1 = new ArrayList<String>();
        copy(raw, slices.resolve("raw-request.md"), p1, "slices/raw-request.md");
        copy(intake, slices.resolve("intake.properties"), p1, "slices/intake.properties");
        Path attachments = input.resolve(StoryIntake.ATTACHMENTS_PROPERTIES);
        if (Files.isRegularFile(attachments)) {
            copy(attachments, slices.resolve("attachments.properties"), p1, "slices/attachments.properties");
        }
        Path resolved = SpecificationClarification.resolvedPath(workspace, storyId);
        if (Files.isRegularFile(resolved)) {
            copy(resolved, slices.resolve("clarification.resolved.md"), p1,
                    "slices/clarification.resolved.md");
        }
        addRepositoryFact(workspace, slices, p1, "facts.md", "repository-facts.md");
        addRepositoryFact(workspace, slices, p1, "module-map.md", "module-map.md");
        addRepositoryFact(workspace, slices, p1, "baseline.md", "baseline.md");
        if (!RequirementAttachmentSlot.listPresent(workspace, storyId).isEmpty()) {
            String index = RequirementAttachmentSlot.renderIndexMarkdown(workspace, storyId);
            Files.write(slices.resolve("attachments-index.md"), index.getBytes(StandardCharsets.UTF_8));
            p1.add("slices/attachments-index.md");
        }

        StringBuilder manifest = new StringBuilder("# Context Package Manifest\n\n");
        manifest.append("- role: ").append(ROLE).append('\n');
        manifest.append("- story_id: ").append(storyId).append("\n\n## priority1\n\n");
        for (String item : p1) {
            manifest.append("- ").append(item).append('\n');
        }
        manifest.append("\n## output_required\n\n")
                .append("- .story/").append(storyId).append("/specification/specification.result.properties\n")
                .append("- candidate: .story/").append(storyId).append("/specification/candidate-requirement.md\n")
                .append("- questions: .story/").append(storyId).append("/specification/clarification.questions.md\n");
        Path manifestPath = packageDir.resolve("manifest.md");
        Files.write(manifestPath, manifest.toString().getBytes(StandardCharsets.UTF_8));
        ModelInputEnvelope.write(packageDir, ROLE, storyId,
                "Turn raw customer input into either a candidate requirement or a concise, answerable "
                        + "clarification request. Do not modify business source and do not approve your own spec.",
                p1, budget == null ? PackageBudget.PRODUCTION_P1 : budget);
        return new ContextPackageResult(ROLE, storyId, packageDir, manifestPath, p1);
    }

    private static void copy(Path source, Path target, List<String> p1, String rel) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        p1.add(rel);
    }

    private static void addRepositoryFact(
            Path workspace, Path slices, List<String> p1, String sourceName, String sliceName)
            throws IOException {
        Path source = workspace.resolve(".ai4se/repository").resolve(sourceName);
        if (Files.isRegularFile(source)) {
            copy(source, slices.resolve(sliceName), p1, "slices/" + sliceName);
        }
    }
}
