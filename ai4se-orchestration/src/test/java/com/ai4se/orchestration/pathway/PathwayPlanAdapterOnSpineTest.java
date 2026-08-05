package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PathwayPlanAdapterOnSpineTest {

    @TempDir
    Path temp;

    @Test
    void planAdapterWritesPlanAndIsDisclosed() throws Exception {
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

        FunctionalModelCliAdapter analysis = new FunctionalModelCliAdapter("analysis-hook", request -> {
            try {
                DiscoveryRecords.writeReport(request.workspace(), "story-plan-ad", "## 摸底\n- A.java\n");
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
        });

        FunctionalModelCliAdapter plan = new FunctionalModelCliAdapter("plan-hook", request -> {
            try {
                PlanRecords.writeFormalPlan(
                        request.workspace(),
                        "story-plan-ad",
                        "仅改 A.java",
                        Collections.singletonList("src/main/java/A.java"));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "plan ok", "", Collections.<String, String>emptyMap());
        });

        FunctionalModelCliAdapter dev = new FunctionalModelCliAdapter("dev-hook", request -> {
            try {
                Files.write(request.workspace().resolve("src/main/java/A.java"),
                        "class A { int x; }\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return AdapterResult.failure(-1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
            return AdapterResult.ok(0, "dev ok", "", Collections.<String, String>emptyMap());
        });

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-plan-ad")
                        .script(Script.V3)
                        .suite("A")
                        .seedPath(seed)
                        .allowedFile("src/main/java/A.java")
                        .verifyCommand("true")
                        .analysisAdapter(analysis)
                        .planAdapter(plan)
                        .devAdapter(dev)
                        .planApprover("peng.lv")
                        .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                        .build(),
                real);

        assertTrue(PlanRecords.hasFormalPlan(ws, "story-plan-ad"));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-plan-ad/execution/adapter-planning.md")));
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("discovery_prepared_by_runner: false"), meta);
        assertTrue(meta.contains("approval_prepared_by_runner: false"), meta);
        assertTrue(meta.contains("adapter_roles: Analysis,Planning,Development"), meta);
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
