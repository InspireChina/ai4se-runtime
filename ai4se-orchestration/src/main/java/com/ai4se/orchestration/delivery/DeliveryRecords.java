package com.ai4se.orchestration.delivery;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** W8 Delivery — local Commit or awaiting; never Push. SHA observed via git. */
public final class DeliveryRecords {

    public static final String FILE = "delivery.md";

    private DeliveryRecords() {
    }

    public static Path deliveryDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("delivery");
    }

    /**
     * Stage Development changed files, {@code git commit} locally (never push), record observed SHA.
     */
    public static String commitLocalAndRecord(
            Path workspace,
            String storyId,
            String commitMessage,
            ProcessInvoker invoker) throws IOException {
        ReviewRecords.requirePresent(workspace, storyId);
        if (ReviewRecords.isRejected(workspace, storyId)) {
            throw new StageGateException("Cannot deliver after Review 驳回");
        }
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required — local commit must be executed");
        }
        if (Strings.isBlank(commitMessage)) {
            throw new StageGateException("Commit message required");
        }
        List<String> changed = DevelopmentRecords.readChangedFiles(workspace, storyId);
        if (changed.isEmpty()) {
            throw new StageGateException("Local commit requires Development changed files");
        }
        String sha = WorkspaceGit.commitLocal(workspace, invoker, changed, commitMessage.trim());
        write(workspace, storyId, "LOCAL_COMMIT", sha, false, true, true);
        return sha;
    }

    /**
     * Observe existing HEAD sha (no new commit). Prefer {@link #commitLocalAndRecord} for Delivery.
     */
    public static void recordLocalCommit(Path workspace, String storyId, ProcessInvoker invoker)
            throws IOException {
        ReviewRecords.requirePresent(workspace, storyId);
        if (ReviewRecords.isRejected(workspace, storyId)) {
            throw new StageGateException("Cannot deliver after Review 驳回");
        }
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required — Commit sha must be observed");
        }
        WorkspaceGit.requireNotPushedClean(workspace, invoker);
        String commitSha = WorkspaceGit.headSha(workspace, invoker);
        write(workspace, storyId, "LOCAL_COMMIT", commitSha, false, true, false);
    }

    /**
     * @deprecated Caller-supplied SHA is soft — use {@link #commitLocalAndRecord} or
     * {@link #recordLocalCommit(Path, String, ProcessInvoker)}.
     */
    @Deprecated
    public static void recordLocalCommit(Path workspace, String storyId, String commitSha)
            throws IOException {
        ReviewRecords.requirePresent(workspace, storyId);
        if (ReviewRecords.isRejected(workspace, storyId)) {
            throw new StageGateException("Cannot deliver after Review 驳回");
        }
        if (Strings.isBlank(commitSha)) {
            throw new StageGateException("Commit sha required (or use awaiting)");
        }
        write(workspace, storyId, "LOCAL_COMMIT", commitSha.trim(), false, false, false);
    }

    public static void recordAwaitingHumanCommit(Path workspace, String storyId) throws IOException {
        ReviewRecords.requirePresent(workspace, storyId);
        if (ReviewRecords.isRejected(workspace, storyId)) {
            throw new StageGateException("Cannot deliver after Review 驳回");
        }
        write(workspace, storyId, "AWAITING_HUMAN_COMMIT", "", false, false, false);
    }

    public static void rejectPushAttempt(Path workspace, String storyId) {
        throw new StageGateException("Push is forbidden — Delivery is local Commit / awaiting only");
    }

    public static boolean isReady(Path workspace, String storyId) throws IOException {
        Path path = deliveryDir(workspace, storyId).resolve(FILE);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return text.contains("pushed: false")
                && (text.contains("LOCAL_COMMIT") || text.contains("AWAITING_HUMAN_COMMIT"));
    }

    /** Observed SHA from an existing LOCAL_COMMIT delivery record, or null. */
    public static String readCommitShaOrNull(Path workspace, String storyId) throws IOException {
        Path path = deliveryDir(workspace, storyId).resolve(FILE);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (!text.contains("LOCAL_COMMIT")) {
            return null;
        }
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("- commit_sha:")) {
                String sha = t.substring("- commit_sha:".length()).trim();
                return Strings.isBlank(sha) ? null : sha;
            }
        }
        return null;
    }

    /** True when Delivery already recorded a local commit (resume must not commit again). */
    public static boolean hasLocalCommit(Path workspace, String storyId) throws IOException {
        return readCommitShaOrNull(workspace, storyId) != null;
    }

    public static void requireReady(Path workspace, String storyId) throws IOException {
        if (!isReady(workspace, storyId)) {
            throw new StageGateException("Delivery not ready (need local commit or awaiting; no Push)");
        }
    }

    private static void write(
            Path workspace,
            String storyId,
            String mode,
            String sha,
            boolean pushed,
            boolean observed,
            boolean committedNow) throws IOException {
        if (pushed) {
            rejectPushAttempt(workspace, storyId);
        }
        Path dir = deliveryDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Delivery\n\n"
                + "- mode: " + mode + "\n"
                + "- commit_sha: " + sha + "\n"
                + "- pushed: false\n"
                + "- observed: " + observed + "\n"
                + "- committed_now: " + committedNow + "\n"
                + "- policy: Commit 不 Push\n";
        Files.write(dir.resolve(FILE), body.getBytes(StandardCharsets.UTF_8));
    }
}
