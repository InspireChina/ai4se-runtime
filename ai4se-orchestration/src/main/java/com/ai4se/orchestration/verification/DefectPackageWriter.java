package com.ai4se.orchestration.verification;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Structured Defect Package — FAIL 回灌 Dev 的 P1，不改业务码。 */
public final class DefectPackageWriter {

    private DefectPackageWriter() {
    }

    public static Path defectsDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("defects");
    }

    public static Path write(
            Path workspace,
            String storyId,
            int round,
            String whyFailed,
            List<String> impactedAcceptance,
            List<String> filePointers,
            String suggestedScope,
            String forbiddenScope,
            String reproducePointer) throws IOException {
        if (Strings.isBlank(whyFailed)) {
            throw new StageGateException("Defect requires structured why-failed");
        }
        if (impactedAcceptance == null || impactedAcceptance.isEmpty()) {
            throw new StageGateException("Defect must map to at least one Acceptance");
        }
        if (Strings.isBlank(reproducePointer)) {
            throw new StageGateException("Defect requires reproduce pointer (not log flood)");
        }
        Path dir = defectsDir(workspace, storyId);
        Files.createDirectories(dir);
        Path path = dir.resolve("defect-round-" + round + ".md");
        StringBuilder sb = new StringBuilder();
        sb.append("# Defect Package\n\n");
        sb.append("- round: ").append(round).append('\n');
        sb.append("- why_failed: ").append(whyFailed.trim()).append('\n');
        sb.append("\n## Impacted Acceptance\n\n");
        for (String ac : impactedAcceptance) {
            sb.append("- ").append(ac).append('\n');
        }
        sb.append("\n## File Pointers\n\n");
        if (filePointers != null) {
            for (String f : filePointers) {
                sb.append("- ").append(f).append('\n');
            }
        }
        sb.append("\n## Suggested Scope\n\n");
        sb.append(Strings.isBlank(suggestedScope) ? "(keep Allowed)\n" : suggestedScope.trim() + "\n");
        sb.append("\n## Forbidden Scope\n\n");
        sb.append(Strings.isBlank(forbiddenScope) ? "(do not expand casually)\n" : forbiddenScope.trim() + "\n");
        sb.append("\n## Reproduce Pointer\n\n");
        sb.append(reproducePointer.trim()).append('\n');
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
        return path;
    }

    public static boolean hasAny(Path workspace, String storyId) {
        Path dir = defectsDir(workspace, storyId);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try {
            for (Path p : Files.newDirectoryStream(dir, "defect-round-*.md")) {
                if (Files.isRegularFile(p)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static Path latest(Path workspace, String storyId) throws IOException {
        Path dir = defectsDir(workspace, storyId);
        Path latest = null;
        int max = -1;
        if (!Files.isDirectory(dir)) {
            return null;
        }
        for (Path p : Files.newDirectoryStream(dir, "defect-round-*.md")) {
            String name = p.getFileName().toString();
            // defect-round-N.md
            String num = name.replace("defect-round-", "").replace(".md", "");
            try {
                int n = Integer.parseInt(num);
                if (n > max) {
                    max = n;
                    latest = p;
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return latest;
    }
}
