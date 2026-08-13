package com.ai4se.orchestration.delivery;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.runtime.common.util.Strings;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** W8 Delivery — local Commit or awaiting; never Push. SHA observed via git. */
public final class DeliveryRecords {

    public static final String FILE = "delivery.md";
    /** Persisted before {@code git commit}; required for safe orphan recovery. */
    public static final String INTENT_FILE = "commit-intent.properties";

    private DeliveryRecords() {
    }

    public static Path deliveryDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("delivery");
    }

    /**
     * Stage Development changed files, {@code git commit} locally (never push), record observed SHA.
     *
     * <p>Writes a commit intent (baseline HEAD, message, paths) before committing. If a prior
     * process committed successfully but crashed before writing {@code delivery.md}, recovers
     * only when HEAD advanced vs the intent baseline, subject matches, and intent paths are not
     * dirty.
     */
    public static String commitLocalAndRecord(
            Path workspace,
            String storyId,
            String commitMessage,
            ProcessInvoker invoker) throws IOException {
        ReviewRecords.requirePresent(workspace, storyId);
        if (!ReviewRecords.allowsAutomaticDelivery(workspace, storyId)) {
            throw new StageGateException(
                    "Cannot deliver after Review "
                            + ReviewRecords.readDecision(workspace, storyId)
                            + " — only PASS allows automatic Delivery");
        }
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required — local commit must be executed");
        }
        if (Strings.isBlank(commitMessage)) {
            throw new StageGateException("Commit message required");
        }
        String existing = readCommitShaOrNull(workspace, storyId);
        if (existing != null) {
            return existing;
        }
        List<String> changed = DevelopmentRecords.readChangedFiles(workspace, storyId);
        if (changed.isEmpty()) {
            throw new StageGateException("Local commit requires Development changed files");
        }
        String message = commitMessage.trim();

        // Prefer recovering a prior successful commit before writing a fresh intent.
        String recoveredEarly = recoverOrphanLocalCommit(workspace, storyId, message, invoker);
        if (recoveredEarly != null) {
            return recoveredEarly;
        }

        String headBefore = WorkspaceGit.headSha(workspace, invoker);
        writeCommitIntent(workspace, storyId, headBefore, message, changed);
        try {
            String sha = WorkspaceGit.commitLocal(workspace, invoker, changed, message);
            write(workspace, storyId, "LOCAL_COMMIT", sha, false, true, true);
            clearCommitIntent(workspace, storyId);
            return sha;
        } catch (StageGateException e) {
            String recovered = recoverOrphanLocalCommit(workspace, storyId, message, invoker);
            if (recovered != null) {
                return recovered;
            }
            throw e;
        }
    }

    /**
     * Recover orphan local commit using persisted intent. Never recovers from HEAD subject alone.
     *
     * @return commit SHA when recovered; null when recovery is unsafe or inapplicable
     */
    public static String recoverOrphanLocalCommit(
            Path workspace,
            String storyId,
            String commitMessage,
            ProcessInvoker invoker) throws IOException {
        CommitIntent intent = readCommitIntentOrNull(workspace, storyId);
        if (intent == null) {
            return null;
        }
        if (!commitMessage.trim().equals(intent.message)) {
            return null;
        }
        String head;
        String subject;
        try {
            head = WorkspaceGit.headSha(workspace, invoker);
            subject = WorkspaceGit.headCommitSubject(workspace, invoker);
        } catch (StageGateException e) {
            return null;
        }
        if (head.equalsIgnoreCase(intent.headBefore)) {
            // Commit did not land (hook/timeout/failure) — do not treat old HEAD as success.
            return null;
        }
        if (!intent.message.equals(subject)) {
            return null;
        }
        List<String> dirty = WorkspaceGit.businessChangedPaths(workspace, invoker);
        if (intersects(dirty, intent.paths)) {
            // Expected business paths still dirty — refuse recovery.
            return null;
        }
        write(workspace, storyId, "LOCAL_COMMIT", head, false, true, false);
        clearCommitIntent(workspace, storyId);
        return head;
    }

    /**
     * Persist commit intent before {@code git commit}. Visible for crash-simulation tests.
     */
    public static void writeCommitIntent(
            Path workspace,
            String storyId,
            String headBefore,
            String message,
            List<String> paths) throws IOException {
        Path dir = deliveryDir(workspace, storyId);
        Files.createDirectories(dir);
        Properties p = new Properties();
        p.setProperty("head_before", headBefore == null ? "" : headBefore.trim().toLowerCase(Locale.ROOT));
        p.setProperty("message", message == null ? "" : message.trim());
        p.setProperty("paths", joinPaths(paths));
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(dir.resolve(INTENT_FILE)), StandardCharsets.UTF_8))) {
            p.store(w, "ai4se delivery commit intent");
        }
    }

    static void clearCommitIntent(Path workspace, String storyId) throws IOException {
        Path intent = deliveryDir(workspace, storyId).resolve(INTENT_FILE);
        Files.deleteIfExists(intent);
    }

    static CommitIntent readCommitIntentOrNull(Path workspace, String storyId) throws IOException {
        Path intent = deliveryDir(workspace, storyId).resolve(INTENT_FILE);
        if (!Files.isRegularFile(intent)) {
            return null;
        }
        Properties p = new Properties();
        p.load(Files.newInputStream(intent));
        String head = p.getProperty("head_before", "").trim();
        String message = p.getProperty("message", "").trim();
        String pathsRaw = p.getProperty("paths", "");
        if (Strings.isBlank(head) || Strings.isBlank(message)) {
            return null;
        }
        List<String> paths = splitPaths(pathsRaw);
        return new CommitIntent(head.toLowerCase(Locale.ROOT), message, paths);
    }

    private static boolean intersects(List<String> dirty, List<String> intentPaths) {
        if (dirty == null || dirty.isEmpty() || intentPaths == null || intentPaths.isEmpty()) {
            return false;
        }
        Set<String> dirtySet = new LinkedHashSet<String>();
        for (String d : dirty) {
            dirtySet.add(normalizePath(d));
        }
        for (String path : intentPaths) {
            if (dirtySet.contains(normalizePath(path))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/').trim();
    }

    private static String joinPaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(normalizePath(paths.get(i)));
        }
        return sb.toString();
    }

    private static List<String> splitPaths(String raw) {
        if (Strings.isBlank(raw)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String part : raw.split(",")) {
            String t = normalizePath(part);
            if (!Strings.isBlank(t)) {
                out.add(t);
            }
        }
        return Collections.unmodifiableList(out);
    }

    static final class CommitIntent {
        final String headBefore;
        final String message;
        final List<String> paths;

        CommitIntent(String headBefore, String message, List<String> paths) {
            this.headBefore = headBefore;
            this.message = message;
            this.paths = paths;
        }
    }

    /**
     * Observe existing HEAD sha (no new commit). Prefer {@link #commitLocalAndRecord} for Delivery.
     */
    public static void recordLocalCommit(Path workspace, String storyId, ProcessInvoker invoker)
            throws IOException {
        ReviewRecords.requirePresent(workspace, storyId);
        if (!ReviewRecords.allowsAutomaticDelivery(workspace, storyId)) {
            throw new StageGateException(
                    "Cannot deliver after Review "
                            + ReviewRecords.readDecision(workspace, storyId)
                            + " — only PASS allows automatic Delivery");
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
        if (!ReviewRecords.allowsAutomaticDelivery(workspace, storyId)) {
            throw new StageGateException(
                    "Cannot deliver after Review "
                            + ReviewRecords.readDecision(workspace, storyId)
                            + " — only PASS allows automatic Delivery");
        }
        if (Strings.isBlank(commitSha)) {
            throw new StageGateException("Commit sha required (or use awaiting)");
        }
        write(workspace, storyId, "LOCAL_COMMIT", commitSha.trim(), false, false, false);
    }

    public static void recordAwaitingHumanCommit(Path workspace, String storyId) throws IOException {
        ReviewRecords.requirePresent(workspace, storyId);
        if (!ReviewRecords.allowsAutomaticDelivery(workspace, storyId)) {
            throw new StageGateException(
                    "Cannot deliver after Review "
                            + ReviewRecords.readDecision(workspace, storyId)
                            + " — only PASS allows automatic Delivery");
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
