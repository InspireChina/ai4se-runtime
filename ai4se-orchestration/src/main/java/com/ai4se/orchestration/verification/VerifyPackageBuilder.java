package com.ai4se.orchestration.verification;

import com.ai4se.context.compress.CompressionRetention;
import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds / reads Verify Context Packages under packages/verification/round-N/.
 * P1 must embed Acceptance + Diff content (pointers-only = bare verify, refused).
 */
public final class VerifyPackageBuilder {

    private VerifyPackageBuilder() {
    }

    public static Path packageDir(Path workspace, String storyId, int round) {
        return workspace.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("verification").resolve("round-" + round);
    }

    public static int nextRound(Path workspace, String storyId) throws IOException {
        Path root = workspace.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("verification");
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

    public static Path build(
            Path workspace,
            String storyId,
            int round,
            String command,
            Path defectPointerOrNull) throws IOException {
        if (Strings.isBlank(command)) {
            throw new StageGateException("Verify Package requires command");
        }
        StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
        List<String> acceptance = requirement.acceptance();
        if (acceptance.isEmpty()) {
            throw new StageGateException("Verify Package P1 requires Acceptance — refuse bare verify");
        }

        Path dir = packageDir(workspace, storyId, round);
        Files.createDirectories(dir.resolve("slices"));

        StringBuilder acBody = new StringBuilder("# Acceptance (embedded P1)\n\n");
        for (String item : acceptance) {
            acBody.append("- ").append(item).append('\n');
        }
        Files.write(dir.resolve("slices/acceptance.md"), acBody.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder diffBody = new StringBuilder("# Workspace Diff / changed files (embedded P1)\n\n");
        Path changed = workspace.resolve(".story").resolve(storyId)
                .resolve("development").resolve(DevelopmentRecords.CHANGED_FILES);
        if (Files.isRegularFile(changed)) {
            diffBody.append(new String(Files.readAllBytes(changed), StandardCharsets.UTF_8).trim());
            diffBody.append('\n');
        } else {
            throw new StageGateException(
                    "Verify Package P1 requires development/changed-files.md — refuse empty diff");
        }
        Files.write(dir.resolve("slices/diff.md"), diffBody.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder entryBody = new StringBuilder();
        entryBody.append("# Test entry (from repository entries)\n\n");
        entryBody.append("- command: ").append(command.trim()).append('\n');
        entryBody.append("- story_id: ").append(storyId).append('\n');
        entryBody.append("- goal: ").append(nullToEmpty(requirement.goal())).append('\n');
        Files.write(dir.resolve("slices/entry.md"), entryBody.toString().getBytes(StandardCharsets.UTF_8));

        String defectLine = defectPointerOrNull == null
                ? "- (none)\n"
                : "- " + defectPointerOrNull.toString() + "\n";
        if (defectPointerOrNull != null) {
            String defectBody = "# Prior Defect pointer\n\n- " + defectPointerOrNull + "\n";
            if (Files.isRegularFile(defectPointerOrNull)) {
                defectBody += "\n## Defect body\n\n"
                        + new String(Files.readAllBytes(defectPointerOrNull), StandardCharsets.UTF_8);
            }
            Files.write(dir.resolve("slices/defect.md"), defectBody.getBytes(StandardCharsets.UTF_8));
        }

        String manifest = ""
                + "# Context Package Manifest\n\n"
                + "- role: Verification\n"
                + "- story_id: " + storyId + "\n"
                + "- round: " + round + "\n"
                + "- command: " + command.trim() + "\n"
                + "- p1_embedded: true\n\n"
                + "## priority1\n\n"
                + "- slices/acceptance.md\n"
                + "- slices/diff.md\n"
                + "- slices/entry.md\n"
                + (defectPointerOrNull == null ? "" : "- slices/defect.md\n")
                + "\n## defects\n\n"
                + defectLine
                + "\n## forbidden_filtered\n\n"
                + "- whole repository tree\n"
                + "- empty bare verify\n"
                + "- acceptance pointer-only stubs\n"
                + "- chat_transcript\n";
        Files.write(dir.resolve("manifest.md"), manifest.getBytes(StandardCharsets.UTF_8));
        CompressionRetention.requireRetainedInManifest(
                "Verification", manifest, defectPointerOrNull != null);
        List<String> retained = new ArrayList<String>(Arrays.asList(
                "acceptance", "conclusions", "unknown", "knowledge_ids"));
        if (defectPointerOrNull != null) {
            retained.add("defect");
        }
        CompressionRetention.recordRebuild(
                workspace, storyId, "Verification", dir, retained, CompressionRetention.MUST_DISCARD);
        return dir;
    }

    public static List<Path> listRoundManifests(Path workspace, String storyId) throws IOException {
        Path root = workspace.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("verification");
        List<Path> out = new ArrayList<Path>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        for (Path p : Files.newDirectoryStream(root)) {
            Path m = p.resolve("manifest.md");
            if (Files.isRegularFile(m)) {
                out.add(m);
            }
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
