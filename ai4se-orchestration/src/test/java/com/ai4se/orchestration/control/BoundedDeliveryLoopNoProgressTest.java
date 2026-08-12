package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BoundedDeliveryLoopNoProgressTest {

    @TempDir
    Path temp;

    @Test
    void sameFingerprintAndDiffStopsAsNoProgress() throws Exception {
        String storyId = "story-noprog";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "noprog", storyId);
        AtomicInteger calls = new AtomicInteger();
        FunctionalModelCliAdapter stuck = BoundedLoopFixtures.stuckDev(calls);

        BoundedLoopResult result = BoundedLoopFixtures.runLoop(
                ws, storyId, 5, stuck, BoundedLoopFixtures.alwaysFailingVerifier());

        assertEquals(RunStopReason.FAILED_NO_PROGRESS, result.reason);
        assertEquals(2, result.developmentRoundsUsed);
        assertEquals(2, calls.get());
        assertTrue(BoundedLoopFixtures.hasDefects(ws, storyId));
    }
}
