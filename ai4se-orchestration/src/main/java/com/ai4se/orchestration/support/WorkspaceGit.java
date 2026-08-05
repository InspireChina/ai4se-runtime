package com.ai4se.orchestration.support;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Observed git state — no caller-supplied SHA / path lists. */
public final class WorkspaceGit {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private WorkspaceGit() {
    }

    public static List<String> changedPaths(Path workspace, ProcessInvoker invoker) throws IOException {
        ProcessInvoker.ProcessOutcome out = invoke(invoker, CommandArgv.gitStatusPorcelain(), workspace);
        if (out.timedOut) {
            throw new StageGateException("git status timed out");
        }
        if (out.exitCode != 0) {
            throw new StageGateException(
                    "git status failed exit=" + out.exitCode + " stderr=" + truncate(out.stderr));
        }
        return parsePorcelain(out.stdout);
    }

    /** Business paths that Verify must not touch (excludes .story / .ai4se / build noise). */
    public static List<String> businessChangedPaths(Path workspace, ProcessInvoker invoker)
            throws IOException {
        List<String> all = changedPaths(workspace, invoker);
        List<String> out = new ArrayList<String>();
        for (String p : all) {
            if (!isIgnorableForMutation(p)) {
                out.add(p);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static String headSha(Path workspace, ProcessInvoker invoker) throws IOException {
        ProcessInvoker.ProcessOutcome out = invoke(invoker, CommandArgv.gitRevParseHead(), workspace);
        if (out.timedOut) {
            throw new StageGateException("git rev-parse timed out");
        }
        if (out.exitCode != 0) {
            throw new StageGateException(
                    "git rev-parse HEAD failed exit=" + out.exitCode + " stderr=" + truncate(out.stderr));
        }
        String sha = out.stdout == null ? "" : out.stdout.trim();
        if (Strings.isBlank(sha) || !sha.matches("^[0-9a-fA-F]{7,40}$")) {
            throw new StageGateException("Invalid observed commit sha: " + sha);
        }
        return sha.toLowerCase(Locale.ROOT);
    }

    /**
     * Refuse Delivery that looks already pushed to a remote tracking branch with nothing ahead.
     * Local-only repos (no tracking) are fine.
     */
    public static void requireNotPushedClean(Path workspace, ProcessInvoker invoker) throws IOException {
        ProcessInvoker.ProcessOutcome out = invoke(invoker, CommandArgv.gitStatusShortBranch(), workspace);
        if (out.timedOut) {
            throw new StageGateException("git status -sb timed out");
        }
        if (out.exitCode != 0) {
            throw new StageGateException("git status -sb failed exit=" + out.exitCode);
        }
        String text = out.stdout == null ? "" : out.stdout;
        String first = text.split("\\R", 2)[0].trim().toLowerCase(Locale.ROOT);
        // "## main...origin/main" with no "ahead" → already matched remote (push happened or identical)
        if (first.contains("...") && first.contains("origin/")
                && !first.contains("ahead") && !first.contains("behind")) {
            throw new StageGateException(
                    "Push/remote-synced branch detected — Delivery must be local Commit / awaiting only: "
                            + first);
        }
        if (first.contains("ahead") && first.contains("behind")) {
            // diverged — still local work; allow
            return;
        }
    }

    /**
     * Stage listed paths and create a <b>local</b> commit. Never pushes.
     * Returns the new HEAD sha.
     */
    public static String commitLocal(
            Path workspace,
            ProcessInvoker invoker,
            List<String> pathsToAdd,
            String message) throws IOException {
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required for local commit");
        }
        if (pathsToAdd == null || pathsToAdd.isEmpty()) {
            throw new StageGateException("Local commit requires at least one path to stage");
        }
        // Do NOT requireNotPushedClean before commit: field repos normally track origin while clean.
        // After a successful local commit, branch should be "ahead" (still never Push).
        for (String path : pathsToAdd) {
            if (Strings.isBlank(path)) {
                continue;
            }
            ProcessInvoker.ProcessOutcome add = invoke(invoker, CommandArgv.gitAdd(path.trim()), workspace);
            if (add.timedOut || add.exitCode != 0) {
                throw new StageGateException(
                        "git add failed for " + path + " exit=" + add.exitCode
                                + " stderr=" + truncate(add.stderr));
            }
        }
        String[] identity = resolveCommitIdentity(workspace, invoker);
        ProcessInvoker.ProcessOutcome commit = invoke(
                invoker, CommandArgv.gitCommitLocal(message, identity[0], identity[1]), workspace);
        if (commit.timedOut) {
            throw new StageGateException("git commit timed out");
        }
        if (commit.exitCode != 0) {
            throw new StageGateException(
                    "git commit failed exit=" + commit.exitCode + " stderr=" + truncate(commit.stderr));
        }
        // After local commit we must be ahead of remote (or have no tracking) — never look fully synced.
        ProcessInvoker.ProcessOutcome sb = invoke(invoker, CommandArgv.gitStatusShortBranch(), workspace);
        if (sb.exitCode == 0 && sb.stdout != null) {
            String first = sb.stdout.split("\\R", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (first.contains("...") && first.contains("origin/")
                    && !first.contains("ahead") && !first.contains("behind")) {
                throw new StageGateException(
                        "After local commit, branch still looks remote-synced (push?): " + first);
            }
        }
        return headSha(workspace, invoker);
    }

    static List<String> parsePorcelain(String stdout) {
        Set<String> paths = new LinkedHashSet<String>();
        if (Strings.isBlank(stdout)) {
            return Collections.emptyList();
        }
        for (String line : stdout.split("\\R")) {
            if (line.isEmpty()) {
                continue;
            }
            // XY PATH or XY ORIG -> PATH
            String rest = line.length() >= 3 ? line.substring(3) : line.trim();
            if (rest.contains(" -> ")) {
                rest = rest.substring(rest.lastIndexOf(" -> ") + 4).trim();
            }
            rest = rest.trim().replace('\\', '/');
            if (rest.startsWith("\"") && rest.endsWith("\"") && rest.length() >= 2) {
                rest = rest.substring(1, rest.length() - 1);
            }
            if (!rest.isEmpty()) {
                paths.add(rest);
            }
        }
        return new ArrayList<String>(paths);
    }

    public static boolean isIgnorableForMutation(String path) {
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (p.startsWith(".story/") || p.equals(".story")) {
            return true;
        }
        if (p.startsWith(".ai4se/") || p.equals(".ai4se")) {
            return true;
        }
        if (p.startsWith(".git/") || p.equals(".git")) {
            return true;
        }
        if (p.startsWith("target/") || p.startsWith("build/") || p.startsWith("node_modules/")) {
            return true;
        }
        if (p.startsWith("dist/") || p.startsWith(".idea/") || p.startsWith(".cursor/")) {
            return true;
        }
        return false;
    }

    /**
     * Prefer customer-repo {@code git config user.name/email} (local then global).
     * Fallback {@code ai4se}/{@code ai4se@local} for bare fixtures without identity.
     */
    static String[] resolveCommitIdentity(Path workspace, ProcessInvoker invoker) throws IOException {
        String name = gitConfigGet(workspace, invoker, "user.name");
        String email = gitConfigGet(workspace, invoker, "user.email");
        if (Strings.isBlank(name)) {
            name = "ai4se";
        }
        if (Strings.isBlank(email)) {
            email = "ai4se@local";
        }
        return new String[] {name, email};
    }

    private static String gitConfigGet(Path workspace, ProcessInvoker invoker, String key)
            throws IOException {
        ProcessInvoker.ProcessOutcome out = invoke(
                invoker, Arrays.asList("git", "config", "--get", key), workspace);
        if (out.timedOut || out.exitCode != 0 || out.stdout == null) {
            return "";
        }
        return out.stdout.trim();
    }

    private static ProcessInvoker.ProcessOutcome invoke(
            ProcessInvoker invoker, List<String> argv, Path workspace) throws IOException {
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required for observed git");
        }
        try {
            return invoker.run(argv, workspace, null, TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StageGateException("Interrupted during git observation");
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
