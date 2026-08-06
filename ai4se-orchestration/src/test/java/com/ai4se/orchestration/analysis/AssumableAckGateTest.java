package com.ai4se.orchestration.analysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.pathway.PathwayRunner.AssumablePolicy;
import com.ai4se.orchestration.pathway.PathwayRunner.LifecycleMode;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AssumableAckGateTest {

    @TempDir
    Path temp;

    @Test
    void requireAckBlocksAssumableWithoutAck() throws Exception {
        Path ws = prepareWorkspace();
        Path seed = temp.resolve("seed-ack.md");
        Files.write(seed, ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- ok\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "story-ack", seed);
        GapRecords.write(ws, "story-ack", GapStatus.ASSUMABLE, 0, "assumed default locale");

        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        StageGateException ex = assertThrows(StageGateException.class, () -> PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-ack")
                        .script(Script.V3)
                        .allowedFile("src/main/java/A.java")
                        .verifyCommand("true")
                        .assumablePolicy(AssumablePolicy.REQUIRE_ACK)
                        .lifecycleMode(LifecycleMode.SKIP)
                        .build(),
                real));
        assertTrue(ex.getMessage().contains("ASSUMABLE"), ex.getMessage());
    }

    @Test
    void requireAckProceedsWhenAckProvidedViaConfig() throws Exception {
        Path ws = prepareWorkspace();
        Path seed = temp.resolve("seed-ack-ok.md");
        Files.write(seed, ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- ok\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "story-ack-ok", seed);
        GapRecords.write(ws, "story-ack-ok", GapStatus.ASSUMABLE, 0, "assumed default locale");

        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-ack-ok")
                        .script(Script.V3)
                        .allowedFile("src/main/java/A.java")
                        .verifyCommand("true")
                        .assumablePolicy(AssumablePolicy.REQUIRE_ACK)
                        .assumableAck("peng.lv", "read assumptions; accept default locale")
                        .lifecycleMode(LifecycleMode.SKIP)
                        .build(),
                real);
        assertTrue(AssumableAckRecords.hasAck(ws, "story-ack-ok"));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("meta.yaml")));
    }

    private Path prepareWorkspace() throws Exception {
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
        return ws;
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
