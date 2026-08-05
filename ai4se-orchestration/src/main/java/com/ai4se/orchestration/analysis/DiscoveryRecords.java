package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Discovery artifact: either {@code discovery.report.md} or legal {@code discovery.skip.md}.
 */
public final class DiscoveryRecords {

    public static final String REPORT = "discovery.report.md";
    public static final String SKIP = "discovery.skip.md";

    private DiscoveryRecords() {
    }

    public static Path analysisDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("analysis");
    }

    public static void writeReport(Path workspace, String storyId, String factsBody) throws IOException {
        Path dir = analysisDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Discovery Report\n\n"
                + "Facts only — no implementation recommendations.\n\n"
                + (factsBody == null ? "" : factsBody.trim())
                + "\n";
        Files.write(dir.resolve(REPORT), body.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeSkip(Path workspace, String storyId, String rationale, String approver)
            throws IOException {
        if (Strings.isBlank(rationale) || Strings.isBlank(approver)) {
            throw new StageGateException("discovery.skip requires rationale and approver");
        }
        Path dir = analysisDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Discovery Skip\n\n"
                + "- rationale: " + rationale.trim() + "\n"
                + "- approver: " + approver.trim() + "\n"
                + "- at: " + Instant.now().toString() + "\n";
        Files.write(dir.resolve(SKIP), body.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasReportOrSkip(Path workspace, String storyId) {
        Path dir = analysisDir(workspace, storyId);
        return Files.isRegularFile(dir.resolve(REPORT)) || Files.isRegularFile(dir.resolve(SKIP));
    }

    public static void requireReportOrSkip(Path workspace, String storyId) {
        if (!hasReportOrSkip(workspace, storyId)) {
            throw new StageGateException(
                    "Missing discovery.report or discovery.skip under .story/" + storyId + "/analysis/");
        }
    }
}
