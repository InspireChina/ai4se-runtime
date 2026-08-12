package com.ai4se.orchestration.run;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.execution.support.SplitProcessInvoker;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.pathway.PathwayRunner.DeliveryMode;
import com.ai4se.orchestration.pathway.PathwayRunner.LifecycleMode;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.review.ReviewRecords;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared fixtures for PR3 resume / run-ledger tests. */
final class ResumeFixtures {

    static final String VERIFY = "true";
    static final String ALLOWED = "src/main/java/A.java";

    private ResumeFixtures() {
    }

    static Path prepareWorkspace(Path temp, String name) throws Exception {
        Path ws = temp.resolve(name);
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.write(
                ws.resolve("pom.xml"),
                ("<project><modelVersion>4.0.0</modelVersion>"
                        + "<groupId>t</groupId><artifactId>t</artifactId><version>1</version></project>\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                ws.resolve("src/main/java/A.java"),
                "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "add", "-A");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - " + VERIFY + "\n").getBytes(StandardCharsets.UTF_8));
        return ws;
    }

    static Path seed(Path temp, String name) throws Exception {
        Path seed = temp.resolve("seed-" + name + ".md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## acceptance\n- ok\n").getBytes(StandardCharsets.UTF_8));
        return seed;
    }

    static FunctionalModelCliAdapter analysis(String storyId, AtomicInteger calls) {
        return new FunctionalModelCliAdapter("analysis-" + storyId, request -> {
            calls.incrementAndGet();
            try {
                DiscoveryRecords.writeReport(request.workspace(), storyId, "## 摸底\n- A.java\n");
                GapRecords.write(request.workspace(), storyId, GapStatus.CLEAR, 0, "clear");
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "analysis", "", Collections.<String, String>emptyMap());
        });
    }

    static FunctionalModelCliAdapter plan(String storyId, AtomicInteger calls) {
        return new FunctionalModelCliAdapter("plan-" + storyId, request -> {
            calls.incrementAndGet();
            try {
                PlanRecords.writeFormalPlan(
                        request.workspace(), storyId, "plan", Collections.singletonList(ALLOWED));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "plan", "", Collections.<String, String>emptyMap());
        });
    }

    static FunctionalModelCliAdapter dev(AtomicInteger calls) {
        return new FunctionalModelCliAdapter("dev", request -> {
            calls.incrementAndGet();
            try {
                Files.write(
                        request.workspace().resolve(ALLOWED),
                        ("class A { int x=" + calls.get() + "; }\n").getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "dev", "", Collections.<String, String>emptyMap());
        });
    }

    static FunctionalModelCliAdapter review(String storyId, AtomicInteger calls) {
        return new FunctionalModelCliAdapter("review-" + storyId, request -> {
            calls.incrementAndGet();
            try {
                ReviewRecords.write(
                        request.workspace(),
                        storyId,
                        "通过",
                        null,
                        ReviewRecords.SOURCE_ADAPTER);
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "review", "", Collections.<String, String>emptyMap());
        });
    }

    static ProcessInvoker passingVerifier() {
        return new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new ProcessInvoker() {
                    @Override
                    public ProcessOutcome run(
                            List<String> argv,
                            Path workingDirectory,
                            Map<String, String> extraEnv,
                            Duration timeout) {
                        return SequenceProcessInvoker.ok("PASS");
                    }
                });
    }

    static ProcessInvoker alwaysFailingVerifier() {
        return new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new ProcessInvoker() {
                    @Override
                    public ProcessOutcome run(
                            List<String> argv,
                            Path workingDirectory,
                            Map<String, String> extraEnv,
                            Duration timeout) {
                        return SequenceProcessInvoker.exit(1, "", "FAIL forever");
                    }
                });
    }

    static PathwayRunner.Config.Builder base(
            Path ws,
            String storyId,
            Path seed,
            RunLedger ledger,
            FunctionalModelCliAdapter analysis,
            FunctionalModelCliAdapter plan,
            FunctionalModelCliAdapter dev,
            FunctionalModelCliAdapter review) {
        return PathwayRunner.Config.builder(ws, storyId)
                .script(Script.V3)
                .suite("B")
                .seedPath(seed)
                .allowedFile(ALLOWED)
                .verifyFromEntriesOnly()
                .analysisAdapter(analysis)
                .planAdapter(plan)
                .devAdapter(dev)
                .reviewAdapter(review)
                .planHumanOwned(true)
                .planApprover("tester")
                .approvalMode(PathwayRunner.ApprovalMode.ALWAYS_RECORD)
                .deliveryMode(DeliveryMode.LOCAL_COMMIT)
                .lifecycleMode(LifecycleMode.SKIP)
                .boundedDeliveryLoop(3)
                .commitMessage("ai4se: " + storyId)
                .runLedger(ledger);
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
