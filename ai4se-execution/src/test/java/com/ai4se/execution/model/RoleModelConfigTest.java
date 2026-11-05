package com.ai4se.execution.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.support.ScriptedProcessInvoker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RoleModelConfigTest {

    @TempDir
    Path temp;

    @Test
    void parseYamlAndResolvePerRole() {
        RoleModelConfig cfg = RoleModelConfig.parseYamlLite(""
                + "default: claude-sonnet-4-5\n"
                + "roles:\n"
                + "  analysis: claude-sonnet-4-5\n"
                + "  development: deepseek-v4-pro\n"
                + "  acceptance: accept-model-x\n");
        assertEquals("claude-sonnet-4-5", cfg.resolve("Analysis"));
        assertEquals("deepseek-v4-pro", cfg.resolve("Development"));
        assertEquals("accept-model-x", cfg.resolve("Review")); // review ← acceptance fallback
        assertEquals("accept-model-x", cfg.resolve("acceptance"));
        assertEquals("claude-sonnet-4-5", cfg.resolve("Planning")); // default
    }

    @Test
    void mergeOverlayWins() {
        RoleModelConfig file = RoleModelConfig.builder()
                .role("development", "file-model")
                .build();
        RoleModelConfig cli = RoleModelConfig.builder()
                .role("development", "deepseek-v4-pro")
                .role("analysis", "claude-sonnet-4-5")
                .build();
        RoleModelConfig merged = file.mergeOverlay(cli);
        assertEquals("deepseek-v4-pro", merged.resolve("development"));
        assertEquals("claude-sonnet-4-5", merged.resolve("analysis"));
    }

    @Test
    void loadFromWorkspaceFile() throws Exception {
        Path file = temp.resolve(".ai4se/runtime/role-models.yaml");
        Files.createDirectories(file.getParent());
        Files.write(file, ("roles:\n  analysis: sonnet\n").getBytes(StandardCharsets.UTF_8));
        RoleModelConfig cfg = RoleModelConfig.loadFromWorkspace(temp);
        assertEquals("sonnet", cfg.resolve("analysis"));
    }

    @Test
    void claudeCliPassesModelFlag() throws Exception {
        Path pkg = temp.resolve("packages/analysis");
        Files.createDirectories(pkg.resolve("slices"));
        Files.write(pkg.resolve("manifest.md"), ("# m\n- role: Analysis\n").getBytes(StandardCharsets.UTF_8));
        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "ok", "", false);
        ClaudeCliAdapter adapter = new ClaudeCliAdapter(invoker, "claude", null);
        Map<String, String> env = new HashMap<String, String>();
        env.put(RoleModelConfig.ENV_MODEL, "deepseek-v4-pro");
        AdapterRequest req = new AdapterRequest(
                temp, pkg, "Development", "s1", Duration.ofSeconds(5), env);
        adapter.execute(req);
        List<String> argv = invoker.argvHistory().get(0);
        assertTrue(argv.contains("--model"), argv.toString());
        int i = argv.indexOf("--model");
        assertEquals("deepseek-v4-pro", argv.get(i + 1));
        assertTrue(argv.contains("--dangerously-skip-permissions"));
    }

    @Test
    void resolverPrefersRequestEnvOverAdapterDefault() {
        Map<String, String> env = Collections.singletonMap(RoleModelConfig.ENV_MODEL, "from-env");
        AdapterRequest req = new AdapterRequest(
                temp, temp, "Analysis", "s", Duration.ofSeconds(1), env);
        assertEquals("from-env", RoleModelResolver.modelFor(req, "adapter-default"));
        assertEquals("adapter-default", RoleModelResolver.modelFor(
                new AdapterRequest(temp, temp, "Analysis", "s", Duration.ofSeconds(1),
                        Collections.<String, String>emptyMap()),
                "adapter-default"));
        assertNull(RoleModelResolver.modelFor(
                new AdapterRequest(temp, temp, "Analysis", "s", Duration.ofSeconds(1),
                        Collections.<String, String>emptyMap()),
                null));
    }
}
