package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BoundedDeliveryLoopMaxRoundsTest {

    @TempDir
    Path temp;

    @Test
    void stopsAtBudgetWhenAlwaysFailing() throws Exception {
        String storyId = "story-budget";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "budget", storyId);
        AtomicInteger calls = new AtomicInteger();
        // Different content each round → not NO_PROGRESS; hits budget instead.
        FunctionalModelCliAdapter dev = BoundedLoopFixtures.fixingDev(storyId, calls);

        BoundedLoopResult result = BoundedLoopFixtures.runLoop(
                ws, storyId, 2, dev, BoundedLoopFixtures.alwaysFailingVerifier());

        assertEquals(RunStopReason.FAILED_VERIFICATION_BUDGET, result.reason);
        assertEquals(2, result.developmentRoundsUsed);
        assertEquals(2, calls.get());
        assertTrue(BoundedLoopFixtures.hasDefects(ws, storyId));
        assertEquals(2, BoundedLoopFixtures.countDevPackages(ws, storyId));
        assertEquals(2, BoundedLoopFixtures.countVerifyPackages(ws, storyId));
    }
}
