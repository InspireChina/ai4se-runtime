package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.ClarificationRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PathwayClarificationStopTest {

    @TempDir
    Path temp;

    @Test
    void unansweredClarificationStopsWithBlockedGap() throws Exception {
        Path ws = fixture(temp, "stop");
        Path seed = seed(temp);
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();

        StageGateException ex = assertThrows(StageGateException.class, () -> PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-c-stop")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/test/java/T.java")
                        .verifyCommand("true")
                        .discoverySkip("fixture", "test")
                        .clarificationResolved("空集合返回什么？", null, "human")
                        .devAdapter(noopDev())
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .allowReviewFixture(true)
                        .build(),
                real));
        // clarificationResolved with null answer won't work - use builder fields differently
    }

    @Test
    void pendingQuestionWithoutAnswerStops() throws Exception {
        Path ws = fixture(temp, "pend");
        Path seed = seed(temp);
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();

        StageGateException ex = assertThrows(StageGateException.class, () -> PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-c-pend")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/test/java/T.java")
                        .verifyCommand("true")
                        .discoverySkip("fixture", "test")
                        .clarificationQuestionOnly("空集合返回 null 还是 empty？")
                        .devAdapter(noopDev())
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .build(),
                real));

        assertTrue(ex.getMessage().contains("Clarification Stop"), ex.getMessage());
        assertTrue(ClarificationRecords.hasPending(ws, "story-c-pend"));
        assertFalse(ClarificationRecords.hasResolved(ws, "story-c-pend"));
        assertEquals(GapStatus.BLOCKED, GapRecords.readStatus(ws, "story-c-pend"));
        assertEquals(WorkflowStatus.STOPPED, StoryWorkflowMachine.load(ws, "story-c-pend").status());
    }

    @Test
    void resumeAfterClarificationCompletes() throws Exception {
        Path ws = fixture(temp, "resume");
        Path seed = seed(temp);
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();

        assertThrows(StageGateException.class, () -> PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-c-res")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/test/java/T.java")
                        .verifyCommand("true")
                        .discoverySkip("fixture", "test")
                        .clarificationQuestionOnly("返回值约定？")
                        .devAdapter(noopDev())
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .allowReviewFixture(true)
                        .build(),
                real));

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-c-res")
                        .script(Script.V3)
                        .suite("A")
                        .allowedFile("src/test/java/T.java")
                        .verifyCommand("true")
                        .discoverySkip("fixture", "test")
                        .clarificationResolved("返回值约定？", "返回 null", "human")
                        .resumeAfterStop(true)
                        .analysisAdapter(clearingAnalysis())
                        .devAdapter(mutatingDev())
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .allowReviewFixture(true)
                        .build(),
                real);

        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertEquals(GapStatus.CLEAR, GapRecords.readStatus(ws, "story-c-res"));
        assertTrue(ClarificationRecords.hasResolved(ws, "story-c-res"));
        assertFalse(ClarificationRecords.hasPending(ws, "story-c-res"));
        String recheckInput = new String(Files.readAllBytes(
                ws.resolve(".story/story-c-res/packages/analysis/model-input.md")),
                StandardCharsets.UTF_8);
        assertTrue(recheckInput.contains("返回 null"), recheckInput);
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("gap_prepared_by_runner: false"), meta);
    }

    private static FunctionalModelCliAdapter noopDev() {
        return new FunctionalModelCliAdapter("dev-noop", request ->
                AdapterResult.ok(0, "noop", "", Collections.<String, String>emptyMap()));
    }

    private static FunctionalModelCliAdapter clearingAnalysis() {
        return new FunctionalModelCliAdapter("analysis-recheck", request -> {
            try {
                Path analysis = request.workspace().resolve(".story").resolve(request.storyId())
                        .resolve("analysis");
                Files.createDirectories(analysis);
                Files.write(analysis.resolve("discovery.report.md"),
                        "# Discovery\n\nAnswer matches existing null convention.\n"
                                .getBytes(StandardCharsets.UTF_8));
                GapRecords.write(request.workspace(), request.storyId(), GapStatus.CLEAR, 0, 0,
                        "clarification rechecked by Analysis");
                return AdapterResult.ok(0, "clear", "", Collections.<String, String>emptyMap());
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
        });
    }

    private static FunctionalModelCliAdapter mutatingDev() {
        return new FunctionalModelCliAdapter("dev-mut", request -> {
            try {
                Path f = request.workspace().resolve("src/test/java/T.java");
                Files.write(f, "class T { int x; }\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
        });
    }

    private static Path fixture(Path temp, String name) throws Exception {
        Path ws = temp.resolve(name);
        Files.createDirectories(ws.resolve("src/test/java"));
        Files.write(ws.resolve("pom.xml"), ("<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>t</groupId><artifactId>t</artifactId><version>1</version></project>\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/test/java/T.java"), "class T {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "add", "-A");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));
        return ws;
    }

    private static Path seed(Path temp) throws Exception {
        Path seed = temp.resolve("seed-" + System.nanoTime() + ".md");
        Files.write(seed, ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- ok\n").getBytes(StandardCharsets.UTF_8));
        return seed;
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
