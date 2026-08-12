package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BoundedDeliveryLoopFirstPassTest {

    @TempDir
    Path temp;

    @Test
    void firstRoundPassProceedsWithoutDefect() throws Exception {
        String storyId = "story-first-pass";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "first", storyId);
        AtomicInteger calls = new AtomicInteger();
        FunctionalModelCliAdapter dev = BoundedLoopFixtures.fixingDev(storyId, calls);

        BoundedLoopResult result = BoundedLoopFixtures.runLoop(
                ws, storyId, 3, dev, BoundedLoopFixtures.passingVerifier());

        assertEquals(RunStopReason.PASSED_VERIFICATION, result.reason);
        assertEquals(1, result.developmentRoundsUsed);
        assertEquals(1, calls.get());
        assertFalse(BoundedLoopFixtures.hasDefects(ws, storyId));
        assertTrue(BoundedLoopFixtures.countDevPackages(ws, storyId) >= 1);
        assertTrue(BoundedLoopFixtures.countVerifyPackages(ws, storyId) >= 1);
    }
}
