package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProductionDeliveryResumeTest {

    @TempDir
    Path temp;

    @Test
    void reopensStoppedDeliveryOnlyWhenAllUpstreamStagesAreDurablyComplete() throws Exception {
        Path workspace = temp.resolve("workspace");
        Files.createDirectories(workspace.resolve("src/main/java"));
        Files.write(workspace.resolve("src/main/java/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        run(invoker, workspace, "git", "init", "--template=");
        run(invoker, workspace, "git", "add", "-A");
        run(invoker, workspace, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(workspace);

        Path requirement = temp.resolve("story.md");
        Files.write(requirement, (
                "## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n## acceptance\n- ok\n")
                .getBytes(StandardCharsets.UTF_8));
        String storyId = "story-delivery-resume";
        StoryOpener.open(workspace, storyId, requirement);
        StoryWorkflowMachine.save(workspace, new StoryWorkflowState(
                storyId, WorkflowStage.DELIVERY, WorkflowStatus.STOPPED, "hook failed"));

        RunLedger ledger = RunLedger.open(workspace, storyId);
        ledger.beginRun("src/main/java", 3);
        for (WorkflowStage stage : Arrays.asList(
                WorkflowStage.ANALYSIS,
                WorkflowStage.PLANNING,
                WorkflowStage.DEVELOPMENT,
                WorkflowStage.VERIFICATION,
                WorkflowStage.REVIEW)) {
            ledger.stageCompleted(stage);
        }
        ledger.markTerminal(ProductionTerminal.FAILED_ENVIRONMENT, "customer hook unavailable");

        PathwayRunner.ensureWorkflowForProductionResume(workspace, storyId, ledger);

        StoryWorkflowState resumed = StoryWorkflowMachine.load(workspace, storyId);
        assertEquals(WorkflowStage.DELIVERY, resumed.stage());
        assertEquals(WorkflowStatus.RUNNING, resumed.status());
    }

    @Test
    void reopensPreDevelopmentPolicyStopWhenPlanAndProbesWereAlreadyFrozen() throws Exception {
        Path workspace = temp.resolve("pre-development-policy-stop");
        Files.createDirectories(workspace.resolve("src/main/java"));
        Files.write(workspace.resolve("src/main/java/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        run(invoker, workspace, "git", "init", "--template=");
        run(invoker, workspace, "git", "add", "-A");
        run(invoker, workspace, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(workspace);

        Path requirement = temp.resolve("pre-development-story.md");
        Files.write(requirement, (
                "## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n## acceptance\n- ok\n")
                .getBytes(StandardCharsets.UTF_8));
        String storyId = "story-pre-development-policy-stop";
        StoryOpener.open(workspace, storyId, requirement);
        DiscoveryRecords.writeReport(workspace, storyId, "# Discovery\n- fixture\n");
        GapRecords.write(workspace, storyId, GapStatus.CLEAR, 0, 0, "clear");
        PlanRecords.writeFormalPlan(workspace, storyId, "frozen plan", Arrays.asList("src/main/java/A.java"));
        ApprovalRecords.approvePlan(workspace, storyId, "operator", "readiness approved");
        writeFrozenProbe(workspace, storyId);
        StoryWorkflowMachine.save(workspace, new StoryWorkflowState(
                storyId, WorkflowStage.DEVELOPMENT, WorkflowStatus.STOPPED, "old policy gate"));

        RunLedger ledger = RunLedger.open(workspace, storyId);
        ledger.beginRun("src/main/java", 3);
        ledger.markTerminal(ProductionTerminal.FAILED_POLICY, "old pre-development policy gate");

        PathwayRunner.ensureWorkflowForProductionResume(workspace, storyId, ledger);

        StoryWorkflowState resumed = StoryWorkflowMachine.load(workspace, storyId);
        assertEquals(WorkflowStage.DEVELOPMENT, resumed.stage());
        assertEquals(WorkflowStatus.RUNNING, resumed.status());
    }

    private static void writeFrozenProbe(Path workspace, String storyId) throws Exception {
        Path root = workspace.resolve(".ai4se/acceptance-probes").resolve(storyId);
        Files.createDirectories(root);
        Path probe = root.resolve("ac1.sh");
        Files.write(probe, "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
        String relative = ".ai4se/acceptance-probes/" + storyId + "/ac1.sh";
        Files.write(root.resolve("probes.properties"), (
                "ac.count=1\n"
                        + "ac.1.path=" + relative + "\n"
                        + "ac.1.command=sh " + relative + "\n"
                        + "ac.1.sha256=" + sha256(probe) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) {
            out.append(String.format("%02x", b & 0xff));
        }
        return out.toString();
    }

    private static void run(ProcessInvoker invoker, Path workspace, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome result = invoker.run(
                Arrays.asList(argv), workspace, null, Duration.ofSeconds(30));
        if (result.exitCode != 0) {
            throw new AssertionError(result.stderr + result.stdout);
        }
    }
}
