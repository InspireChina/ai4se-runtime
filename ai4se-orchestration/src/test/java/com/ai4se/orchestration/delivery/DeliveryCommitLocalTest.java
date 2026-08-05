package com.ai4se.orchestration.delivery;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.execution.support.SplitProcessInvoker;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DeliveryCommitLocalTest {

    @TempDir
    Path temp;

    @Test
    void commitLocalCreatesRealGitCommit() throws Exception {
        Path ws = temp.resolve("repo");
        Files.createDirectories(ws);
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "--allow-empty", "-m", "init");

        String id = "c1";
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.createDirectories(ws.resolve(".story").resolve(id).resolve("packages"));
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/baseline.md"), "# f\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        Files.write(
                ws.resolve(".story").resolve(id).resolve("requirement.md"),
                ("## acceptance\n- ac\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(ws.resolve("src"));
        Files.write(ws.resolve("src/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));

        StoryWorkflowMachine.start(ws, id);
        DiscoveryRecords.writeSkip(ws, id, "k", "o");
        GapRecords.write(ws, id, GapStatus.CLEAR, 0, "ok");
        StoryWorkflowMachine.advance(ws, id);
        PlanRecords.writeFormalPlan(ws, id, "d", Arrays.asList("src/A.java"));
        ApprovalRecords.approvePlan(ws, id, "r", "ok");
        StoryWorkflowMachine.advance(ws, id);

        ProcessInvoker invoker = new SplitProcessInvoker(
                real,
                new SequenceProcessInvoker(SequenceProcessInvoker.ok("ok")));
        DevelopmentRecords.recordObservedChanges(
                ws, id, "impl", invoker);
        StoryWorkflowMachine.advance(ws, id);
        VerificationControl.run(ws, id, "mvn -q test", invoker);
        StoryWorkflowMachine.advance(ws, id);
        ReviewRecords.write(ws, id, "通过", "");
        StoryWorkflowMachine.advance(ws, id);

        // dirty file for commit
        Files.write(ws.resolve("src/A.java"), "class A { int x; }\n".getBytes(StandardCharsets.UTF_8));
        DevelopmentRecords.recordDeclaredChanges(
                ws, id, Collections.singletonList("src/A.java"), "ready to commit");

        String sha = DeliveryRecords.commitLocalAndRecord(ws, id, "ai4se: c1", invoker);
        assertTrue(sha.matches("^[0-9a-f]{7,40}$"));
        String delivery = new String(Files.readAllBytes(
                ws.resolve(".story/" + id + "/delivery/delivery.md")), StandardCharsets.UTF_8);
        assertTrue(delivery.contains("committed_now: true"));
        assertTrue(delivery.contains(sha));
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out = invoker.run(
                java.util.Arrays.asList(argv), ws, null, java.time.Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr);
        }
    }
}
