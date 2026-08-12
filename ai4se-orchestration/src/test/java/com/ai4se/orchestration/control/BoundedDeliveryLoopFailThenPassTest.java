package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BoundedDeliveryLoopFailThenPassTest {

    @TempDir
    Path temp;

    @Test
    void failThenPassWithoutRequiringFirstFail() throws Exception {
        String storyId = "story-fail-pass";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "failpass", storyId);
        AtomicInteger calls = new AtomicInteger();
        FunctionalModelCliAdapter dev = BoundedLoopFixtures.fixingDev(storyId, calls);

        BoundedLoopResult result = BoundedLoopFixtures.runLoop(
                ws, storyId, 3, dev, BoundedLoopFixtures.failThenPassVerifier());

        assertEquals(RunStopReason.PASSED_VERIFICATION, result.reason);
        assertEquals(2, result.developmentRoundsUsed);
        assertEquals(2, calls.get());
        assertTrue(BoundedLoopFixtures.hasDefects(ws, storyId));
        assertTrue(BoundedLoopFixtures.countDevPackages(ws, storyId) >= 2);
        assertTrue(BoundedLoopFixtures.countVerifyPackages(ws, storyId) >= 2);
    }
}
