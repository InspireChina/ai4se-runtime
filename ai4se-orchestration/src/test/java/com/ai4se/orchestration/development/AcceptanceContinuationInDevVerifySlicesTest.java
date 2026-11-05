package com.ai4se.orchestration.development;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.verification.VerifyPackageBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Wrapped Acceptance continuations (STATIC_VALUE / staticValue) must reach Dev and Verify
 * {@code slices/acceptance.md}, not only the first physical line of a numbered item.
 */
final class AcceptanceContinuationInDevVerifySlicesTest {

    @TempDir
    Path temp;

    @Test
    void devAndVerifyAcceptanceSlicesKeepWrappedFieldNameConstraints() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve("seed.md");
        Files.write(
                seed,
                ("## Background\nfromJson must not mutate statics\n\n"
                        + "## Goal\nIgnore static fields\n\n"
                        + "## Allowed files\n- src/main/java/A.java\n\n"
                        + "## Out of scope\n- pom.xml\n\n"
                        + "## Acceptance\n"
                        + "1. Given a target class and JSON containing keys that exactly\n"
                        + "   match both field names (including static field names such as `STATIC_VALUE`):\n"
                        + "   - the instance field is populated normally;\n"
                        + "3. Add a regression test demonstrating the behavior. The regression JSON keys\n"
                        + "   MUST be identical to the Java field names under test (no camelCase rewrite\n"
                        + "   of `STATIC_VALUE` → `staticValue`).\n")
                        .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "story-wrap", seed);
        AnalysisPackageBuilder.build(ws, "story-wrap");
        DiscoveryRecords.writeSkip(ws, "story-wrap", "fixture paths known", "tester");
        GapRecords.write(ws, "story-wrap", GapStatus.CLEAR, 0, "ok");
        PlanRecords.writeFormalPlan(
                ws, "story-wrap", "plan", Collections.singletonList("src/main/java/A.java"));

        Path devPkg = DevPackageBuilder.build(ws, "story-wrap");
        String devAcc = new String(
                Files.readAllBytes(devPkg.resolve("slices/acceptance.md")),
                StandardCharsets.UTF_8);
        assertContainsFieldConstraints(devAcc);

        Path changed = ws.resolve(".story/story-wrap/development");
        Files.createDirectories(changed);
        Files.write(
                changed.resolve(DevelopmentRecords.CHANGED_FILES),
                ("- src/main/java/A.java\n").getBytes(StandardCharsets.UTF_8));

        Path verifyPkg = VerifyPackageBuilder.build(ws, "story-wrap", 1, "true", null);
        String verifyAcc = new String(
                Files.readAllBytes(verifyPkg.resolve("slices/acceptance.md")),
                StandardCharsets.UTF_8);
        assertContainsFieldConstraints(verifyAcc);
    }

    private static void assertContainsFieldConstraints(String acc) {
        assertTrue(acc.contains("exactly match both field names"), acc);
        assertTrue(acc.contains("STATIC_VALUE"), acc);
        assertTrue(acc.contains("staticValue"), acc);
    }
}
