package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression: preflight must inspect Adapter instance binary, not static resolveBinary(null).
 */
final class PathwayPreflightTest {

    @TempDir
    Path temp;

    @Test
    void acceptsStubBinaryOnInstanceEvenWithoutPathCli() throws Exception {
        Path ws = onboarded("preflight-stub");
        Path stub = temp.resolve("instance-only-agent");
        Files.write(stub, ("#!/usr/bin/env bash\necho ok\n").getBytes(StandardCharsets.UTF_8));
        stub.toFile().setExecutable(true);

        CursorCliAdapter adapter = new CursorCliAdapter(
                new ProcessInvoker.RealProcessInvoker(), stub.toAbsolutePath().toString());
        PathwayRunner.Config config = PathwayRunner.Config.builder(ws, "story-pf")
                .allowedFile("src/A.java")
                .devAdapter(adapter)
                .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                .build();

        // Old bug: resolveBinary(null) → "agent"/"cursor" not on PATH → ENV_FAIL,
        // while this instance would launch the stub successfully.
        assertDoesNotThrow(() -> PathwayPreflight.check(ws, config));
        assertTrue(adapter.resolvedBinary().contains("instance-only-agent"));
    }

    @Test
    void rejectsMissingInstanceBinaryPath() throws Exception {
        Path ws = onboarded("preflight-miss");
        Path missing = temp.resolve("no-such-cli-binary");
        ClaudeCliAdapter adapter = new ClaudeCliAdapter(
                new ProcessInvoker.RealProcessInvoker(), missing.toAbsolutePath().toString());
        PathwayRunner.Config config = PathwayRunner.Config.builder(ws, "story-miss")
                .allowedFile("src/A.java")
                .devAdapter(adapter)
                .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                .build();

        StageGateException ex = assertThrows(
                StageGateException.class, () -> PathwayPreflight.check(ws, config));
        assertTrue(ex.getMessage().contains("ENV_FAIL"), ex.getMessage());
        assertTrue(ex.getMessage().contains("not a file") || ex.getMessage().contains("binary"),
                ex.getMessage());
    }

    private Path onboarded(String tag) throws Exception {
        Path ws = temp.resolve(tag);
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));
        return ws;
    }
}
