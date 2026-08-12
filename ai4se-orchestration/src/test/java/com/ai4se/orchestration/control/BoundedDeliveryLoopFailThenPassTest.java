package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.verification.VerifyPackageBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        assertEquals(2, BoundedLoopFixtures.countDevPackages(ws, storyId));
        assertEquals(2, BoundedLoopFixtures.countVerifyPackages(ws, storyId));
        assertTrue(Files.isRegularFile(
                DevPackageBuilder.packageDir(ws, storyId, 1).resolve("manifest.md")));
        assertTrue(Files.isRegularFile(
                DevPackageBuilder.packageDir(ws, storyId, 2).resolve("manifest.md")));
        assertTrue(Files.isRegularFile(
                VerifyPackageBuilder.packageDir(ws, storyId, 1).resolve("manifest.md")));
        assertTrue(Files.isRegularFile(
                VerifyPackageBuilder.packageDir(ws, storyId, 2).resolve("manifest.md")));
        String round2 = new String(Files.readAllBytes(
                DevPackageBuilder.packageDir(ws, storyId, 2).resolve("manifest.md")),
                StandardCharsets.UTF_8);
        assertTrue(round2.contains("defect"), round2);
    }
}
