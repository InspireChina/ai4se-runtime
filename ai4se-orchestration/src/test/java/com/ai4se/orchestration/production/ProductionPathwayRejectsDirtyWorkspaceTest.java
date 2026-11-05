package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProductionPathwayRejectsDirtyWorkspaceTest {

    @TempDir
    Path temp;

    @Test
    void refusesBusinessDirtyWorktree() throws Exception {
        Path ws = temp.resolve("cust");
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

        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));

        Path seed = temp.resolve("seed.md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## acceptance\n- ok\n")
                        .getBytes(StandardCharsets.UTF_8));

        Files.write(
                ws.resolve("src/main/java/A.java"),
                "class A { int dirty; }\n".getBytes(StandardCharsets.UTF_8));

        ProductionRunRequest request = ProductionRunRequest.builder(ws, "story-dirty")
                .seedRequirement(seed)
                .writeScope("src/main/java")
                .build();

        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> ProductionPathway.validateWorkspaceGates(ws, request, real));
        assertTrue(ex.getMessage().toLowerCase().contains("dirty"), ex.getMessage());
    }

    @Test
    void refusesUncommittedControlPlaneFiles() throws Exception {
        Path ws = temp.resolve("cust2");
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

        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));
        // entries.yaml uncommitted — must refuse (control plane).

        Path seed = temp.resolve("seed2.md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## acceptance\n- ok\n")
                        .getBytes(StandardCharsets.UTF_8));

        ProductionRunRequest request = ProductionRunRequest.builder(ws, "story-ctrl")
                .seedRequirement(seed)
                .writeScope("src/main/java")
                .build();

        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> ProductionPathway.validateWorkspaceGates(ws, request, real));
        assertTrue(ex.getMessage().contains(".ai4se") || ex.getMessage().toLowerCase().contains("dirty"),
                ex.getMessage());
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
