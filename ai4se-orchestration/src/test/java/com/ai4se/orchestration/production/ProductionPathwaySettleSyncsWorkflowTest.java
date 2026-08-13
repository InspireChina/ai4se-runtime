package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Production settle must stop a RUNNING Story workflow so ledger/workflow stay consistent.
 */
final class ProductionPathwaySettleSyncsWorkflowTest {

    @TempDir
    Path temp;

    @Test
    void settleStoppedMarksWorkflowStoppedWhenRunningAtReview() throws Exception {
        Path ws = prepareWorkspace();
        Path seed = temp.resolve("seed.md");
        Files.write(
                seed,
                ("## raw\nr\n## goal\ng\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## Acceptance\n- ok\n")
                        .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "story-sync", seed);
        StoryWorkflowMachine.start(ws, "story-sync");
        // Force stage to REVIEW/RUNNING (simulates mid-Review settle).
        Path wf = ws.resolve(".story/story-sync/workflow-state.properties");
        Files.write(
                wf,
                ("story_id=story-sync\nstage=REVIEW\nstatus=RUNNING\nstop_reason=\n")
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals(WorkflowStage.REVIEW, StoryWorkflowMachine.load(ws, "story-sync").stage());
        assertEquals(WorkflowStatus.RUNNING, StoryWorkflowMachine.load(ws, "story-sync").status());

        RunLedger ledger = RunLedger.open(ws, "story-sync");
        ledger.beginRun("src/main/java", 1);

        ProductionRunRequest request = ProductionRunRequest.builder(ws, "story-sync")
                .writeScope("src/main/java")
                .maxDevelopmentRounds(1)
                .build();
        ProductionRunResult result = ProductionPathway.settleStoppedForTest(
                request,
                ledger,
                ProductionTerminal.FAILED_POLICY,
                "Review result missing decision");

        assertEquals(ProductionTerminal.FAILED_POLICY, result.terminal);
        assertEquals(WorkflowStatus.STOPPED, StoryWorkflowMachine.load(ws, "story-sync").status());
        assertEquals(WorkflowStage.REVIEW, StoryWorkflowMachine.load(ws, "story-sync").stage());
        assertTrue(
                StoryWorkflowMachine.load(ws, "story-sync").stopReason().contains("FAILED_POLICY"),
                StoryWorkflowMachine.load(ws, "story-sync").stopReason());
        assertEquals(
                ProductionTerminal.FAILED_POLICY.name(),
                ledger.readState().terminalOrNull);
    }

    private Path prepareWorkspace() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.write(ws.resolve("src/main/java/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        run(invoker, ws, "git", "init", "--template=");
        run(invoker, ws, "git", "add", "-A");
        run(invoker, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        return ws;
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out =
                invoker.run(Arrays.asList(argv), ws, null, Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
