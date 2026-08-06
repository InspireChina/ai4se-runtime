package com.ai4se.orchestration.pathway;

import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.claude.ClaudeCliAdapter;
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
 * <p>Problem class: execution environment not ready — WSL stub, missing claude, unusable
 * entries share one preflight mechanism.
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
            String bin = ClaudeCliAdapter.resolveBinary(null);
            if (Strings.isBlank(bin)) {
                throw new StageGateException("Preflight ENV_FAIL: claude binary unresolved");
            }
            if (bin.indexOf('/') >= 0 || bin.indexOf('\\') >= 0) {
                Path p = Paths.get(bin);
                Path cmd = Paths.get(bin + ".cmd");
                if (!Files.isRegularFile(p) && !Files.isRegularFile(cmd)) {
                    throw new StageGateException(
                            "Preflight ENV_FAIL: claude binary not a file: " + bin);
                }
            } else if ("claude".equals(bin) && !claudeLikelyOnPath()) {
                throw new StageGateException(
                        "Preflight ENV_FAIL: claude CLI not found — set "
                                + ClaudeCliAdapter.ENV_BIN
                                + " or install claude on PATH");
            }
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

    private static boolean isClaude(ModelCliAdapter adapter) {
        return adapter instanceof ClaudeCliAdapter;
    }

    private static boolean claudeLikelyOnPath() {
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
            if (Files.isRegularFile(Paths.get(dir, "claude"))
                    || Files.isRegularFile(Paths.get(dir, "claude.cmd"))) {
                return true;
            }
        }
        return false;
    }
}
