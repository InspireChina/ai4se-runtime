package com.ai4se.orchestration.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DiscoveryAdapterExecutionTest {

    @TempDir
    Path temp;

    @Test
    void acceptsOnlyAValidCandidateWrittenInsideCandidateRoot() throws Exception {
        Path ws = workspace("success");
        String head = git(ws, "rev-parse", "HEAD").trim();
        FunctionalModelCliAdapter adapter = new FunctionalModelCliAdapter("test-discovery", request -> {
            try {
                Path root = request.workspace().resolve(".ai4se/knowledge-candidates/c1");
                Files.write(root.resolve("candidate.yaml"), (""
                        + "candidate_id: c1\nscope: repository\nsource_commit: " + head + "\ndocuments:\n"
                        + "  - id: context\n    path: documents/context.md\n    kind: system-context\n"
                        + "    tags: [system]\n    refs: [module:core]\n    source_paths: [src/Main.java]\n")
                        .getBytes(StandardCharsets.UTF_8));
                Files.write(root.resolve("documents/context.md"), "# Context\n\n## Evidence\n\n- src/Main.java\n\n## Unknowns\n\n- none\n"
                        .getBytes(StandardCharsets.UTF_8));
                return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
            } catch (Exception e) {
                return AdapterResult.failure(1, "", "", e.getMessage(), Collections.<String, String>emptyMap());
            }
        });

        DiscoveryAdapterExecution.Outcome result = DiscoveryAdapterExecution.submit(
                ws, "c1", "repository", adapter, new ProcessInvoker.RealProcessInvoker(), Duration.ofMinutes(1));

        assertEquals(1, result.documentCount());
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/knowledge-candidates/c1/adapter-discovery.md")));
        assertTrue(!Files.exists(ws.resolve("src/Rogue.java")));
    }

    @Test
    void refusesAnAdapterThatWritesBusinessSource() throws Exception {
        Path ws = workspace("rogue");
        FunctionalModelCliAdapter adapter = new FunctionalModelCliAdapter("rogue", request -> {
            try {
                Files.write(request.workspace().resolve("src/Rogue.java"), "class Rogue {}\n".getBytes(StandardCharsets.UTF_8));
                return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
            } catch (Exception e) {
                return AdapterResult.failure(1, "", "", e.getMessage(), Collections.<String, String>emptyMap());
            }
        });

        StageGateException thrown = assertThrows(StageGateException.class,
                () -> DiscoveryAdapterExecution.submit(ws, "c2", "repository", adapter,
                        new ProcessInvoker.RealProcessInvoker(), Duration.ofMinutes(1)));
        assertTrue(thrown.getMessage().contains("write-scope violation"), thrown.getMessage());
    }

    @Test
    void repairsAnEvidenceContractFailureExactlyOnce() throws Exception {
        Path ws = workspace("repair");
        String head = git(ws, "rev-parse", "HEAD").trim();
        final int[] calls = {0};
        FunctionalModelCliAdapter adapter = new FunctionalModelCliAdapter("repair", request -> {
            try {
                calls[0]++;
                Path root = request.workspace().resolve(".ai4se/knowledge-candidates/c3");
                Files.write(root.resolve("candidate.yaml"), (""
                        + "candidate_id: c3\nscope: repository\nsource_commit: " + head + "\ndocuments:\n"
                        + "  - id: context\n    path: documents/context.md\n    kind: system-context\n"
                        + "    tags: [system]\n    refs: [module:core]\n    source_paths: [src/Main.java]\n")
                        .getBytes(StandardCharsets.UTF_8));
                String evidence = calls[0] == 1 ? "- a source citation is missing\n" : "- src/Main.java\n";
                Files.write(root.resolve("documents/context.md"), ("# Context\n\n## Evidence\n\n"
                        + evidence + "\n## Unknowns\n\n- none\n").getBytes(StandardCharsets.UTF_8));
                return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
            } catch (Exception e) {
                return AdapterResult.failure(1, "", "", e.getMessage(), Collections.<String, String>emptyMap());
            }
        });

        DiscoveryAdapterExecution.Outcome result = DiscoveryAdapterExecution.submit(
                ws, "c3", "repository", adapter, new ProcessInvoker.RealProcessInvoker(), Duration.ofMinutes(1));

        assertEquals(2, calls[0]);
        assertEquals(1, result.documentCount());
        assertTrue(new String(Files.readAllBytes(
                ws.resolve(".ai4se/knowledge-candidates/c3/validation-failure.md")), StandardCharsets.UTF_8)
                .contains("must cite every declared source_path"));
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/knowledge-candidates/c3/adapter-discovery-attempt-1.md")));
    }

    @Test
    void rejectsASecondInvalidCandidateWithoutUnboundedRepair() throws Exception {
        Path ws = workspace("repair-refused");
        String head = git(ws, "rev-parse", "HEAD").trim();
        final int[] calls = {0};
        FunctionalModelCliAdapter adapter = new FunctionalModelCliAdapter("invalid", request -> {
            try {
                calls[0]++;
                Path root = request.workspace().resolve(".ai4se/knowledge-candidates/c4");
                Files.write(root.resolve("candidate.yaml"), (""
                        + "candidate_id: c4\nscope: repository\nsource_commit: " + head + "\ndocuments:\n"
                        + "  - id: context\n    path: documents/context.md\n    kind: system-context\n"
                        + "    tags: [system]\n    refs: [module:core]\n    source_paths: [src/Main.java]\n")
                        .getBytes(StandardCharsets.UTF_8));
                Files.write(root.resolve("documents/context.md"), "# Context\n\n## Evidence\n\n- still missing\n\n## Unknowns\n\n- none\n"
                        .getBytes(StandardCharsets.UTF_8));
                return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
            } catch (Exception e) {
                return AdapterResult.failure(1, "", "", e.getMessage(), Collections.<String, String>emptyMap());
            }
        });

        StageGateException thrown = assertThrows(StageGateException.class,
                () -> DiscoveryAdapterExecution.submit(ws, "c4", "repository", adapter,
                        new ProcessInvoker.RealProcessInvoker(), Duration.ofMinutes(1)));

        assertEquals(2, calls[0]);
        assertTrue(thrown.getMessage().contains("one validation repair"), thrown.getMessage());
    }

    private Path workspace(String name) throws Exception {
        Path ws = temp.resolve(name);
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.write(ws.resolve("src/Main.java"), "class Main {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/facts.md"), "# facts\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/module-map.md"), "# map\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        git(ws, "init");
        git(ws, "add", ".");
        git(ws, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "base");
        return ws;
    }

    private static String git(Path ws, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process p = new ProcessBuilder(command).directory(ws.toFile()).start();
        byte[] stdout = read(p.getInputStream());
        byte[] stderr = read(p.getErrorStream());
        if (p.waitFor() != 0) {
            throw new AssertionError("git failed: " + new String(stderr, StandardCharsets.UTF_8));
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int count;
        while ((count = input.read(bytes)) >= 0) {
            out.write(bytes, 0, count);
        }
        return out.toByteArray();
    }
}
