package com.ai4se.orchestration.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.control.RoundOutcome;
import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProductionRunScorecardTest {

    @TempDir
    Path temp;

    @Test
    void collectsAwaitingAcceptanceAndIndependentSafetyFlags() throws Exception {
        Path ws = prepareGitWorkspace(temp.resolve("ws"));
        String storyId = "story-score-1";
        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun("src/main/java/A.java", 3);
        ledger.stageCompleted(WorkflowStage.ANALYSIS);
        ledger.stageCompleted(WorkflowStage.PLANNING);
        ledger.onRoundStarted(1);
        ledger.onRoundCompleted(1, RoundOutcome.VERIFY_PASS, null, null);
        ledger.stageCompleted(WorkflowStage.DEVELOPMENT);
        ledger.stageCompleted(WorkflowStage.VERIFICATION);
        ledger.markTerminal(ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE, "ok");

        Path pkg = ws.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("development").resolve("round-1");
        Files.createDirectories(pkg.resolve("slices"));
        Files.write(pkg.resolve("manifest.md"), "# m\n".getBytes(StandardCharsets.UTF_8));
        Files.write(
                pkg.resolve("slices/acceptance.md"),
                "# ac\n".getBytes(StandardCharsets.UTF_8));
        Path verify = ws.resolve(".story").resolve(storyId).resolve("verification");
        Files.createDirectories(verify);
        Files.write(
                verify.resolve("report-round-1.md"),
                ("# Verify\n\n- outcome: PASS\n").getBytes(StandardCharsets.UTF_8));
        Path exec = ws.resolve(".story").resolve(storyId).resolve("execution");
        Files.createDirectories(exec);
        Files.write(
                exec.resolve("adapter-dev-round-1.md"),
                ("# audit\n").getBytes(StandardCharsets.UTF_8));

        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        Files.write(
                ws.resolve("src/main/java/A.java"),
                "class A { int x=1; }\n".getBytes(StandardCharsets.UTF_8));
        String sha = WorkspaceGit.commitLocal(
                ws, invoker, Collections.singletonList("src/main/java/A.java"), "ai4se: score");
        Path delivery = ws.resolve(".story").resolve(storyId).resolve("delivery");
        Files.createDirectories(delivery);
        Files.write(
                delivery.resolve(DeliveryRecords.FILE),
                ("# Delivery\n\n- mode: LOCAL_COMMIT\n- commit_sha: " + sha
                        + "\n- pushed: false\n- observed: true\n- committed_now: true\n")
                        .getBytes(StandardCharsets.UTF_8));

        ProductionRunScorecard.ExperimentHints hints = ProductionRunScorecard.ExperimentHints.empty();
        hints.pairId = "pair-1";
        hints.arm = "B";
        hints.baselineCommit = "deadbeef";
        hints.modelId = "cursor-test";
        hints.humanInterventions = "0";
        hints.diffVerdict = "accept";
        hints.wallTimeSec = "100";
        hints.notes = "unit";

        ProductionRunScorecard.Metrics m = ProductionRunScorecard.collect(ws, storyId, invoker, hints);
        assertEquals(storyId, m.storyId);
        assertTrue(m.awaitingAcceptance);
        assertEquals(0, m.exitCode);
        assertEquals(1, m.roundsUsed);
        assertEquals("1", m.commitExists);
        assertEquals("1", m.commitScopeOk);
        assertEquals("1", m.verifyPassBeforeReview);
        assertTrue(m.p1Bytes > 0);
        assertTrue(m.packageBytes >= m.p1Bytes);
        assertEquals("src/main/java/A.java", m.writeScope);

        String csv = ProductionRunScorecard.toCsvLine(m);
        assertTrue(csv.startsWith("pair-1," + storyId + ",B,"));
        assertTrue(ProductionRunScorecard.CSV_HEADER.contains("pair_id"));
        assertTrue(ProductionRunScorecard.CSV_HEADER.contains("commit_scope_ok"));
        assertTrue(ProductionRunScorecard.CSV_HEADER.contains("verify_pass_before_review"));
        assertTrue(ProductionRunScorecard.CSV_HEADER.contains("input_tokens"));
        assertTrue(m.toHumanSummary().contains("verify_pass_before_review=1"));
    }

    @Test
    void readOnlyCollectDoesNotCreateMissingStory() throws Exception {
        Path ws = prepareGitWorkspace(temp.resolve("missing"));
        Path before = ws.resolve(".story");
        assertTrue(!Files.exists(before) || listStoryIds(before).isEmpty());
        assertThrows(
                StageGateException.class,
                () -> ProductionRunScorecard.collect(ws, "story-never-existed"));
        assertTrue(!Files.exists(ws.resolve(".story").resolve("story-never-existed")));
    }

    @Test
    void rejectsPathEscapeStoryId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionRunScorecard.requireSafeStoryId("../escape"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionRunScorecard.requireSafeStoryId("a/b"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionRunScorecard.requireSafeStoryId("/abs"));
    }

    private static Path prepareGitWorkspace(Path ws) throws Exception {
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.write(
                ws.resolve("src/main/java/A.java"),
                "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        run(invoker, ws, "git", "init", "--template=");
        run(invoker, ws, "git", "add", "-A");
        run(invoker, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        return ws;
    }

    private static java.util.List<String> listStoryIds(Path storyRoot) throws Exception {
        if (!Files.isDirectory(storyRoot)) {
            return Collections.emptyList();
        }
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (Path p : Files.newDirectoryStream(storyRoot)) {
            if (Files.isDirectory(p)) {
                out.add(p.getFileName().toString());
            }
        }
        return out;
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out =
                invoker.run(Arrays.asList(argv), ws, null, Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
