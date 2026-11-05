package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(Files.isDirectory(DevPackageBuilder.packageDir(ws, storyId, 3)));
        assertFalse(Files.isDirectory(VerifyPackageBuilder.packageDir(ws, storyId, 3)));

        String r1 = new String(Files.readAllBytes(
                DevPackageBuilder.packageDir(ws, storyId, 1).resolve("manifest.md")),
                StandardCharsets.UTF_8);
        String r2 = new String(Files.readAllBytes(
                DevPackageBuilder.packageDir(ws, storyId, 2).resolve("manifest.md")),
                StandardCharsets.UTF_8);
        assertTrue(r1.contains("round: 1"));
        assertTrue(r2.contains("round: 2"));
        assertTrue(r2.contains("defect"), "repair round Dev package must embed Defect as P1");
        assertTrue(Files.isRegularFile(
                DevPackageBuilder.packageDir(ws, storyId, 2).resolve("slices/defect-ref.md")));
    }
}
