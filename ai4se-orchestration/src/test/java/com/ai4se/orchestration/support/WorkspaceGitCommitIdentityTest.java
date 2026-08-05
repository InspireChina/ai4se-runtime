package com.ai4se.orchestration.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.ProcessInvoker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceGitCommitIdentityTest {

    @TempDir
    Path temp;

    @Test
    void commitUsesWorkspaceUserWhenConfigured() throws Exception {
        Path ws = temp.resolve("repo");
        Files.createDirectories(ws);
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "config", "user.name", "peng.lv");
        run(real, ws, "git", "config", "user.email", "peng.lv@example.com");
        run(real, ws, "git", "commit", "--allow-empty", "-m", "init");

        Files.write(ws.resolve("a.txt"), "x\n".getBytes(StandardCharsets.UTF_8));
        String sha = WorkspaceGit.commitLocal(
                ws, real, Collections.singletonList("a.txt"), "field commit");
        assertTrue(sha.matches("^[0-9a-f]{7,40}$"));

        ProcessInvoker.ProcessOutcome who = real.run(
                Arrays.asList("git", "log", "-1", "--format=%an <%ae>"),
                ws,
                null,
                Duration.ofSeconds(30));
        assertEquals(0, who.exitCode);
        assertEquals("peng.lv <peng.lv@example.com>", who.stdout.trim());
    }

    @Test
    void resolveFallsBackWhenNoIdentityVisible() throws Exception {
        Path ws = temp.resolve("bare");
        Files.createDirectories(ws);
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");

        // Isolate from developer global/system git identity.
        Map<String, String> env = new HashMap<String, String>();
        env.put("GIT_CONFIG_GLOBAL", "/dev/null");
        env.put("GIT_CONFIG_SYSTEM", "/dev/null");
        ProcessInvoker.ProcessOutcome name = real.run(
                Arrays.asList("git", "config", "--get", "user.name"),
                ws,
                env,
                Duration.ofSeconds(10));
        ProcessInvoker.ProcessOutcome email = real.run(
                Arrays.asList("git", "config", "--get", "user.email"),
                ws,
                env,
                Duration.ofSeconds(10));
        assertTrue(name.exitCode != 0 || name.stdout == null || name.stdout.trim().isEmpty());
        assertTrue(email.exitCode != 0 || email.stdout == null || email.stdout.trim().isEmpty());

        // CommandArgv fallback values used when WorkspaceGit resolves blanks.
        List<String> argv = CommandArgv.gitCommitLocal("m", "", "");
        assertTrue(argv.contains("user.name=ai4se"));
        assertTrue(argv.contains("user.email=ai4se@local"));
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                Arrays.asList(argv), ws, null, Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
