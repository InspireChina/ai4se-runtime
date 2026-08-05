package com.ai4se.execution.cursor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.ScriptedProcessInvoker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CursorCliAdapterTest {

    @TempDir
    Path temp;

    @Test
    void refusesMissingPackageWithoutInvokingCli() throws Exception {
        CountingInvoker invoker = new CountingInvoker(0, "ok", "", false);
        CursorCliAdapter adapter = new CursorCliAdapter(invoker, "agent");
        AdapterResult result = adapter.execute(new AdapterRequest(
                temp, temp.resolve("missing-pkg"), "Analysis", "s1", Duration.ofSeconds(5),
                Collections.<String, String>emptyMap()));
        assertFalse(result.success());
        assertEquals(0, invoker.calls.get());
        assertTrue(result.message().contains("Package"));
    }

    @Test
    void submitsPackageToCliOnSuccess() throws Exception {
        Path pkg = writePackage("Analysis");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "analysis done", "", false);
        CursorCliAdapter adapter = new CursorCliAdapter(invoker, "agent");
        AdapterResult result = adapter.execute(request(pkg, "Analysis"));
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("analysis done", result.stdout());
        assertEquals(1, invoker.argvHistory().size());
        List<String> argv = invoker.argvHistory().get(0);
        assertEquals("agent", argv.get(0));
        assertTrue(argv.contains("-p"));
        assertTrue(argv.contains("--output-format"));
        assertTrue(argv.contains("text"));
        assertFalse(argv.contains("--force"), "Analysis must not force-write");
        String prompt = argv.get(argv.size() - 1);
        assertTrue(prompt.contains("role=Analysis"));
        assertTrue(prompt.contains("Do NOT decide workflow stages"));
        assertTrue(prompt.contains("manifest.md"));
    }

    @Test
    void developmentRoleAddsForce() throws Exception {
        Path pkg = writePackage("Development");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "ok", "", false);
        CursorCliAdapter adapter = new CursorCliAdapter(invoker, "agent");
        adapter.execute(request(pkg, "Development"));
        assertTrue(invoker.argvHistory().get(0).contains("--force"));
    }

    @Test
    void cursorAppBinaryInsertsAgentSubcommand() throws Exception {
        Path pkg = writePackage("Development");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "ok", "", false);
        CursorCliAdapter adapter = new CursorCliAdapter(
                invoker, "/Applications/Cursor.app/Contents/Resources/app/bin/cursor");
        adapter.execute(request(pkg, "Development"));
        List<String> argv = invoker.argvHistory().get(0);
        assertEquals("/Applications/Cursor.app/Contents/Resources/app/bin/cursor", argv.get(0));
        assertEquals("agent", argv.get(1));
        assertTrue(argv.contains("-p"));
        assertTrue(argv.contains("--force"));
    }

    @Test
    void nonZeroExitReturnedAsIsWithoutRetry() throws Exception {
        Path pkg = writePackage("Analysis");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(7, "partial", "boom", false);
        CursorCliAdapter adapter = new CursorCliAdapter(invoker, "agent");
        AdapterResult result = adapter.execute(request(pkg, "Analysis"));
        assertFalse(result.success());
        assertEquals(7, result.exitCode());
        assertEquals("partial", result.stdout());
        assertEquals("boom", result.stderr());
        assertTrue(result.message().contains("exit=7"));
        assertTrue(result.message().contains("boom"));
        assertEquals(1, invoker.argvHistory().size(), "must not retry inside adapter");
        assertFalse(result.hasNextStageHint());
    }

    @Test
    void timeoutReturnedAsFailureOnce() throws Exception {
        Path pkg = writePackage("Analysis");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(-1, "", "hang", true);
        CursorCliAdapter adapter = new CursorCliAdapter(invoker, "agent");
        AdapterResult result = adapter.execute(request(pkg, "Analysis"));
        assertFalse(result.success());
        assertTrue(result.message().contains("timed out"));
        assertEquals(1, invoker.argvHistory().size());
    }

    @Test
    void resultMustNotCarryControlHints() throws Exception {
        Path pkg = writePackage("Analysis");
        CursorCliAdapter adapter = new CursorCliAdapter(
                new ScriptedProcessInvoker(0, "ok", "", false), "agent");
        AdapterResult result = adapter.execute(request(pkg, "Analysis"));
        assertFalse(result.details().containsKey("next_stage"));
        assertFalse(result.details().containsKey("retry"));
        assertFalse(result.details().containsKey("skip_verification"));
        assertFalse(result.hasNextStageHint());
    }

    @Test
    void adapterPublicApiHasNoControlMethods() {
        List<String> banned = Arrays.asList(
                "retry", "advance", "skipStage", "skipVerification", "setNextStage", "loop");
        for (java.lang.reflect.Method m : CursorCliAdapter.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            for (String b : banned) {
                assertFalse(name.contains(b.toLowerCase()), "forbidden method: " + m.getName());
            }
        }
    }

    private AdapterRequest request(Path pkg, String role) {
        return new AdapterRequest(
                temp, pkg, role, "story-1", Duration.ofSeconds(30),
                Collections.<String, String>emptyMap());
    }

    private Path writePackage(String role) throws IOException {
        Path pkg = temp.resolve("packages").resolve(role.toLowerCase());
        Files.createDirectories(pkg.resolve("slices"));
        Files.write(
                pkg.resolve("manifest.md"),
                ("# Context Package Manifest\n\n- role: " + role + "\n- story_id: story-1\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                pkg.resolve("slices/acceptance.md"),
                "# Acceptance\n\n- concrete\n".getBytes(StandardCharsets.UTF_8));
        return pkg;
    }

    static final class CountingInvoker implements ProcessInvoker {
        final AtomicInteger calls = new AtomicInteger();
        private final int exit;
        private final String stdout;
        private final String stderr;
        private final boolean timedOut;

        CountingInvoker(int exit, String stdout, String stderr, boolean timedOut) {
            this.exit = exit;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }

        @Override
        public ProcessOutcome run(
                List<String> argv,
                Path workingDirectory,
                java.util.Map<String, String> extraEnv,
                Duration timeout) {
            calls.incrementAndGet();
            return new ProcessOutcome(exit, stdout, stderr, timedOut);
        }
    }
}
