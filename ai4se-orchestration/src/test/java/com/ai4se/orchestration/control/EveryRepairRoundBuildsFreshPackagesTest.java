package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.orchestration.verification.VerifyPackageBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EveryRepairRoundBuildsFreshPackagesTest {

    @TempDir
    Path temp;

    @Test
    void eachRoundCreatesDistinctDevAndVerifyPackages() throws Exception {
        String storyId = "story-fresh-pkg";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "fresh", storyId);
        AtomicInteger calls = new AtomicInteger();
        FunctionalModelCliAdapter dev = BoundedLoopFixtures.fixingDev(storyId, calls);

        BoundedLoopResult result = BoundedLoopFixtures.runLoop(
                ws, storyId, 3, dev, BoundedLoopFixtures.failThenPassVerifier());

        assertEquals(RunStopReason.PASSED_VERIFICATION, result.reason);
        assertEquals(2, result.developmentRoundsUsed);

        assertTrue(BoundedLoopFixtures.countDevPackages(ws, storyId) >= 2);
        assertTrue(BoundedLoopFixtures.countVerifyPackages(ws, storyId) >= 2);
        assertTrue(Files.isRegularFile(
                VerifyPackageBuilder.packageDir(ws, storyId, 1).resolve("manifest.md")));
        assertTrue(Files.isRegularFile(
                VerifyPackageBuilder.packageDir(ws, storyId, 2).resolve("manifest.md")));
        assertTrue(
                BoundedLoopFixtures.anyDevPackageHasDefectRef(ws, storyId),
                "repair round Dev package must embed Defect as P1");
    }
}
