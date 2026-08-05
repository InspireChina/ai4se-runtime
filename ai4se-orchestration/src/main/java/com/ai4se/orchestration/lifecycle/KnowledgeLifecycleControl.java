package com.ai4se.orchestration.lifecycle;

import com.ai4se.orchestration.acceptance.HumanAcceptanceRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;

/**
 * W10 · S6 Knowledge Lifecycle (minimal) — manage customer-repo knowledge only after human ACCEPTED.
 * Success: index/file change <b>or</b> explicit {@code lifecycle: noop} with reason.
 * Does not Repo Scan; does not invent business insights as Facts.
 */
public final class KnowledgeLifecycleControl {

    public static final String DIR = "lifecycle";
    public static final String NOOP_FILE = "noop.md";
    public static final String APPLIED_FILE = "applied.md";

    private KnowledgeLifecycleControl() {
    }

    public static Path lifecycleDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(DIR);
    }

    /** True if any lifecycle artifact already exists for this Story. */
    public static boolean hasLifecycleArtifact(Path workspace, String storyId) {
        Path dir = lifecycleDir(workspace, storyId);
        return Files.isRegularFile(dir.resolve(NOOP_FILE))
                || Files.isRegularFile(dir.resolve(APPLIED_FILE));
    }

    /**
     * W10 gate: refuse to write 06 before human ACCEPTED.
     * Also used as negative必验 — calling apply/noop without acceptance throws.
     */
    public static void requireMayRun(Path workspace, String storyId) throws IOException {
        HumanAcceptanceRecords.requireAccepted(workspace, storyId);
    }

    /**
     * Explicit noop — no Knowledge/Learning change; reason required.
     */
    public static Path noop(Path workspace, String storyId, String reason) throws IOException {
        requireMayRun(workspace, storyId);
        if (Strings.isBlank(reason)) {
            throw new StageGateException("lifecycle noop requires reason");
        }
        Path dir = lifecycleDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Knowledge Lifecycle\n\n"
                + "- lifecycle: noop\n"
                + "- story_id: " + storyId + "\n"
                + "- reason: " + reason.trim() + "\n"
                + "- at: " + Instant.now() + "\n"
                + "- repo_scan: false\n"
                + "- host: customer-repo only\n";
        Path path = dir.resolve(NOOP_FILE);
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    /**
     * Minimal write-back: append a Learning event under {@code .ai4se/learning/} and index pointer.
     * Not AI insight-as-Facts; caller supplies the learning text (human or audited draft).
     */
    public static Path applyLearning(
            Path workspace, String storyId, String learningId, String learningBody)
            throws IOException {
        requireMayRun(workspace, storyId);
        if (Strings.isBlank(learningId) || Strings.isBlank(learningBody)) {
            throw new StageGateException("applyLearning requires learningId and body");
        }
        String id = learningId.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        Path learningDir = workspace.resolve(".ai4se").resolve("learning");
        Files.createDirectories(learningDir);
        Path learningFile = learningDir.resolve(id + ".md");
        String fileBody = ""
                + "# Learning: " + id + "\n\n"
                + "- story_id: " + storyId + "\n"
                + "- at: " + Instant.now() + "\n\n"
                + learningBody.trim() + "\n";
        Files.write(learningFile, fileBody.getBytes(StandardCharsets.UTF_8));

        Path index = workspace.resolve(".ai4se").resolve("index").resolve("knowledge.yaml");
        if (!Files.isRegularFile(index)) {
            throw new StageGateException("Missing .ai4se/index/knowledge.yaml — onboard slots first");
        }
        String entry = ""
                + "- id: " + id + "\n"
                + "  path: .ai4se/learning/" + id + ".md\n"
                + "  kind: learning\n"
                + "  tags: [story-" + storyId + "]\n"
                + "  refs: [.story/" + storyId + "]\n"
                + "  status: active\n";
        Files.write(index, entry.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

        Path dir = lifecycleDir(workspace, storyId);
        Files.createDirectories(dir);
        String applied = ""
                + "# Knowledge Lifecycle\n\n"
                + "- lifecycle: applied\n"
                + "- story_id: " + storyId + "\n"
                + "- learning_id: " + id + "\n"
                + "- path: .ai4se/learning/" + id + ".md\n"
                + "- index: .ai4se/index/knowledge.yaml\n"
                + "- at: " + Instant.now() + "\n"
                + "- repo_scan: false\n"
                + "- host: customer-repo only\n";
        Path path = dir.resolve(APPLIED_FILE);
        Files.write(path, applied.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    public static void requireCompleted(Path workspace, String storyId) throws IOException {
        if (!hasLifecycleArtifact(workspace, storyId)) {
            throw new StageGateException(
                    "Knowledge Lifecycle missing — need applied change or lifecycle: noop");
        }
    }
}
