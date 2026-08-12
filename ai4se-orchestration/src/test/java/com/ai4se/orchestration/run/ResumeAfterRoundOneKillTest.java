package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.control.FailureFingerprint;
import com.ai4se.orchestration.control.RoundProgressSink;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round 1 FAIL then simulated process kill: resume must not expand budget and must continue
 * round numbering (not restart at round 1 with a fresh full budget).
 */
final class ResumeAfterRoundOneKillTest {

    @TempDir
    Path temp;

    @Test
    void roundOneFailThenKillResumeKeepsBudgetAndContinuesRoundNumbers() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "kill-r1");
        Path seed = ResumeFixtures.seed(temp, "kill-r1");
        String storyId = "story-kill-r1";
        AtomicInteger analysisCalls = new AtomicInteger();
        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger stuckCalls = new AtomicInteger();
        AtomicInteger reviewCalls = new AtomicInteger();

        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 3);

        AtomicBoolean killArmed = new AtomicBoolean(true);
        RoundProgressSink killAfterRound1 = new RoundProgressSink() {
            @Override
            public void onRoundStarted(int round) throws IOException {
                ledger.onRoundStarted(round);
            }

            @Override
            public void onRoundCompleted(
                    int round, FailureFingerprint fingerprintOrNull, String businessDiffHashOrNull)
                    throws IOException {
                ledger.onRoundCompleted(round, fingerprintOrNull, businessDiffHashOrNull);
                if (round == 1 && killArmed.getAndSet(false)) {
                    throw new IOException("simulated process kill after round 1 FAIL");
                }
            }
        };

        IOException killed = null;
        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, analysisCalls),
                                    ResumeFixtures.plan(storyId, planCalls),
                                    ResumeFixtures.stuckDev(stuckCalls),
                                    ResumeFixtures.review(storyId, reviewCalls))
                            .boundedDeliveryLoop(3)
                            .roundProgressSink(killAfterRound1)
                            .build(),
                    ResumeFixtures.alwaysFailingVerifier());
        } catch (IOException e) {
            killed = e;
        }
        assertTrue(killed != null && killed.getMessage().contains("simulated process kill"),
                String.valueOf(killed));
        assertEquals(1, ledger.readState().roundsUsed, "round 1 FAIL must flush rounds_used before kill");
        assertEquals(0, ledger.readState().currentRound);
        assertEquals(3, ledger.readState().maxDevRoundsOrMinusOne);
        assertEquals(1, stuckCalls.get());
        assertTrue(Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 1)));
        assertTrue(!Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 2)));

        AtomicInteger fixCalls = new AtomicInteger();
        PathwayRunner.PathwayResult resumed = PathwayRunner.run(
                ResumeFixtures.base(
                                ws,
                                storyId,
                                seed,
                                ledger,
                                ResumeFixtures.analysis(storyId, analysisCalls),
                                ResumeFixtures.plan(storyId, planCalls),
                                ResumeFixtures.dev(fixCalls),
                                ResumeFixtures.review(storyId, reviewCalls))
                        .boundedDeliveryLoop(99)
                        .productionResume(true)
                        .build(),
                ResumeFixtures.passingVerifier());

        assertEquals(1, fixCalls.get(), "remaining budget is 2 rounds; first resume round should PASS");
        assertEquals(2, ledger.readState().roundsUsed);
        assertTrue(Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 2)));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/" + storyId + "/execution/adapter-dev-round-2.md")));
        assertTrue(!Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 3)),
                "must not restart from round 1 with a full 3-round budget");
        assertEquals(1, reviewCalls.get());
        assertTrue(ledger.hasCompleted(WorkflowStage.VERIFICATION));
        assertTrue(resumed.commitShaOrNull != null && !resumed.commitShaOrNull.isEmpty());
    }
}
