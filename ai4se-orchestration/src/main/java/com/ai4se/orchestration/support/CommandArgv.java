package com.ai4se.orchestration.support;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.ShellExecutable;
import com.ai4se.runtime.common.util.Strings;
import java.util.Arrays;
import java.util.List;

/**
 * Builds process argv for observed gates.
 * Customer entry commands run under {@code bash -lc} (already allowlisted by entries).
 * Git observations use fixed argv (no shell).
 */
public final class CommandArgv {

    private CommandArgv() {
    }

    /** Entry / verify command — shell form, gated by VerificationEntries beforehand. */
    public static List<String> shellCommand(String command) {
        if (Strings.isBlank(command)) {
            throw new StageGateException("Command required");
        }
        return Arrays.asList(ShellExecutable.resolve(), "-lc", command.trim());
    }

    public static List<String> gitStatusPorcelain() {
        // -uall: list each untracked file (not just parent dir) for Allowed ⊆ checks
        return Arrays.asList("git", "status", "--porcelain", "-uall");
    }

    public static List<String> gitRevParseHead() {
        return Arrays.asList("git", "rev-parse", "HEAD");
    }

    public static List<String> gitStatusShortBranch() {
        return Arrays.asList("git", "status", "-sb");
    }

    public static List<String> gitAdd(String path) {
        if (Strings.isBlank(path)) {
            throw new StageGateException("git add path required");
        }
        return Arrays.asList("git", "add", "--", path.trim());
    }

    /**
     * Local commit only — never push.
     * Prefer workspace {@code user.name}/{@code user.email}; callers pass resolved values
     * (fallback {@code ai4se}/{@code ai4se@local} when the repo has no identity).
     */
    public static List<String> gitCommitLocal(String message, String userName, String userEmail) {
        if (Strings.isBlank(message)) {
            throw new StageGateException("Commit message required");
        }
        String name = Strings.isBlank(userName) ? "ai4se" : userName.trim();
        String email = Strings.isBlank(userEmail) ? "ai4se@local" : userEmail.trim();
        return Arrays.asList(
                "git",
                "-c", "user.name=" + name,
                "-c", "user.email=" + email,
                "commit",
                "-m",
                message.trim());
    }

    /** Subject of HEAD commit (single line). */
    public static List<String> gitLogHeadSubject() {
        return Arrays.asList("git", "log", "-1", "--pretty=%s");
    }

    /** Verify object exists (commit SHA). */
    public static List<String> gitCatFileExists(String sha) {
        if (Strings.isBlank(sha)) {
            throw new StageGateException("commit sha required");
        }
        return Arrays.asList("git", "cat-file", "-e", sha.trim());
    }

    /** Paths changed in a commit (name-only, no rename rewrite). */
    public static List<String> gitShowNameOnly(String sha) {
        if (Strings.isBlank(sha)) {
            throw new StageGateException("commit sha required");
        }
        return Arrays.asList("git", "show", "--name-only", "--pretty=format:", sha.trim());
    }
}
