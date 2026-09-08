package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.verification.VerifyPackageBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
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
        assertEquals(1, BoundedLoopFixtures.countDevPackages(ws, storyId));
        assertEquals(1, BoundedLoopFixtures.countVerifyPackages(ws, storyId));
        assertTrue(Files.isRegularFile(
                DevPackageBuilder.packageDir(ws, storyId, 1).resolve("manifest.md")));
        assertTrue(Files.isRegularFile(
                VerifyPackageBuilder.packageDir(ws, storyId, 1).resolve("manifest.md")));
        String audit = new String(Files.readAllBytes(
                ws.resolve(".story/" + storyId + "/execution/adapter-dev-round-1.md")),
                StandardCharsets.UTF_8);
        assertTrue(audit.contains("round-1"), audit);
    }

    @Test
    void frozenAcceptanceProbesPermitLegacyRepositoryWithoutGeneralTestEntry() throws Exception {
        String storyId = "story-probes-only";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "probes-only", storyId);
        Files.write(ws.resolve(".ai4se/repository/entries.yaml"), "build:\n  - true\n"
                .getBytes(StandardCharsets.UTF_8));
        Path root = ws.resolve(".ai4se/acceptance-probes").resolve(storyId);
        Files.createDirectories(root);
        Path probe = root.resolve("ac1.sh");
        Files.write(probe, "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
        String relative = ".ai4se/acceptance-probes/" + storyId + "/ac1.sh";
        Files.write(root.resolve("probes.properties"), (
                "ac.count=1\n"
                        + "ac.1.path=" + relative + "\n"
                        + "ac.1.command=sh " + relative + "\n"
                        + "ac.1.sha256=" + sha256(probe) + "\n").getBytes(StandardCharsets.UTF_8));

        AtomicInteger calls = new AtomicInteger();
        BoundedLoopResult result = BoundedDeliveryLoop.run(
                ws,
                storyId,
                1,
                BoundedLoopFixtures.fixingDev(storyId, calls),
                java.time.Duration.ofMinutes(2),
                null,
                Collections.<String>emptyList(),
                "implement within Allowed",
                BoundedLoopFixtures.passingVerifier());

        assertEquals(RunStopReason.PASSED_VERIFICATION, result.reason);
        assertEquals(1, calls.get());
    }

    @Test
    void noRepositoryEntryAndNoFrozenProbeStillRefusesBeforeDevelopment() throws Exception {
        String storyId = "story-no-oracle";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "no-oracle", storyId);
        Files.write(ws.resolve(".ai4se/repository/entries.yaml"), "build:\n  - true\n"
                .getBytes(StandardCharsets.UTF_8));
        AtomicInteger calls = new AtomicInteger();

        assertThrows(StageGateException.class, () -> BoundedDeliveryLoop.run(
                ws,
                storyId,
                1,
                BoundedLoopFixtures.fixingDev(storyId, calls),
                java.time.Duration.ofMinutes(2),
                null,
                Collections.<String>emptyList(),
                "implement within Allowed",
                BoundedLoopFixtures.passingVerifier()));
        assertEquals(0, calls.get());
    }

    @Test
    void nonZeroDevelopmentExitWithInScopeDiffStillReachesVerification() throws Exception {
        String storyId = "story-partial-adapter-exit";
        Path ws = BoundedLoopFixtures.prepareAtDevelopment(temp, "partial-adapter-exit", storyId);
        FunctionalModelCliAdapter partial = new FunctionalModelCliAdapter("partial", request -> {
            try {
                Files.write(request.workspace().resolve("src/main/java/A.java"),
                        "class A { int writtenBeforeExit=1; }\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(), Collections.<String, String>emptyMap());
            }
            return AdapterResult.failure(1, "partial", "agent command failed", "non-zero", Collections.<String, String>emptyMap());
        });

        BoundedLoopResult result = BoundedDeliveryLoop.run(
                ws,
                storyId,
                1,
                partial,
                java.time.Duration.ofMinutes(2),
                null,
                Collections.singletonList(BoundedLoopFixtures.VERIFY),
                "implement within Allowed",
                BoundedLoopFixtures.passingVerifier());

        assertEquals(RunStopReason.PASSED_VERIFICATION, result.reason);
        String note = new String(Files.readAllBytes(
                ws.resolve(".story").resolve(storyId).resolve("development/change-note.md")),
                StandardCharsets.UTF_8);
        assertTrue(note.contains("non-zero adapter exit"), note);
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) {
            out.append(String.format("%02x", b & 0xff));
        }
        return out.toString();
    }
}
