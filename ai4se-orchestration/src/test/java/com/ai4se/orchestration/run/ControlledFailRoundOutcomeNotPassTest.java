package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.control.RoundOutcome;
import com.ai4se.orchestration.pathway.PathwayRunner;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Controlled failures must settle with explicit RoundOutcome names — never outcome=PASS in JSONL.
 */
final class ControlledFailRoundOutcomeNotPassTest {

    @TempDir
    Path temp;

    @Test
    void adapterFailJsonlNeverRecordsOutcomePass() throws Exception {
        assertControlledFailOutcome(
                "adapter",
                ResumeFixtures.failingAdapterDev(new AtomicInteger()),
                ResumeFixtures.passingVerifier(),
                "FAILED_ADAPTER",
                RoundOutcome.FAILED_ADAPTER);
    }

    @Test
    void policyFailJsonlNeverRecordsOutcomePass() throws Exception {
        assertControlledFailOutcome(
                "policy",
                ResumeFixtures.noChangeDev(new AtomicInteger()),
                ResumeFixtures.passingVerifier(),
                "FAILED_POLICY",
                RoundOutcome.FAILED_POLICY);
    }

    @Test
    void environmentFailJsonlNeverRecordsOutcomePass() throws Exception {
        assertControlledFailOutcome(
                "env",
                ResumeFixtures.dev(new AtomicInteger()),
                ResumeFixtures.envFailVerifier(),
                "FAILED_ENVIRONMENT",
                RoundOutcome.FAILED_ENVIRONMENT);
    }

    private void assertControlledFailOutcome(
            String name,
            FunctionalModelCliAdapter failingDev,
            ProcessInvoker verifier,
            String expectedTerminal,
            RoundOutcome expectedOutcome) throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "outcome-" + name);
        Path seed = ResumeFixtures.seed(temp, "outcome-" + name);
        String storyId = "story-outcome-" + name;
        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 1);

        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, new AtomicInteger()),
                                    ResumeFixtures.plan(storyId, new AtomicInteger()),
                                    failingDev,
                                    ResumeFixtures.review(storyId, new AtomicInteger()))
                            .boundedDeliveryLoop(1)
                            .build(),
                    verifier);
        } catch (StageGateException e) {
            assertTrue(e.getMessage().contains(expectedTerminal), e.getMessage());
        }

        assertTrue(
                expectedOutcome.name().equals(ledger.readState().lastRoundOutcomeOrNull),
                ledger.readState().lastRoundOutcomeOrNull);
        boolean sawPass = false;
        boolean sawExpected = false;
        for (String line : ledger.readEventLines()) {
            if (!line.contains("\"type\":\"round_completed\"")) {
                continue;
            }
            assertFalse(
                    line.contains("outcome=PASS") || line.contains("outcome=VERIFY_PASS"),
                    "controlled fail must not look like PASS: " + line);
            if (line.contains("outcome=" + expectedOutcome.name())) {
                sawExpected = true;
            }
            if (line.contains("outcome=PASS")) {
                sawPass = true;
            }
        }
        assertFalse(sawPass);
        assertTrue(sawExpected, "expected round_completed outcome=" + expectedOutcome);
    }
}
