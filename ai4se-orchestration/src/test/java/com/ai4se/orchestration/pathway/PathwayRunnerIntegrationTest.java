package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.execution.support.SplitProcessInvoker;
import com.ai4se.orchestration.pathway.PathwayRunner.DeliveryMode;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unmanned V3/V4 on A-suite fixture with real git commit + scripted verify. */
final class PathwayRunnerIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void unmannedV3LocalCommitAndEvidence() throws Exception {
        Path ws = temp.resolve("cust");
        Files.createDirectories(ws);
        gitInit(ws);
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));

        Path seed = temp.resolve("seed.md");
        Files.write(seed, (""
                + "## raw\nflag\n\n## goal\nflags\n\n## in_scope\n- api\n\n"
                + "## out_of_scope\n- ui\n\n## acceptance\n- isEnabled false when off\n")
                .getBytes(StandardCharsets.UTF_8));

        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(SequenceProcessInvoker.ok("TESTS OK")));

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-v3")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/FeatureFlags.java")
                        .verifyCommand("mvn -q test")
                        .deliveryMode(DeliveryMode.LOCAL_COMMIT)
                        .commitMessage("ai4se: story-v3")
                        .allowReviewFixture(true)
                        .build(),
                invoker);

        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertNotNull(result.commitShaOrNull);
        assertTrue(result.commitShaOrNull.matches("^[0-9a-f]{7,40}$"));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("meta.yaml")));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("audit-host.md")));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("delivery/delivery.md")));
        assertTrue(Files.isDirectory(result.evidenceRoot.resolve("sessions")));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("sessions/decisions.md")));
        assertTrue(Files.isDirectory(result.evidenceRoot.resolve("compress")));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("acceptance/human-acceptance.md")));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("lifecycle/noop.md")));
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("W10"));
        String delivery = new String(Files.readAllBytes(
                ws.resolve(".story/story-v3/delivery/delivery.md")), StandardCharsets.UTF_8);
        assertTrue(delivery.contains("committed_now: true"));
        assertTrue(delivery.contains("pushed: false"));
        assertTrue(Files.isRegularFile(ws.resolve("src/FeatureFlags.java")));
    }

    @Test
    void unmannedV4DefectLoopThenPass() throws Exception {
        Path ws = temp.resolve("cust4");
        Files.createDirectories(ws);
        gitInit(ws);
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));

        Path seed = temp.resolve("seed4.md");
        Files.write(seed, (""
                + "## raw\nx\n\n## goal\ny\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac-1\n")
                .getBytes(StandardCharsets.UTF_8));

        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.exit(1, "", "FAIL"),
                        SequenceProcessInvoker.ok("PASS")));

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-v4")
                        .script(Script.V4)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/A.java")
                        .allowReviewFixture(true)
                        .build(),
                invoker);

        assertEquals(Script.V4, result.script);
        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertTrue(Files.isDirectory(ws.resolve(".story/story-v4/defects")));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-v4/packages/verification/round-2/manifest.md")));
        assertTrue(Files.isDirectory(result.evidenceRoot.resolve("sessions")));
        assertTrue(Files.isDirectory(result.evidenceRoot.resolve("compress")));
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("script: V4"));
        assertTrue(meta.contains("W10"));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("lifecycle/noop.md")));
    }

    private static void gitInit(Path ws) throws Exception {
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        // empty template — avoid copying hooks (sandbox / CI friendly)
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "--allow-empty", "-m", "init");
        Files.write(ws.resolve("README.md"), "# fixture\n".getBytes(StandardCharsets.UTF_8));
        run(real, ws, "git", "add", "README.md");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "-m", "readme");
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(
                    "cmd failed " + java.util.Arrays.toString(argv) + " stderr=" + out.stderr);
        }
    }
}
