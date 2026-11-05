package com.ai4se.execution.claude;

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

final class ClaudeCliAdapterTest {

    @TempDir
    Path temp;

    @Test
    void refusesMissingPackageWithoutInvokingCli() {
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "ok", "", false);
        ClaudeCliAdapter adapter = new ClaudeCliAdapter(invoker, "claude");
        AdapterResult result = adapter.execute(new AdapterRequest(
                temp, temp.resolve("missing-pkg"), "Analysis", "s1", Duration.ofSeconds(5),
                Collections.<String, String>emptyMap()));
        assertFalse(result.success());
        assertEquals(0, invoker.argvHistory().size());
        assertTrue(result.message().contains("Package"));
    }

    @Test
    void submitsPackageToCliOnSuccess() throws Exception {
        Path pkg = writePackage("Analysis");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "analysis done", "", false);
        ClaudeCliAdapter adapter = new ClaudeCliAdapter(invoker, "claude");
        AdapterResult result = adapter.execute(request(pkg, "Analysis"));
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        List<String> argv = invoker.argvHistory().get(0);
        assertEquals("claude", argv.get(0));
        assertTrue(argv.contains("-p"));
        assertTrue(argv.contains("--output-format"));
        assertTrue(argv.contains("text"));
        assertFalse(argv.contains("--dangerously-skip-permissions"), "Analysis must not full-bypass");
        assertTrue(argv.contains("--permission-mode"), "Analysis must auto-approve .story writes");
        assertTrue(argv.contains("acceptEdits"));
        String prompt = argv.get(argv.size() - 1);
        assertTrue(prompt.contains("role=Analysis"));
        assertTrue(prompt.contains("Do NOT decide workflow stages"));
        assertTrue(prompt.contains("manifest.md"));
    }

    @Test
    void planningAndReviewRolesUseAcceptEditsNotFullBypass() throws Exception {
        for (String role : Arrays.asList("Planning", "Review")) {
            Path pkg = writePackage(role);
            ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "ok", "", false);
            ClaudeCliAdapter adapter = new ClaudeCliAdapter(invoker, "claude");
            adapter.execute(request(pkg, role));
            List<String> argv = invoker.argvHistory().get(0);
            assertFalse(argv.contains("--dangerously-skip-permissions"), role);
            assertTrue(argv.contains("--permission-mode"), role);
            assertTrue(argv.contains("acceptEdits"), role);
        }
    }

    @Test
    void developmentRoleAddsSkipPermissions() throws Exception {
        Path pkg = writePackage("Development");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "ok", "", false);
        ClaudeCliAdapter adapter = new ClaudeCliAdapter(invoker, "claude");
        adapter.execute(request(pkg, "Development"));
        List<String> argv = invoker.argvHistory().get(0);
        assertTrue(argv.contains("--dangerously-skip-permissions"));
        assertFalse(argv.contains("--permission-mode"), "Dev uses full bypass, not acceptEdits");
    }

    @Test
    void nonZeroExitReturnedAsIsWithoutRetry() throws Exception {
        Path pkg = writePackage("Analysis");
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(7, "partial", "boom", false);
        ClaudeCliAdapter adapter = new ClaudeCliAdapter(invoker, "claude");
        AdapterResult result = adapter.execute(request(pkg, "Analysis"));
        assertFalse(result.success());
        assertEquals(7, result.exitCode());
        assertEquals(1, invoker.argvHistory().size(), "must not retry inside adapter");
        assertFalse(result.hasNextStageHint());
    }

    @Test
    void adapterPublicApiHasNoControlMethods() {
        List<String> banned = Arrays.asList(
                "retry", "advance", "skipStage", "skipVerification", "setNextStage", "loop");
        for (java.lang.reflect.Method m : ClaudeCliAdapter.class.getDeclaredMethods()) {
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
}
