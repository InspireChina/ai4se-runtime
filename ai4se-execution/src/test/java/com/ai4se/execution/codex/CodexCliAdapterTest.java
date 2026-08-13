package com.ai4se.execution.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.ScriptedProcessInvoker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CodexCliAdapterTest {

    @TempDir
    Path temp;

    @Test
    void buildsExecWorkspaceWriteApprovalArgvForAllProductionRoles() throws Exception {
        for (String role : Arrays.asList("Analysis", "Planning", "Development", "Review")) {
            Path pkg = writePackage(role);
            ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "ok", "", false);
            CodexCliAdapter adapter = new CodexCliAdapter(invoker, "codex", "gpt-test");

            AdapterResult result = adapter.execute(request(pkg, role));
            assertTrue(result.success(), role);
            List<String> argv = invoker.argvHistory().get(0);
            assertEquals("codex", argv.get(0));
            assertEquals("exec", argv.get(1));
            assertTrue(argv.contains("--approve-for-me"), role);
            assertFalse(argv.contains("--sandbox"), role);
            assertFalse(argv.contains("workspace-write"), role);
            assertTrue(argv.contains("--model"), role);
            assertTrue(argv.contains("gpt-test"), role);
            assertFalse(argv.stream().anyMatch(flag -> flag.startsWith("--dangerously-bypass")), role);
            assertTrue(argv.get(argv.size() - 1).contains("role=" + role), role);
        }
    }

    @Test
    void nonZeroExitIsReturnedWithoutRetry() throws Exception {
        Path pkg = writePackage("Analysis");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(17, "partial", "boom", false);
        AdapterResult result = new CodexCliAdapter(invoker, "codex").execute(request(pkg, "Analysis"));

        assertFalse(result.success());
        assertEquals(17, result.exitCode());
        assertEquals(1, invoker.argvHistory().size());
        assertTrue(result.message().contains("Codex CLI exit=17"));
    }

    @Test
    void timeoutIsReturnedAsFailureWithoutRetry() throws Exception {
        Path pkg = writePackage("Review");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(-1, "", "hang", true);
        AdapterResult result = new CodexCliAdapter(invoker, "codex").execute(request(pkg, "Review"));

        assertFalse(result.success());
        assertTrue(result.message().contains("timed out"));
        assertEquals(1, invoker.argvHistory().size());
    }

    @Test
    void missingPackageFailsBeforeProcess() {
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "ok", "", false);
        AdapterResult result = new CodexCliAdapter(invoker, "codex").execute(new AdapterRequest(
                temp, temp.resolve("missing"), "Analysis", "s1", Duration.ofSeconds(5),
                Collections.<String, String>emptyMap()));
        assertFalse(result.success());
        assertTrue(invoker.argvHistory().isEmpty());
    }

    private AdapterRequest request(Path pkg, String role) {
        return new AdapterRequest(
                temp, pkg, role, "story-1", Duration.ofSeconds(30),
                Collections.<String, String>emptyMap());
    }

    private Path writePackage(String role) throws IOException {
        Path pkg = temp.resolve("packages").resolve(role.toLowerCase());
        Files.createDirectories(pkg.resolve("slices"));
        Files.write(pkg.resolve("manifest.md"),
                ("# Context Package Manifest\n\n- role: " + role + "\n- story_id: story-1\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(pkg.resolve("slices/acceptance.md"),
                "# Acceptance\n\n- concrete\n".getBytes(StandardCharsets.UTF_8));
        return pkg;
    }
}
