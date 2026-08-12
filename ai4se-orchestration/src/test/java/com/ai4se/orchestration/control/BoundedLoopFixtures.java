package com.ai4se.orchestration.control;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.execution.support.SplitProcessInvoker;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.verification.DefectPackageWriter;
import com.ai4se.orchestration.verification.VerifyPackageBuilder;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared fixture for BoundedDeliveryLoop tests — leaves story at DEVELOPMENT RUNNING. */
final class BoundedLoopFixtures {

    static final String VERIFY = "true";

    private BoundedLoopFixtures() {
    }

    static Path prepareAtDevelopment(Path temp, String name, String storyId) throws Exception {
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

        Path seed = temp.resolve("seed-" + name + ".md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## acceptance\n- ok\n")
                        .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, storyId, seed);
        DiscoveryRecords.writeReport(ws, storyId, "## 摸底\n- A.java\n");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "clear");
        PlanRecords.writeFormalPlan(
                ws, storyId, "plan", Collections.singletonList("src/main/java/A.java"));
        ApprovalRecords.approvePlan(ws, storyId, "test", "fixture approval");
        StoryWorkflowMachine.save(
                ws,
                new StoryWorkflowState(
                        storyId, WorkflowStage.DEVELOPMENT, WorkflowStatus.RUNNING, null));
        return ws;
    }

    static FunctionalModelCliAdapter fixingDev(String storyId, AtomicInteger calls) {
        return new FunctionalModelCliAdapter("fix-dev", request -> {
            calls.incrementAndGet();
            try {
                Files.write(
                        request.workspace().resolve("src/main/java/A.java"),
                        ("class A { int x=" + calls.get() + "; }\n").getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "fixed", "", Collections.<String, String>emptyMap());
        });
    }

    /** Always writes the same content — no meaningful diff across rounds. */
    static FunctionalModelCliAdapter stuckDev(AtomicInteger calls) {
        return new FunctionalModelCliAdapter("stuck-dev", request -> {
            calls.incrementAndGet();
            try {
                Files.write(
                        request.workspace().resolve("src/main/java/A.java"),
                        "class A { int stuck=1; }\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "stuck", "", Collections.<String, String>emptyMap());
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

    /** Fail first verify, then pass thereafter. */
    static ProcessInvoker failThenPassVerifier() {
        final AtomicInteger verifies = new AtomicInteger();
        return new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new ProcessInvoker() {
                    @Override
                    public ProcessOutcome run(
                            List<String> argv,
                            Path workingDirectory,
                            Map<String, String> extraEnv,
                            Duration timeout) {
                        if (verifies.incrementAndGet() == 1) {
                            return SequenceProcessInvoker.exit(1, "", "first FAIL");
                        }
                        return SequenceProcessInvoker.ok("PASS");
                    }
                });
    }

    static ProcessInvoker envFailVerifier() {
        return new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new ProcessInvoker() {
                    @Override
                    public ProcessOutcome run(
                            List<String> argv,
                            Path workingDirectory,
                            Map<String, String> extraEnv,
                            Duration timeout) {
                        return SequenceProcessInvoker.exit(
                                127, "", "bash: true: command not found");
                    }
                });
    }

    static BoundedLoopResult runLoop(
            Path ws,
            String storyId,
            int maxRounds,
            FunctionalModelCliAdapter dev,
            ProcessInvoker invoker) throws Exception {
        return BoundedDeliveryLoop.run(
                ws,
                storyId,
                maxRounds,
                dev,
                Duration.ofMinutes(2),
                null,
                Collections.singletonList(VERIFY),
                "implement within Allowed",
                invoker);
    }

    static boolean anyDevPackageHasDefectRef(Path ws, String storyId) throws Exception {
        Path root = ws.resolve(".story").resolve(storyId)
                .resolve("packages").resolve("development");
        if (!Files.isDirectory(root)) {
            return false;
        }
        for (Path p : Files.newDirectoryStream(root, "round-*")) {
            if (Files.isRegularFile(p.resolve("slices/defect-ref.md"))) {
                return true;
            }
        }
        return false;
    }

    static int countDevPackages(Path ws, String storyId) throws Exception {
        Path root = DevPackageBuilder.packageDir(ws, storyId, 1).getParent();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int n = 0;
        for (Path p : Files.newDirectoryStream(root, "round-*")) {
            if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("manifest.md"))) {
                n++;
            }
        }
        return n;
    }

    static int countVerifyPackages(Path ws, String storyId) throws Exception {
        return VerifyPackageBuilder.listRoundManifests(ws, storyId).size();
    }

    static boolean hasDefects(Path ws, String storyId) {
        return DefectPackageWriter.hasAny(ws, storyId);
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
