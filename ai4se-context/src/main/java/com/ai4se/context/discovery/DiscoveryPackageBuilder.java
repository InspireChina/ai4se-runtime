package com.ai4se.context.discovery;

import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.packagebuild.ModelInputEnvelope;
import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.context.packagebuild.PackageRefuseException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the bounded, read-first package for semantic repository Discovery.
 *
 * <p>Onboarding remains deterministic and non-AI. Discovery receives its facts as P1 and may
 * write only a candidate knowledge directory; it never has authority to change customer source or
 * promote knowledge to verified.
 */
public final class DiscoveryPackageBuilder {

    public static final String ROLE = "Discovery";
    public static final String ROOT = ".ai4se/knowledge-candidates";
    public static final String MANIFEST = "candidate.yaml";
    public static final String DOCUMENTS = "documents";

    private DiscoveryPackageBuilder() {
    }

    public static Path candidateRoot(Path workspace, String candidateId) {
        return workspace.resolve(ROOT).resolve(normalizeCandidateId(candidateId));
    }

    public static ContextPackageResult build(
            Path workspace,
            String candidateId,
            String scope,
            String sourceCommit,
            PackageBudget budget) throws IOException {
        if (workspace == null || Strings.isBlank(candidateId) || Strings.isBlank(scope)
                || Strings.isBlank(sourceCommit)) {
            throw new PackageRefuseException(
                    "Discovery requires workspace, candidateId, scope and observed sourceCommit");
        }
        Path repository = workspace.resolve(".ai4se/repository");
        if (!Files.isDirectory(repository)) {
            throw new PackageRefuseException("Discovery requires onboarded .ai4se/repository facts");
        }
        Path root = candidateRoot(workspace, candidateId);
        if (Files.exists(root)) {
            throw new PackageRefuseException(
                    "Discovery candidate already exists — review/approve it or use a new candidate: "
                            + root);
        }
        Path packageDir = root.resolve("package");
        Path slices = packageDir.resolve("slices");
        Files.createDirectories(slices);
        Files.createDirectories(root.resolve(DOCUMENTS));

        List<String> p1 = new ArrayList<String>();
        copyRequired(repository.resolve("facts.md"), slices.resolve("repository-facts.md"), p1,
                "slices/repository-facts.md");
        copyRequired(repository.resolve("module-map.md"), slices.resolve("module-map.md"), p1,
                "slices/module-map.md");
        copyIfPresent(repository.resolve("baseline.md"), slices.resolve("baseline.md"), p1,
                "slices/baseline.md");
        copyIfPresent(repository.resolve("entries.yaml"), slices.resolve("entries.yaml"), p1,
                "slices/entries.yaml");
        copyIfPresent(repository.resolve("source-inventory.yaml"), slices.resolve("source-inventory.yaml"), p1,
                "slices/source-inventory.yaml");
        copyIfPresent(workspace.resolve(".ai4se/index/repository-graph.yaml"),
                slices.resolve("repository-graph.yaml"), p1, "slices/repository-graph.yaml");

        String seed = ""
                + "candidate_id=" + normalizeCandidateId(candidateId) + "\n"
                + "scope=" + scope.trim() + "\n"
                + "source_commit=" + sourceCommit.trim() + "\n"
                + "candidate_root=" + ROOT + "/" + normalizeCandidateId(candidateId) + "\n"
                + "write_policy=candidate_directory_only\n";
        Files.write(slices.resolve("discovery-seed.properties"), seed.getBytes(StandardCharsets.UTF_8));
        p1.add("slices/discovery-seed.properties");

        StringBuilder manifest = new StringBuilder("# Context Package Manifest\n\n");
        manifest.append("- role: ").append(ROLE).append('\n');
        manifest.append("- story_id: discovery-").append(normalizeCandidateId(candidateId)).append('\n');
        manifest.append("- candidate_root: ").append(root.toAbsolutePath()).append("\n\n");
        manifest.append("## priority1\n\n");
        for (String item : p1) {
            manifest.append("- ").append(item).append('\n');
        }
        manifest.append("\n## output_required\n\n")
                .append("- ").append(ROOT).append('/').append(normalizeCandidateId(candidateId))
                .append('/').append(MANIFEST).append("\n")
                .append("- ").append(ROOT).append('/').append(normalizeCandidateId(candidateId))
                .append('/').append(DOCUMENTS).append("/<document-id>.md\n");
        Path manifestPath = packageDir.resolve("manifest.md");
        Files.write(manifestPath, manifest.toString().getBytes(StandardCharsets.UTF_8));
        ModelInputEnvelope.write(
                packageDir,
                ROLE,
                "discovery-" + normalizeCandidateId(candidateId),
                "Build a candidate repository knowledge set for the declared scope. Every conclusion "
                        + "must cite existing source paths; write uncertainty under Unknowns. Do not modify "
                        + "business source, .story, existing knowledge or index files.",
                p1,
                budget == null ? PackageBudget.PRODUCTION_P1 : budget);
        return new ContextPackageResult(ROLE, "discovery-" + normalizeCandidateId(candidateId),
                packageDir, manifestPath, p1);
    }

    public static String normalizeCandidateId(String value) {
        if (Strings.isBlank(value)) {
            throw new IllegalArgumentException("candidate id required");
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty() || normalized.contains("..")) {
            throw new IllegalArgumentException("invalid candidate id: " + value);
        }
        return normalized;
    }

    private static void copyRequired(Path source, Path target, List<String> p1, String rel)
            throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new PackageRefuseException("Discovery required onboarding fact missing: " + source);
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        p1.add(rel);
    }

    private static void copyIfPresent(Path source, Path target, List<String> p1, String rel)
            throws IOException {
        if (Files.isRegularFile(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            p1.add(rel);
        }
    }
}
