package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Review Adapter on spine → structured decision, not hardcoded 通过. */
final class PathwayReviewAdapterOnSpineTest {

    @TempDir
    Path temp;

    @Test
    void reviewAdapterDecisionIsRecordedNotFixturePass() throws Exception {
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
                        "附条件",
                        "AC1 evidence thin — follow up",
                        ReviewRecords.SOURCE_ADAPTER);
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "review written", "", Collections.<String, String>emptyMap());
        });

        PathwayRunner.PathwayResult result = PathwayRunner.run(
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
                        .build(),
                real);

        Path reviewFile = ReviewRecords.reviewDir(ws, "story-review").resolve(ReviewRecords.FILE);
        assertTrue(Files.isRegularFile(reviewFile));
        String body = new String(Files.readAllBytes(reviewFile), StandardCharsets.UTF_8);
        assertTrue(body.contains("decision: 附条件"), body);
        assertTrue(body.contains("residual_risk: AC1 evidence thin"), body);
        assertTrue(body.contains("review_source: adapter"), body);
        assertFalse(body.contains("decision: 通过") && !body.contains("附条件"));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-review/execution/adapter-review.md")));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-review/packages/review/slices/acceptance.md")));
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("adapter_roles: Review") || meta.contains("Review"), meta);
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
