package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.execution.support.SplitProcessInvoker;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.LowRiskPlanApproval;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.pathway.PathwayRunner.AssumablePolicy;
import com.ai4se.orchestration.pathway.PathwayRunner.DeliveryMode;
import com.ai4se.orchestration.pathway.PathwayRunner.LifecycleMode;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Production facade integration: non-Maven entries + old Plan/Approval vs narrower writeScope.
 *
 * <p>Full {@link ProductionPathway#run} needs a real Cursor binary; these tests exercise the
 * production recipe ({@link ProductionPathway#buildStrictConfig} flags + same PathwayRunner
 * gates) with Functional adapters where a live Cursor is not required.
 */
final class ProductionPathwayFacadeIntegrationTest {

    private static final String NPM_TEST = "npm test";

    @TempDir
    Path temp;

    @Test
    void nonMavenEntries_productionConfigDoesNotBindDefaultMavenAndRunCompletes() throws Exception {
        Path ws = prepareNpmWorkspace("npm-ok");
        Path seed = seedFile("npm-ok");
        freezeAcceptanceProbe(ws, "story-npm");
        commitAll(ws);

        ProductionRunRequest request = ProductionRunRequest.builder(ws, "story-npm")
                .seedRequirement(seed)
                .writeScope("src/main/js")
                .build();
        PathwayRunner.Config productionCfg =
                ProductionPathway.buildStrictConfig(request, new CursorCliAdapter());
        assertEquals("", productionCfg.verifyCommand, "must not invent mvn -q test");
        assertEquals(PathwayRunner.ApprovalMode.LOW_RISK_AUTO, productionCfg.approvalMode);

        // Facade clean gate accepts committed non-Maven entries.
        ProductionPathway.validateWorkspaceGates(
                ws, request, new ProcessInvoker.RealProcessInvoker());

        // Approval whitelist: blank/entries-only OK; hardcoded Maven must not be required.
        PlanRecords.writeFormalPlan(
                prepareStoryArtifacts(ws, "story-npm-check"),
                "story-npm-check",
                "npm plan",
                Collections.singletonList("src/main/js/app.js"));
        assertNull(LowRiskPlanApproval.ineligibleReason(
                ws, "story-npm-check", Collections.singletonList("src/main/js"), null, false));
        String mavenBound = LowRiskPlanApproval.ineligibleReason(
                ws,
                "story-npm-check",
                Collections.singletonList("src/main/js"),
                "mvn -q test",
                false);
        assertTrue(mavenBound != null && mavenBound.contains("entries"), mavenBound);

        AtomicInteger devCalls = new AtomicInteger();
        FunctionalModelCliAdapter analysis = analysisHook("story-npm");
        FunctionalModelCliAdapter plan = planHook(
                "story-npm", Collections.singletonList("src/main/js/app.js"));
        FunctionalModelCliAdapter dev = new FunctionalModelCliAdapter("dev-npm", request2 -> {
            try {
                devCalls.incrementAndGet();
                Files.write(
                        request2.workspace().resolve("src/main/js/app.js"),
                        "module.exports = 1;\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "dev", "", Collections.<String, String>emptyMap());
        });
        FunctionalModelCliAdapter review = reviewHook("story-npm");

        ProcessInvoker invoker = new SplitProcessInvoker(
                new ProcessInvoker.RealProcessInvoker(),
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.ok("npm test ok"),
                        SequenceProcessInvoker.ok("acceptance probe ok")));

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                productionRecipe(ws, "story-npm")
                        .seedPath(seed)
                        .allowedFile("src/main/js")
                        .analysisAdapter(analysis)
                        .planAdapter(plan)
                        .devAdapter(dev)
                        .reviewAdapter(review)
                        .build(),
                invoker);

        assertEquals(1, devCalls.get());
        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertTrue(ApprovalRecords.isApproved(ws, "story-npm"));
        String approval = new String(
                Files.readAllBytes(
                        PlanRecords.planningDir(ws, "story-npm").resolve(ApprovalRecords.FILE)),
                StandardCharsets.UTF_8);
        assertTrue(approval.contains("mode: " + LowRiskPlanApproval.MODE), approval);
        assertFalse(approval.toLowerCase().contains("mvn -q test"), approval);
    }

    @Test
    void existingPlanApproval_narrowerWriteScopeRefusedBeforeDevelopment() throws Exception {
        Path ws = prepareNpmWorkspace("narrow");
        Path seed = seedFile("narrow");
        // Open story and plant wide Plan + Approval before this run's narrower writeScope.
        StoryOpener.open(ws, "story-narrow", seed);
        DiscoveryRecords.writeReport(ws, "story-narrow", "## 摸底\n- wide\n");
        GapRecords.write(ws, "story-narrow", GapStatus.CLEAR, 0, "clear");
        PlanRecords.writeFormalPlan(
                ws,
                "story-narrow",
                "old wide plan",
                Collections.singletonList("src/main/js/secret.js"));
        ApprovalRecords.approvePlan(ws, "story-narrow", "human", "pre-existing approval");
        commitAll(ws);

        ProductionRunRequest request = ProductionRunRequest.builder(ws, "story-narrow")
                .writeScope("src/test/js")
                .build();
        // Facade accepts clean tree; refusal is at Plan Allowed ⊆ writeScope.
        ProductionPathway.validateWorkspaceGates(
                ws, request, new ProcessInvoker.RealProcessInvoker());
        PathwayRunner.Config cfg =
                ProductionPathway.buildStrictConfig(request, new CursorCliAdapter());
        assertTrue(cfg.allowedFiles.contains("src/test/js"));

        AtomicInteger devCalls = new AtomicInteger();
        FunctionalModelCliAdapter analysis = analysisHook("story-narrow");
        FunctionalModelCliAdapter plan = new FunctionalModelCliAdapter("plan-should-skip", request2 ->
                AdapterResult.ok(0, "should not rewrite plan", "", Collections.<String, String>emptyMap()));
        FunctionalModelCliAdapter dev = new FunctionalModelCliAdapter("dev-must-not-run", request2 -> {
            devCalls.incrementAndGet();
            return AdapterResult.ok(0, "nope", "", Collections.<String, String>emptyMap());
        });
        FunctionalModelCliAdapter review = reviewHook("story-narrow");

        StageGateException ex = assertThrows(StageGateException.class, () -> PathwayRunner.run(
                productionRecipe(ws, "story-narrow")
                        .allowedFile("src/test/js")
                        .analysisAdapter(analysis)
                        .planAdapter(plan)
                        .devAdapter(dev)
                        .reviewAdapter(review)
                        .build(),
                new ProcessInvoker.RealProcessInvoker()));

        assertEquals(0, devCalls.get(), "Development must not run when writeScope is narrower");
        assertTrue(
                ex.getMessage().contains("subset")
                        || ex.getMessage().contains("outside")
                        || ex.getMessage().contains("writeScope"),
                ex.getMessage());
        assertTrue(ApprovalRecords.isApproved(ws, "story-narrow"), "old approval still present");
        assertTrue(PlanRecords.readAllowedFiles(ws, "story-narrow")
                .contains("src/main/js/secret.js"));
    }

    /** Same policy flags as {@link ProductionPathway#buildStrictConfig}, adapters injectable for tests. */
    private static PathwayRunner.Config.Builder productionRecipe(Path ws, String storyId) {
        return PathwayRunner.Config.builder(ws, storyId)
                .script(Script.V3)
                .suite("B")
                .assumablePolicy(AssumablePolicy.REQUIRE_ACK)
                .deliveryMode(DeliveryMode.LOCAL_COMMIT)
                .lifecycleMode(LifecycleMode.SKIP)
                .approvalMode(PathwayRunner.ApprovalMode.LOW_RISK_AUTO)
                .planHumanOwned(true)
                .planApprover("operator-write-scope")
                .approvalNote("production: Plan Allowed ⊆ operator writeScope")
                .verifyFromEntriesOnly()
                .requireAcceptanceProofs(true)
                .commitMessage("ai4se(production): " + storyId);
    }

    private static void freezeAcceptanceProbe(Path workspace, String storyId) throws Exception {
        Path dir = workspace.resolve(".ai4se/acceptance-probes").resolve(storyId);
        Files.createDirectories(dir);
        Path probe = dir.resolve("ac-1.sh");
        Files.write(probe, "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
        String rel = ".ai4se/acceptance-probes/" + storyId + "/ac-1.sh";
        Files.write(dir.resolve("probes.properties"), (
                "ac.count=1\n"
                        + "ac.1.path=" + rel + "\n"
                        + "ac.1.sha256=" + sha256(probe) + "\n"
                        + "ac.1.command=sh " + rel + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", Byte.valueOf(b)));
        }
        return hex.toString();
    }

    private static FunctionalModelCliAdapter analysisHook(String storyId) {
        return new FunctionalModelCliAdapter("analysis-" + storyId, request -> {
            try {
                DiscoveryRecords.writeReport(
                        request.workspace(), storyId, "## 摸底\n- npm project\n");
                GapRecords.write(request.workspace(), storyId, GapStatus.CLEAR, 0, "clear");
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "analysis", "", Collections.<String, String>emptyMap());
        });
    }

    private static FunctionalModelCliAdapter planHook(String storyId, java.util.List<String> allowed) {
        return new FunctionalModelCliAdapter("plan-" + storyId, request -> {
            try {
                PlanRecords.writeFormalPlan(request.workspace(), storyId, "plan", allowed);
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "plan", "", Collections.<String, String>emptyMap());
        });
    }

    private static FunctionalModelCliAdapter reviewHook(String storyId) {
        return new FunctionalModelCliAdapter("review-" + storyId, request -> {
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

    private Path prepareNpmWorkspace(String name) throws Exception {
        Path ws = temp.resolve(name);
        Files.createDirectories(ws.resolve("src/main/js"));
        Files.createDirectories(ws.resolve("src/test/js"));
        Files.write(
                ws.resolve("package.json"),
                ("{\"name\":\"t\",\"scripts\":{\"test\":\"echo ok\"}}\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                ws.resolve("src/main/js/app.js"),
                "module.exports = 0;\n".getBytes(StandardCharsets.UTF_8));
        Files.write(
                ws.resolve("src/main/js/secret.js"),
                "module.exports = 'secret';\n".getBytes(StandardCharsets.UTF_8));
        Files.write(
                ws.resolve("src/test/js/app.test.js"),
                "require('../main/js/app.js');\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "add", "-A");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - " + NPM_TEST + "\n")
                        .getBytes(StandardCharsets.UTF_8));
        return ws;
    }

    /** Helper story artifacts for LowRiskPlanApproval checks (not the main run story). */
    private static Path prepareStoryArtifacts(Path ws, String storyId) throws Exception {
        Files.createDirectories(ws.resolve(".story").resolve(storyId).resolve("analysis"));
        DiscoveryRecords.writeReport(ws, storyId, "## 摸底\n- check\n");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "ok");
        return ws;
    }

    private Path seedFile(String tag) throws Exception {
        Path seed = temp.resolve("seed-" + tag + ".md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## acceptance\n- npm tests pass\n")
                        .getBytes(StandardCharsets.UTF_8));
        return seed;
    }

    private static void commitAll(Path ws) throws Exception {
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "add", "-A");
        ProcessInvoker.ProcessOutcome st = real.run(
                java.util.Arrays.asList("git", "status", "--porcelain"),
                ws,
                null,
                java.time.Duration.ofSeconds(30));
        if (st.stdout != null && !st.stdout.trim().isEmpty()) {
            run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "sync");
        }
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
