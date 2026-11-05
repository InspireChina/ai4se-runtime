package com.ai4se.orchestration.acceptance;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * W10 · S5 人验收 — after Delivery COMPLETED; gates Knowledge Lifecycle (06).
 * Not a WorkflowStage; Control records human decision under {@code .story/<id>/acceptance/}.
 */
public final class HumanAcceptanceRecords {

    public static final String DIR = "acceptance";
    public static final String FILE = "human-acceptance.md";

    public enum Status {
        ACCEPTED,
        REJECTED
    }

    private HumanAcceptanceRecords() {
    }

    public static Path acceptanceDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(DIR);
    }

    public static Path acceptanceFile(Path workspace, String storyId) {
        return acceptanceDir(workspace, storyId).resolve(FILE);
    }

    public enum Kind {
        /** Unmanned / IT fixture — not a live human gate. */
        FIXTURE,
        /** Recorded after a real human decision. */
        HUMAN,
        /**
         * Explicit deferred human gate — Learning may run only with disclosed reason in note.
         * Not a silent stand-in for HUMAN.
         */
        DEFERRED
    }

    public static void recordAccepted(Path workspace, String storyId, String accepter, String note)
            throws IOException {
        write(workspace, storyId, Status.ACCEPTED, accepter, note, Kind.FIXTURE);
    }

    public static void recordAccepted(
            Path workspace, String storyId, String accepter, String note, Kind kind)
            throws IOException {
        write(workspace, storyId, Status.ACCEPTED, accepter, note, kind == null ? Kind.FIXTURE : kind);
    }

    public static void recordRejected(Path workspace, String storyId, String accepter, String note)
            throws IOException {
        write(workspace, storyId, Status.REJECTED, accepter, note, Kind.HUMAN);
    }

    public static boolean isAccepted(Path workspace, String storyId) throws IOException {
        Path path = acceptanceFile(workspace, storyId);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return text.contains("status: ACCEPTED");
    }

    public static boolean hasRecord(Path workspace, String storyId) {
        return Files.isRegularFile(acceptanceFile(workspace, storyId));
    }

    public static void requireAccepted(Path workspace, String storyId) throws IOException {
        if (!isAccepted(workspace, storyId)) {
            throw new StageGateException(
                    "Human acceptance ACCEPTED required before Knowledge Lifecycle (06)");
        }
    }

    public static Kind readKind(Path workspace, String storyId) throws IOException {
        Path path = acceptanceFile(workspace, storyId);
        if (!Files.isRegularFile(path)) {
            throw new StageGateException("Human acceptance record missing");
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.startsWith("- kind:") || t.startsWith("kind:")) {
                int i = t.indexOf(':');
                String raw = t.substring(i + 1).trim();
                return Kind.valueOf(raw.toUpperCase());
            }
        }
        return Kind.FIXTURE;
    }

    /**
     * Learning write-back refuses silent fixture acceptance.
     * HUMAN or explicit DEFERRED (with non-blank note) may proceed.
     */
    public static void requireMayApplyLearning(Path workspace, String storyId) throws IOException {
        requireAccepted(workspace, storyId);
        Kind kind = readKind(workspace, storyId);
        if (kind == Kind.HUMAN) {
            return;
        }
        if (kind == Kind.DEFERRED) {
            String text = new String(Files.readAllBytes(acceptanceFile(workspace, storyId)), StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.startsWith("- note:")) {
                    String note = t.substring("- note:".length()).trim();
                    if (!Strings.isBlank(note)) {
                        return;
                    }
                }
            }
            throw new StageGateException(
                    "DEFERRED acceptance requires disclosed note before APPLY_LEARNING");
        }
        throw new StageGateException(
                "APPLY_LEARNING refuses fixture acceptance — use HUMAN or DEFERRED with disclosed note");
    }

    private static void write(
            Path workspace,
            String storyId,
            Status status,
            String accepter,
            String note,
            Kind kind) throws IOException {
        if (Strings.isBlank(accepter)) {
            throw new StageGateException("Human acceptance requires accepter");
        }
        StoryWorkflowState state = StoryWorkflowMachine.load(workspace, storyId);
        if (state.status() != WorkflowStatus.COMPLETED) {
            throw new StageGateException(
                    "Human acceptance only after Delivery COMPLETED, was " + state.status());
        }
        DeliveryRecords.requireReady(workspace, storyId);

        Path dir = acceptanceDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Human Acceptance\n\n"
                + "- status: " + status.name() + "\n"
                + "- kind: " + kind.name() + "\n"
                + "- accepter: " + accepter.trim() + "\n"
                + "- note: " + (note == null ? "" : note.trim()) + "\n"
                + "- at: " + Instant.now() + "\n"
                + "- gate: 06 Knowledge Lifecycle must not run before ACCEPTED\n";
        Files.write(dir.resolve(FILE), body.getBytes(StandardCharsets.UTF_8));
    }
}
