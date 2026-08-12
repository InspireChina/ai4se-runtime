package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.pathway.PathwayRunner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResumeDoesNotDuplicateCommitTest {

    @TempDir
    Path temp;

    @Test
    void resumeReusesExistingLocalCommit() throws Exception {
        Path ws = ResumeFixtures.prepareWorkspace(temp, "rc");
        Path seed = ResumeFixtures.seed(temp, "rc");
        String storyId = "story-resume-commit";
        AtomicInteger analysisCalls = new AtomicInteger();
        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger devCalls = new AtomicInteger();
        AtomicInteger reviewCalls = new AtomicInteger();

        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun(ResumeFixtures.ALLOWED, 3);

        PathwayRunner.PathwayResult first = PathwayRunner.run(
                ResumeFixtures.base(
                                ws,
                                storyId,
                                seed,
                                ledger,
                                ResumeFixtures.analysis(storyId, analysisCalls),
                                ResumeFixtures.plan(storyId, planCalls),
                                ResumeFixtures.dev(devCalls),
                                ResumeFixtures.review(storyId, reviewCalls))
                        .build(),
                ResumeFixtures.passingVerifier());

        String sha = first.commitShaOrNull;
        assertTrue(sha != null && !sha.isEmpty());
        assertTrue(DeliveryRecords.hasLocalCommit(ws, storyId));
        String deliveryBefore = new String(Files.readAllBytes(
                DeliveryRecords.deliveryDir(ws, storyId).resolve(DeliveryRecords.FILE)),
                StandardCharsets.UTF_8);

        PathwayRunner.PathwayResult second = PathwayRunner.run(
                ResumeFixtures.base(
                                ws,
                                storyId,
                                seed,
                                ledger,
                                ResumeFixtures.analysis(storyId, analysisCalls),
                                ResumeFixtures.plan(storyId, planCalls),
                                ResumeFixtures.dev(devCalls),
                                ResumeFixtures.review(storyId, reviewCalls))
                        .productionResume(true)
                        .build(),
                ResumeFixtures.passingVerifier());

        assertEquals(sha, second.commitShaOrNull);
        assertEquals(sha, DeliveryRecords.readCommitShaOrNull(ws, storyId));
        String deliveryAfter = new String(Files.readAllBytes(
                DeliveryRecords.deliveryDir(ws, storyId).resolve(DeliveryRecords.FILE)),
                StandardCharsets.UTF_8);
        assertTrue(deliveryAfter.contains("committed_now: false")
                        || deliveryBefore.contains("committed_now: true"),
                deliveryAfter);
        // SHA must not change — no second git commit created for delivery.
        assertTrue(deliveryAfter.contains(sha));
    }
}
