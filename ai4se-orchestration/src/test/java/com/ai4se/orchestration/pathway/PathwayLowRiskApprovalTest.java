package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.LowRiskPlanApproval;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner.ApprovalMode;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PathwayLowRiskApprovalTest {

    @TempDir
    Path temp;

    @Test
    void lowRiskAutoApprovesWhenPlanWithinHint() throws Exception {
        Path ws = fixture(temp, "ok");
        Path seed = seed(temp);
        FunctionalModelCliAdapter dev = new FunctionalModelCliAdapter("dev-hook", request -> {
            try {
                Files.write(
                        request.workspace().resolve("src/test/java/T.java"),
                        "class T { int x; }\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
        });
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-lr")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/test/java/T.java")
                        .verifyCommand("true")
                        .discoverySkip("fixture", "test")
                        .planSummary("仅测")
                        .approvalMode(ApprovalMode.LOW_RISK_AUTO)
                        .devAdapter(dev)
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .allowReviewFixture(true)
                        .build(),
                real);

        assertTrue(ApprovalRecords.isApproved(ws, "story-lr"));
        String approval = new String(Files.readAllBytes(
                PlanRecords.planningDir(ws, "story-lr").resolve(ApprovalRecords.FILE)),
                StandardCharsets.UTF_8);
        assertTrue(approval.contains("mode: " + LowRiskPlanApproval.MODE), approval);
        assertTrue(approval.contains("approver: " + LowRiskPlanApproval.APPROVER), approval);
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("meta.yaml")));
    }

    @Test
    void lowRiskStopsWhenPlanExceedsHint() throws Exception {
        Path ws = fixture(temp, "wide");
        Path seed = seed(temp);
        FunctionalModelCliAdapter plan = new FunctionalModelCliAdapter("plan-hook", request -> {
            try {
                PlanRecords.writeFormalPlan(
                        request.workspace(),
                        "story-wide",
                        "expanded",
                        java.util.Arrays.asList(
                                "src/test/java/T.java", "src/main/java/Secret.java"));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "plan", "", Collections.<String, String>emptyMap());
        });
        FunctionalModelCliAdapter dev = new FunctionalModelCliAdapter("dev-hook", request ->
                AdapterResult.ok(0, "should-not-run", "", Collections.<String, String>emptyMap()));
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();

        StageGateException ex = assertThrows(StageGateException.class, () -> PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-wide")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/test/java/T.java")
                        .verifyCommand("true")
                        .discoverySkip("fixture", "test")
                        .planAdapter(plan)
                        .approvalMode(ApprovalMode.LOW_RISK_AUTO)
                        .devAdapter(dev)
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .allowReviewFixture(true)
                        .build(),
                real));
        assertTrue(ex.getMessage().contains("low-risk auto ineligible"), ex.getMessage());
        assertTrue(ex.getMessage().contains("超出 hint"), ex.getMessage());
    }

    private static Path fixture(Path temp, String name) throws Exception {
        Path ws = temp.resolve(name);
        Files.createDirectories(ws.resolve("src/test/java"));
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.write(ws.resolve("pom.xml"), ("<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>t</groupId><artifactId>t</artifactId><version>1</version></project>\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/test/java/T.java"), "class T {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/main/java/Secret.java"), "class Secret {}\n"
                .getBytes(StandardCharsets.UTF_8));
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "add", "-A");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));
        return ws;
    }

    private static Path seed(Path temp) throws Exception {
        Path seed = temp.resolve("seed-" + System.nanoTime() + ".md");
        Files.write(seed, ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- ok\n").getBytes(StandardCharsets.UTF_8));
        return seed;
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
