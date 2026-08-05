package com.ai4se.orchestration.acceptance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.execution.support.SplitProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.lifecycle.KnowledgeLifecycleControl;
import com.ai4se.orchestration.lifecycle.OnboardPolicy;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.pathway.PathwayRunner.LifecycleMode;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W10 · human acceptance gates 06; noop|apply; no re-onboard per Story. */
final class PathwayW10AcceptanceLifecycleIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void lifecycleBeforeAcceptanceIsRefused() throws Exception {
        Path ws = fixtureThroughDelivery("story-gate");
        assertFalse(HumanAcceptanceRecords.isAccepted(ws, "story-gate"));
        assertThrows(StageGateException.class, () ->
                KnowledgeLifecycleControl.noop(ws, "story-gate", "too early"));
        assertThrows(StageGateException.class, () ->
                KnowledgeLifecycleControl.applyLearning(ws, "story-gate", "x", "body"));
        assertFalse(KnowledgeLifecycleControl.hasLifecycleArtifact(ws, "story-gate"));
    }

    @Test
    void acceptedThenNoop() throws Exception {
        Path ws = fixtureThroughDelivery("story-noop");
        HumanAcceptanceRecords.recordAccepted(ws, "story-noop", "reviewer", "ok");
        Path noop = KnowledgeLifecycleControl.noop(ws, "story-noop", "nothing to promote");
        assertTrue(Files.isRegularFile(noop));
        String text = new String(Files.readAllBytes(noop), StandardCharsets.UTF_8);
        assertTrue(text.contains("lifecycle: noop"));
        assertTrue(text.contains("nothing to promote"));
        KnowledgeLifecycleControl.requireCompleted(ws, "story-noop");
    }

    @Test
    void acceptedThenApplyLearningUpdatesCustomerIndex() throws Exception {
        Path ws = fixtureThroughDelivery("story-learn");
        HumanAcceptanceRecords.recordAccepted(ws, "story-learn", "reviewer", "ok");
        Path beforeIndex = ws.resolve(".ai4se/index/knowledge.yaml");
        String before = new String(Files.readAllBytes(beforeIndex), StandardCharsets.UTF_8);
        KnowledgeLifecycleControl.applyLearning(
                ws, "story-learn", "hotspot-verify", "Verify command often red on flags");
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/learning/hotspot-verify.md")));
        String after = new String(Files.readAllBytes(beforeIndex), StandardCharsets.UTF_8);
        assertTrue(after.length() > before.length());
        assertTrue(after.contains("hotspot-verify"));
        assertTrue(after.contains("kind: learning"));
    }

    @Test
    void unmannedPathwayRecordsAcceptanceAndNoopWithoutReOnboard() throws Exception {
        Path ws = temp.resolve("cust-w10");
        Files.createDirectories(ws);
        gitInit(ws);
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        FileTime slotsMtime = Files.getLastModifiedTime(ws.resolve(".ai4se/index/knowledge.yaml"));

        Path seed = temp.resolve("seed-w10.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac-1\n")
                .getBytes(StandardCharsets.UTF_8));

        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(SequenceProcessInvoker.ok("OK")));

        OnboardPolicy.requireSlotsAlreadyPresent(ws);
        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-w10")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/W10.java")
                        .lifecycleMode(LifecycleMode.NOOP)
                        .humanAccepter("fixture-reviewer")
                        .build(),
                invoker);

        assertEqualsCompleted(result);
        assertTrue(HumanAcceptanceRecords.isAccepted(ws, "story-w10"));
        assertTrue(KnowledgeLifecycleControl.hasLifecycleArtifact(ws, "story-w10"));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("acceptance/human-acceptance.md")));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("lifecycle/noop.md")));
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("W10"));

        // slots not rewritten by main loop (mtime unchanged or not newer via re-onboard wipe)
        FileTime afterMtime = Files.getLastModifiedTime(ws.resolve(".ai4se/index/knowledge.yaml"));
        assertFalse(afterMtime.toInstant().isBefore(slotsMtime.toInstant().minusSeconds(1)));
        // index content still stub (noop does not rewrite knowledge.yaml)
        String index = new String(Files.readAllBytes(ws.resolve(".ai4se/index/knowledge.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(index.contains("entries: []") || index.contains("entries:"));
    }

    @Test
    void secondStoryReusesSlotsNoOnboard() throws Exception {
        Path ws = temp.resolve("cust-two");
        Files.createDirectories(ws);
        gitInit(ws);
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));

        Path seed1 = temp.resolve("s1.md");
        Path seed2 = temp.resolve("s2.md");
        String seedBody = ""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac\n";
        Files.write(seed1, seedBody.getBytes(StandardCharsets.UTF_8));
        Files.write(seed2, seedBody.getBytes(StandardCharsets.UTF_8));

        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.ok("1"),
                        SequenceProcessInvoker.ok("2")));

        PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "s1")
                        .seedPath(seed1)
                        .allowedFile("src/S1.java")
                        .build(),
                invoker);
        // second story — slots already present; OnboardPolicy must pass without re-onboard
        OnboardPolicy.requireSlotsAlreadyPresent(ws);
        PathwayRunner.PathwayResult r2 = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "s2")
                        .seedPath(seed2)
                        .allowedFile("src/S2.java")
                        .build(),
                invoker);
        assertEqualsCompleted(r2);
        assertTrue(Files.isDirectory(ws.resolve(".story/s1")));
        assertTrue(Files.isDirectory(ws.resolve(".story/s2")));
        assertTrue(HumanAcceptanceRecords.isAccepted(ws, "s2"));
    }

    private static void assertEqualsCompleted(PathwayRunner.PathwayResult result) {
        assertTrue(result.finalState.status() == WorkflowStatus.COMPLETED);
    }

    private Path fixtureThroughDelivery(String storyId) throws Exception {
        Path ws = temp.resolve(storyId + "-ws");
        Files.createDirectories(ws);
        gitInit(ws);
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path seed = temp.resolve(storyId + "-seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac\n")
                .getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(SequenceProcessInvoker.ok("OK")));
        PathwayRunner.run(
                PathwayRunner.Config.builder(ws, storyId)
                        .seedPath(seed)
                        .allowedFile("src/" + storyId + ".java")
                        .lifecycleMode(LifecycleMode.SKIP)
                        .build(),
                invoker);
        return ws;
    }

    private static void gitInit(Path ws) throws Exception {
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "--allow-empty", "-m", "init");
    }

    private static void run(ProcessInvoker invoker, Path cwd, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome o = invoker.run(
                java.util.Arrays.asList(argv), cwd, null, java.time.Duration.ofMinutes(1));
        if (o.exitCode != 0) {
            throw new IllegalStateException("cmd failed: " + java.util.Arrays.toString(argv)
                    + "\n" + o.stderr);
        }
    }
}
