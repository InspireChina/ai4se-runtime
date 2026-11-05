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
    /** Analysis-authored question set, retained as the human-facing source of truth. */
    public static final String QUESTIONS_FILE = "clarification.questions.md";

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

    /**
     * Turns the Analysis-authored question set into a durable human interrupt.
     * The control plane never invents a question: a BLOCKED adapter result must supply one.
     */
    public static void openPendingFromQuestions(Path workspace, String storyId, String gapSummary)
            throws IOException {
        Path dir = DiscoveryRecords.analysisDir(workspace, storyId);
        Path questions = dir.resolve(QUESTIONS_FILE);
        if (!Files.isRegularFile(questions)) {
            throw new StageGateException(
                    "BLOCKED Analysis must write " + QUESTIONS_FILE + " with concrete questions before stop");
        }
        String questionText = new String(Files.readAllBytes(questions), StandardCharsets.UTF_8).trim();
        if (questionText.length() < 16 || !questionText.contains("##")) {
            throw new StageGateException(
                    QUESTIONS_FILE + " must contain a non-trivial Markdown question section");
        }
        writePending(workspace, storyId, questionText, gapSummary);
        Files.deleteIfExists(dir.resolve(RESOLVED_FILE));
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

    /** Records a human answer against the exact frozen Analysis question set. */
    public static void writeResolvedAnswer(
            Path workspace, String storyId, String answer, String resolver) throws IOException {
        if (Strings.isBlank(answer)) {
            throw new StageGateException("clarification answer required");
        }
        Path questions = DiscoveryRecords.analysisDir(workspace, storyId).resolve(QUESTIONS_FILE);
        if (!Files.isRegularFile(questions)) {
            throw new StageGateException("Missing " + QUESTIONS_FILE + " for story " + storyId);
        }
        String questionText = new String(Files.readAllBytes(questions), StandardCharsets.UTF_8).trim();
        if (questionText.length() < 16) {
            throw new StageGateException(QUESTIONS_FILE + " is empty");
        }
        writeResolved(workspace, storyId, questionText, answer, resolver);
    }

    public static boolean hasResolved(Path workspace, String storyId) {
        return Files.isRegularFile(
                DiscoveryRecords.analysisDir(workspace, storyId).resolve(RESOLVED_FILE));
    }

    public static boolean hasPending(Path workspace, String storyId) {
        return Files.isRegularFile(
                DiscoveryRecords.analysisDir(workspace, storyId).resolve(PENDING_FILE));
    }

    public static boolean hasQuestions(Path workspace, String storyId) {
        return Files.isRegularFile(
                DiscoveryRecords.analysisDir(workspace, storyId).resolve(QUESTIONS_FILE));
    }
}
