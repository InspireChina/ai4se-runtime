package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BoundedDeliveryLoopEnvFailDoesNotCreateDefectTest {

    @TempDir
    Path temp;

    @Test
    void envFailStopsWithoutDefectPackage() throws Exception {
        String storyId = "story-env";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "env", storyId);
        AtomicInteger calls = new AtomicInteger();
        FunctionalModelCliAdapter dev = BoundedLoopFixtures.fixingDev(storyId, calls);

        BoundedLoopResult result = BoundedLoopFixtures.runLoop(
                ws, storyId, 3, dev, BoundedLoopFixtures.envFailVerifier());

        assertEquals(RunStopReason.FAILED_ENVIRONMENT, result.reason);
        assertEquals(1, result.developmentRoundsUsed);
        assertEquals(1, calls.get());
        assertFalse(BoundedLoopFixtures.hasDefects(ws, storyId));
    }
}
