package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.execution.support.SplitProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W4-on-spine: Dev Package → Adapter once → observe Diff; failure returns as-is (no retry). */
final class PathwayAdapterOnSpineIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void adapterDevOnSpineV3WritesEvidence() throws Exception {
        Path ws = prepare("adapter-v3");
        AtomicInteger calls = new AtomicInteger();
        FunctionalModelCliAdapter adapter = new FunctionalModelCliAdapter("spine-dev", request -> {
            calls.incrementAndGet();
            Path target = request.workspace().resolve("src/Feature.java");
            Files.createDirectories(target.getParent());
            Files.write(target, "class Feature {}\n".getBytes(StandardCharsets.UTF_8));
            return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
        });

        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(SequenceProcessInvoker.ok("TESTS OK")));

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-adp")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed("story-adp"))
                        .allowedFile("src/Feature.java")
                        .devAdapter(adapter)
                        .allowReviewFixture(true)
                        .build(),
                invoker);

        assertEquals(1, calls.get());
        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertTrue(result.spine.adapterInvoked);
        assertEquals("hybrid_adapter_dev", result.spine.spineMode);
        assertEquals("adapter_spine_wiring_functional", result.spine.signoffClaim());
        assertEquals("functional_hook", result.spine.adapterKind);
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("spine_mode: hybrid_adapter_dev"));
        assertTrue(meta.contains("adapter_roles: Development"));
        assertTrue(meta.contains("adapter_kind: functional_hook"));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-adp/execution/adapter-dev-round-1.md")));
    }

    @Test
    void adapterFailureStopsWithoutRetry() throws Exception {
        Path ws = prepare("adapter-fail");
        AtomicInteger calls = new AtomicInteger();
        FunctionalModelCliAdapter adapter = new FunctionalModelCliAdapter("fail-dev", request -> {
            calls.incrementAndGet();
            return AdapterResult.failure(7, "", "boom", "adapter boom", Collections.<String, String>emptyMap());
        });

        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> PathwayRunner.run(
                        PathwayRunner.Config.builder(ws, "story-fail")
                                .script(Script.V3)
                                .suite("A")
                                .seedPath(seed("story-fail"))
                                .allowedFile("src/A.java")
                                .devAdapter(adapter)
                                .build(),
                        invoker));
        assertEquals(1, calls.get(), "Adapter must not retry");
        assertTrue(ex.getMessage().contains("no Adapter retry"));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-fail/execution/adapter-dev-round-1.md")));
    }

    @Test
    void cursorCliStubBinaryOnSpine() throws Exception {
        Path ws = prepare("cursor-stub");
        Path stub = temp.resolve("fake-agent");
        Files.write(stub, (""
                + "#!/usr/bin/env bash\n"
                + "mkdir -p \"$PWD/src\"\n"
                + "echo 'class Stub {}' > \"$PWD/src/Stub.java\"\n"
                + "echo stub-ok\n"
                + "exit 0\n").getBytes(StandardCharsets.UTF_8));
        stub.toFile().setExecutable(true);

        CursorCliAdapter adapter = new CursorCliAdapter(
                new ProcessInvoker.RealProcessInvoker(), stub.toString());
        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(SequenceProcessInvoker.ok("OK")));

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-cur")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed("story-cur"))
                        .allowedFile("src/Stub.java")
                        .devAdapter(adapter)
                        .allowReviewFixture(true)
                        .build(),
                invoker);

        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertTrue(Files.isRegularFile(ws.resolve("src/Stub.java")));
        assertTrue(result.spine.adapterInvoked);
        String audit = new String(Files.readAllBytes(
                ws.resolve(".story/story-cur/execution/adapter-dev-round-1.md")),
                StandardCharsets.UTF_8);
        assertTrue(audit.contains("adapter: cursor-cli"));
        assertTrue(audit.contains("submitted_once: true"));
        assertEquals("adapter_spine_wiring", result.spine.signoffClaim());
        assertEquals("model_cli", result.spine.adapterKind);
    }

    @Test
    void claudeCliStubBinaryOnSpine() throws Exception {
        Path ws = prepare("claude-stub");
        Path stub = temp.resolve("fake-claude");
        Files.write(stub, (""
                + "#!/usr/bin/env bash\n"
                + "mkdir -p \"$PWD/src\"\n"
                + "echo 'class Stub {}' > \"$PWD/src/Stub.java\"\n"
                + "echo stub-ok\n"
                + "exit 0\n").getBytes(StandardCharsets.UTF_8));
        stub.toFile().setExecutable(true);

        ClaudeCliAdapter adapter = new ClaudeCliAdapter(
                new ProcessInvoker.RealProcessInvoker(), stub.toString());
        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(SequenceProcessInvoker.ok("OK")));

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-cla")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed("story-cla"))
                        .allowedFile("src/Stub.java")
                        .devAdapter(adapter)
                        .allowReviewFixture(true)
                        .build(),
                invoker);

        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertTrue(Files.isRegularFile(ws.resolve("src/Stub.java")));
        assertTrue(result.spine.adapterInvoked);
        String audit = new String(Files.readAllBytes(
                ws.resolve(".story/story-cla/execution/adapter-dev-round-1.md")),
                StandardCharsets.UTF_8);
        assertTrue(audit.contains("adapter: claude-cli"));
        assertTrue(audit.contains("submitted_once: true"));
        assertEquals("adapter_spine_wiring", result.spine.signoffClaim());
        assertEquals("model_cli", result.spine.adapterKind);
    }

    @Test
    void adapterNextStageHintRejectedWithoutRetry() throws Exception {
        Path ws = prepare("adapter-hint");
        AtomicInteger calls = new AtomicInteger();
        FunctionalModelCliAdapter adapter = new FunctionalModelCliAdapter("hint-dev", request -> {
            calls.incrementAndGet();
            return AdapterResult.ok(
                    0, "ok", "", Collections.singletonMap("next_stage", "REVIEW"));
        });
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> PathwayRunner.run(
                        PathwayRunner.Config.builder(ws, "story-hint")
                                .script(Script.V3)
                                .suite("A")
                                .seedPath(seed("story-hint"))
                                .allowedFile("src/A.java")
                                .devAdapter(adapter)
                                .build(),
                        invoker));
        assertEquals(1, calls.get());
        assertTrue(ex.getMessage().contains("must not decide next stage"));
    }

    private Path prepare(String tag) throws Exception {
        Path ws = temp.resolve(tag);
        Files.createDirectories(ws);
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "--allow-empty", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        return ws;
    }

    private Path seed(String id) throws Exception {
        Path seed = temp.resolve(id + "-seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac for " + id + "\n")
                .getBytes(StandardCharsets.UTF_8));
        return seed;
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException("cmd failed " + java.util.Arrays.toString(argv) + " " + out.stderr);
        }
    }
}
