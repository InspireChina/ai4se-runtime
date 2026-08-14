package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression coverage for preflight gates that must not create a run ledger. */
final class ProductionPathwayPreflightOrderingTest {

    @TempDir
    Path temp;

    @Test
    void missingOnboardSlotsDoNotCreateLedgerOrDirtyWorktree() throws Exception {
        Path workspace = temp.resolve("workspace");
        Files.createDirectories(workspace.resolve("src/main/java"));
        Files.write(
                workspace.resolve("src/main/java/A.java"),
                "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        run(invoker, workspace, "git", "init", "--template=");
        run(invoker, workspace, "git", "add", "-A");
        run(invoker, workspace, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "-m", "init");

        Path requirement = temp.resolve("requirement.md");
        Files.write(
                requirement,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## acceptance\n- criterion\n").getBytes(StandardCharsets.UTF_8));
        ProductionRunRequest request = ProductionRunRequest.builder(workspace, "story-missing-slots")
                .seedRequirement(requirement)
                .writeScope("src/main/java/A.java")
                .build();

        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> ProductionPathway.run(request, invoker, new CursorCliAdapter()));
        assertTrue(ex.getMessage().toLowerCase().contains("entries"), ex.getMessage());
        assertFalse(Files.exists(workspace.resolve(".story/story-missing-slots/run")));
        assertTrue(workingTreeIsClean(invoker, workspace));
    }

    private static boolean workingTreeIsClean(ProcessInvoker invoker, Path workspace) throws Exception {
        ProcessInvoker.ProcessOutcome status = invoker.run(
                Arrays.asList("git", "status", "--porcelain"), workspace, null, Duration.ofSeconds(30));
        return status.exitCode == 0 && status.stdout.trim().isEmpty();
    }

    private static void run(ProcessInvoker invoker, Path workspace, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome outcome = invoker.run(
                Arrays.asList(argv), workspace, null, Duration.ofSeconds(30));
        if (outcome.exitCode != 0) {
            throw new IllegalStateException(outcome.stderr + outcome.stdout);
        }
    }
}
