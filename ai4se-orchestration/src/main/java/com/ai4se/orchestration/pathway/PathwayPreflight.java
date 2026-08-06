package com.ai4se.orchestration.pathway;

import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.verification.VerificationEntries;
import com.ai4se.runtime.common.util.ShellExecutable;
import com.ai4se.runtime.common.util.Strings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Control-layer readiness gate before Analysis burns model calls.
 *
 * <p>Problem class: execution environment not ready — WSL stub, missing claude/cursor,
 * unusable entries, Windows shebang CreateProcess=193 share one preflight mechanism.
 */
public final class PathwayPreflight {

    private PathwayPreflight() {
    }

    public static void check(Path workspace, PathwayRunner.Config config) throws IOException {
        if (workspace == null || !Files.isDirectory(workspace)) {
            throw new StageGateException("Preflight: workspace missing");
        }
        try {
            ShellExecutable.requireUsable();
        } catch (IllegalStateException e) {
            throw new StageGateException("Preflight ENV_FAIL: " + e.getMessage());
        }

        Path entries = workspace.resolve(".ai4se/repository/entries.yaml");
        if (!Files.isRegularFile(entries)) {
            throw new StageGateException("Preflight ENV_FAIL: missing .ai4se/repository/entries.yaml");
        }
        List<String> tests = VerificationEntries.readUsableTestCommands(workspace);
        if (tests.isEmpty()) {
            throw new StageGateException(
                    "Preflight ENV_FAIL: no usable test entries in entries.yaml");
        }

        if (usesClaude(config)) {
            requireCliBinary("claude", ClaudeCliAdapter.resolveBinary(null), ClaudeCliAdapter.ENV_BIN);
        }
        if (usesCursor(config)) {
            requireCliBinary("cursor", CursorCliAdapter.resolveBinary(null), CursorCliAdapter.ENV_BIN);
        }
    }

    private static void requireCliBinary(String label, String bin, String envName) {
        if (Strings.isBlank(bin)) {
            throw new StageGateException("Preflight ENV_FAIL: " + label + " binary unresolved");
        }
        if (bin.indexOf('/') >= 0 || bin.indexOf('\\') >= 0) {
            Path p = Paths.get(bin);
            Path cmd = Paths.get(bin + ".cmd");
            if (!Files.isRegularFile(p) && !Files.isRegularFile(cmd)) {
                throw new StageGateException(
                        "Preflight ENV_FAIL: " + label + " binary not a file: " + bin);
            }
            // Script CLIs on Windows need a real bash (already required above); surface clearly.
            if (ShellExecutable.needsShellWrapper(bin)) {
                try {
                    ShellExecutable.requireUsable();
                } catch (IllegalStateException e) {
                    throw new StageGateException(
                            "Preflight ENV_FAIL: " + label + " is a script needing bash — "
                                    + e.getMessage());
                }
            }
        } else if (("claude".equals(bin) || "agent".equals(bin) || "cursor".equals(bin))
                && !cliLikelyOnPath(bin)) {
            throw new StageGateException(
                    "Preflight ENV_FAIL: " + label + " CLI not found — set " + envName
                            + " or install on PATH");
        }
    }

    private static boolean usesClaude(PathwayRunner.Config config) {
        if (config == null) {
            return false;
        }
        return isClaude(config.analysisAdapter)
                || isClaude(config.planAdapter)
                || isClaude(config.devAdapter)
                || isClaude(config.reviewAdapter);
    }

    private static boolean usesCursor(PathwayRunner.Config config) {
        if (config == null) {
            return false;
        }
        return isCursor(config.analysisAdapter)
                || isCursor(config.planAdapter)
                || isCursor(config.devAdapter)
                || isCursor(config.reviewAdapter);
    }

    private static boolean isClaude(ModelCliAdapter adapter) {
        return adapter instanceof ClaudeCliAdapter;
    }

    private static boolean isCursor(ModelCliAdapter adapter) {
        return adapter instanceof CursorCliAdapter;
    }

    private static boolean cliLikelyOnPath(String leaf) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        String[] dirs = path.split(File.pathSeparator);
        for (int i = 0; i < dirs.length; i++) {
            String dir = dirs[i];
            if (dir == null || dir.isEmpty()) {
                continue;
            }
            if (Files.isRegularFile(Paths.get(dir, leaf))
                    || Files.isRegularFile(Paths.get(dir, leaf + ".cmd"))
                    || Files.isRegularFile(Paths.get(dir, leaf + ".exe"))) {
                return true;
            }
        }
        return false;
    }
}
