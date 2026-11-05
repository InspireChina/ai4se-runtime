package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * max=1 controlled failures must settle rounds_used and clear in-flight current_round so resume
 * does not silently grant another Development round.
 */
final class ResumeAfterControlledFailDoesNotExpandBudgetTest {

    @TempDir
    Path temp;

    @Test
    void maxOneAdapterFailResumeDoesNotGrantExtraRound() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertNoExtraRoundOnResume(
                "adapter",
                ResumeFixtures.failingAdapterDev(calls),
                calls,
                ResumeFixtures.passingVerifier(),
                "FAILED_ADAPTER");
    }

    @Test
    void maxOnePolicyFailResumeDoesNotGrantExtraRound() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertNoExtraRoundOnResume(
                "policy",
                ResumeFixtures.noChangeDev(calls),
                calls,
                ResumeFixtures.passingVerifier(),
                "FAILED_POLICY");
    }

    @Test
    void maxOneEnvironmentFailResumeDoesNotGrantExtraRound() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertNoExtraRoundOnResume(
                "env",
                ResumeFixtures.dev(calls),
                calls,
                ResumeFixtures.envFailVerifier(),
                "FAILED_ENVIRONMENT");
    }

    private void assertNoExtraRoundOnResume(
            String name,
            FunctionalModelCliAdapter failingDev,
            AtomicInteger firstCalls,
            ProcessInvoker firstVerifier,
            String expectedTerminal) throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "ctrl-" + name);
        Path seed = ResumeFixtures.seed(temp, "ctrl-" + name);
        String storyId = "story-ctrl-" + name;

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
                    firstVerifier);
        } catch (StageGateException e) {
            assertTrue(e.getMessage().contains(expectedTerminal), e.getMessage());
        }

        assertEquals(1, ledger.readState().roundsUsed, name + " must consume the only round");
        assertEquals(0, ledger.readState().currentRound, name + " must clear in-flight round");
        assertEquals(1, ledger.readState().maxDevRoundsOrMinusOne);
        assertTrue(firstCalls.get() >= 1, name);

        AtomicInteger resumeCalls = new AtomicInteger();
        try {
            PathwayRunner.run(
                    ResumeFixtures.base(
                                    ws,
                                    storyId,
                                    seed,
                                    ledger,
                                    ResumeFixtures.analysis(storyId, new AtomicInteger()),
                                    ResumeFixtures.plan(storyId, new AtomicInteger()),
                                    ResumeFixtures.dev(resumeCalls),
                                    ResumeFixtures.review(storyId, new AtomicInteger()))
                            .boundedDeliveryLoop(10)
                            .productionResume(true)
                            .build(),
                    ResumeFixtures.passingVerifier());
        } catch (StageGateException e) {
            assertTrue(
                    e.getMessage().contains("FAILED_VERIFICATION_BUDGET")
                            || e.getMessage().contains(expectedTerminal),
                    e.getMessage());
        }
        assertEquals(0, resumeCalls.get(), name + " resume must not run another Development round");
        assertEquals(1, ledger.readState().roundsUsed);
        assertEquals(0, ledger.readState().currentRound);
    }
}
