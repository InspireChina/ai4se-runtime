package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Clarification gate under {@code .story/<id>/analysis/}.
 * <ul>
 *   <li>{@code clarification.pending.md} — Gap BLOCKED，等人答（合法 Stop）</li>
 *   <li>{@code clarification.resolved.md} — 已答，可进 Plan（非 Stop）</li>
 * </ul>
 */
public final class ClarificationRecords {

    public static final String RESOLVED_FILE = "clarification.resolved.md";
    public static final String PENDING_FILE = "clarification.pending.md";

    /** @deprecated use {@link #RESOLVED_FILE} */
    public static final String FILE = RESOLVED_FILE;

    private ClarificationRecords() {
    }

    public static void writePending(
            Path workspace, String storyId, String question, String gapSummary) throws IOException {
        if (Strings.isBlank(question)) {
            throw new StageGateException("clarification.pending requires question");
        }
        Path dir = DiscoveryRecords.analysisDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Clarification Pending\n\n"
                + "- status: AWAITING_HUMAN\n"
                + "- at: " + Instant.now().toString() + "\n"
                + "- gap: " + (Strings.isBlank(gapSummary) ? "BLOCKED" : gapSummary.trim()) + "\n\n"
                + "## Question\n\n"
                + question.trim()
                + "\n\n"
                + "Story STOPPED until clarification.resolved.md is written.\n";
        Files.write(dir.resolve(PENDING_FILE), body.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeResolved(
            Path workspace, String storyId, String question, String answer, String resolver)
            throws IOException {
        if (Strings.isBlank(question) || Strings.isBlank(answer)) {
            throw new StageGateException("clarification.resolved requires question and answer");
        }
        Path dir = DiscoveryRecords.analysisDir(workspace, storyId);
        Files.createDirectories(dir);
        String who = Strings.isBlank(resolver) ? "human" : resolver.trim();
        String body = ""
                + "# Clarification Resolved\n\n"
                + "- status: RESOLVED_BEFORE_PLAN\n"
                + "- resolver: " + who + "\n"
                + "- at: " + Instant.now().toString() + "\n\n"
                + "## Question\n\n"
                + question.trim()
                + "\n\n## Answer\n\n"
                + answer.trim()
                + "\n";
        Files.write(dir.resolve(RESOLVED_FILE), body.getBytes(StandardCharsets.UTF_8));
        Path pending = dir.resolve(PENDING_FILE);
        if (Files.isRegularFile(pending)) {
            Files.delete(pending);
        }
    }

    public static boolean hasResolved(Path workspace, String storyId) {
        return Files.isRegularFile(
                DiscoveryRecords.analysisDir(workspace, storyId).resolve(RESOLVED_FILE));
    }

    public static boolean hasPending(Path workspace, String storyId) {
        return Files.isRegularFile(
                DiscoveryRecords.analysisDir(workspace, storyId).resolve(PENDING_FILE));
    }
}
