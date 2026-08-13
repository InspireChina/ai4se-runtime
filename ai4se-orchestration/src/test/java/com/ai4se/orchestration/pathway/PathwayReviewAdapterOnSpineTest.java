package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.review.ReviewDecision;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Review Adapter on spine → machine decision; only PASS auto-delivers. */
final class PathwayReviewAdapterOnSpineTest {

    @TempDir
    Path temp;

    @Test
    void conditionalReviewStopsBeforeDeliveryAndKeepsSidecar() throws Exception {
        Path ws = temp.resolve("cust");
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.write(ws.resolve("pom.xml"), ("<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>t</groupId><artifactId>t</artifactId><version>1</version></project>\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/main/java/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "add", "-A");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));

        Path seed = temp.resolve("seed.md");
        Files.write(seed, ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- ok\n").getBytes(StandardCharsets.UTF_8));

        FunctionalModelCliAdapter review = new FunctionalModelCliAdapter("review-hook", request -> {
            try {
                ReviewRecords.write(
                        request.workspace(),
                        "story-review",
                        "附条件通过",
                        "AC1 evidence thin — follow up",
                        ReviewRecords.SOURCE_ADAPTER);
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "review written", "", Collections.<String, String>emptyMap());
        });

        StageGateException ex = assertThrows(StageGateException.class, () -> PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-review")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/main/java/A.java")
                        .verifyCommand("true")
                        .reviewAdapter(review)
                        .planHumanOwned(true)
                        .planApprover("peng.lv")
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .allowReviewFixture(true)
                        .build(),
                real));
        assertTrue(ex.getMessage().contains("CONDITIONAL"), ex.getMessage());

        assertEquals(ReviewDecision.CONDITIONAL, ReviewRecords.readDecision(ws, "story-review"));
        Path props = ReviewRecords.reviewDir(ws, "story-review").resolve(ReviewRecords.PROPERTIES_FILE);
        assertTrue(Files.isRegularFile(props));
        String propBody = new String(Files.readAllBytes(props), StandardCharsets.UTF_8);
        assertTrue(propBody.contains("decision=CONDITIONAL"), propBody);
        assertTrue(propBody.contains("review_source=adapter"), propBody);
        assertFalse(Files.isRegularFile(
                ws.resolve(".story/story-review/delivery/delivery.md")));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-review/execution/adapter-review.md")));
    }

    @Test
    void freeFormMarkdownDecisionNormalizedByControl() throws Exception {
        Path ws = temp.resolve("cust-md");
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.write(ws.resolve("pom.xml"), ("<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>t</groupId><artifactId>t</artifactId><version>1</version></project>\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/main/java/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "add", "-A");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));
        Path seed = temp.resolve("seed-md.md");
        Files.write(seed, ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- ok\n").getBytes(StandardCharsets.UTF_8));

        FunctionalModelCliAdapter review = new FunctionalModelCliAdapter("review-md", request -> {
            try {
                Path dir = ReviewRecords.reviewDir(request.workspace(), "story-md");
                Files.createDirectories(dir);
                Files.write(
                        dir.resolve(ReviewRecords.FILE),
                        ("# Review Result\n\n## decision\n\n**附条件通过**\n\n"
                                + "| Field | Value |\n|---|---|\n| review_source | adapter |\n")
                                .getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "wrote free-form md", "", Collections.<String, String>emptyMap());
        });

        StageGateException ex = assertThrows(StageGateException.class, () -> PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-md")
                        .seedPath(seed)
                        .allowedFile("src/main/java/A.java")
                        .verifyCommand("true")
                        .reviewAdapter(review)
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .allowReviewFixture(true)
                        .build(),
                real));
        assertTrue(ex.getMessage().contains("CONDITIONAL"), ex.getMessage());
        assertEquals(ReviewDecision.CONDITIONAL, ReviewRecords.readDecision(ws, "story-md"));
        String props = new String(Files.readAllBytes(
                ReviewRecords.reviewDir(ws, "story-md").resolve(ReviewRecords.PROPERTIES_FILE)),
                StandardCharsets.UTF_8);
        assertTrue(props.contains("review_source=adapter"), props);
    }

    @Test
    void reviewRejectStopsBeforeDelivery() throws Exception {
        Path ws = temp.resolve("cust-rej");
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.write(ws.resolve("pom.xml"), ("<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>t</groupId><artifactId>t</artifactId><version>1</version></project>\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/main/java/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "add", "-A");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));
        Path seed = temp.resolve("seed-rej.md");
        Files.write(seed, ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- ok\n").getBytes(StandardCharsets.UTF_8));

        FunctionalModelCliAdapter review = new FunctionalModelCliAdapter("review-reject", request ->
                AdapterResult.ok(0, "decision: 驳回\nresidual_risk: missing AC evidence\n", "",
                        Collections.<String, String>emptyMap()));

        assertThrows(Exception.class, () -> PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-rej")
                        .seedPath(seed)
                        .allowedFile("src/main/java/A.java")
                        .verifyCommand("true")
                        .reviewAdapter(review)
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .allowReviewFixture(true)
                        .build(),
                real));
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
