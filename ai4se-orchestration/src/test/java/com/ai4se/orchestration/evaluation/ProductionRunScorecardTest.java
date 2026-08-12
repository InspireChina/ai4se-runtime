package com.ai4se.orchestration.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.control.RoundOutcome;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProductionRunScorecardTest {

    @TempDir
    Path temp;

    @Test
    void collectsAwaitingAcceptanceAndPackageBytes() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws);
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
        Files.createDirectories(pkg);
        Files.write(pkg.resolve("manifest.md"), "# m\n".getBytes(StandardCharsets.UTF_8));
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

        ProductionRunScorecard.Metrics m = ProductionRunScorecard.collect(ws, storyId);
        assertEquals(storyId, m.storyId);
        assertTrue(m.awaitingAcceptance);
        assertEquals(0, m.exitCode);
        assertEquals(1, m.roundsUsed);
        assertEquals(3, m.maxDevRoundsOrMinusOne);
        assertEquals(RoundOutcome.VERIFY_PASS.name(), m.lastRoundOutcomeOrNull);
        assertEquals(1, m.verifyReportRounds);
        assertEquals(1, m.devPackages);
        assertEquals(1, m.adapterAudits);
        assertTrue(m.packageBytes > 0);

        String csv = ProductionRunScorecard.toCsvLine(m, "B", "0", "0", "accept", "100", "unit");
        assertTrue(csv.startsWith(storyId + ",B,"));
        assertTrue(csv.contains(",1,1,3,VERIFY_PASS,"));
        assertTrue(ProductionRunScorecard.CSV_HEADER.contains("story_id"));
        assertTrue(m.toHumanSummary().contains("awaiting_acceptance=true"));
    }
}
