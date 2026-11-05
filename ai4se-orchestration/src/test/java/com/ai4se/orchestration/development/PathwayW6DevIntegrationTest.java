package com.ai4se.orchestration.development;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W1→W6：挂机到 Dev 后记录限面 Diff，再进 Verification. */
final class PathwayW6DevIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void hangInThroughDevToVerification() throws Exception {
        Path ws = temp.resolve("w6");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve("seed.md");
        Files.write(seed, (""
                + "## raw\nflags\n\n## goal\nfeature flag\n\n## in_scope\n- api\n\n"
                + "## out_of_scope\n- ui\n\n## acceptance\n- isEnabled false when off\n")
                .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "story-w6", seed);
        AnalysisPackageBuilder.build(ws, "story-w6");

        StoryWorkflowMachine.start(ws, "story-w6");
        DiscoveryRecords.writeSkip(ws, "story-w6", "known", "owner");
        GapRecords.write(ws, "story-w6", GapStatus.CLEAR, 0, "ok");
        StoryWorkflowMachine.advance(ws, "story-w6");
        PlanRecords.writeFormalPlan(
                ws,
                "story-w6",
                "FeatureFlags",
                Arrays.asList("src/main/java/FeatureFlags.java"));
        ApprovalRecords.approvePlan(ws, "story-w6", "rev", "ok");
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.advance(ws, "story-w6").stage());

        DevelopmentRecords.recordObservedChanges(
                ws,
                "story-w6",
                "Add isEnabled with safe default false",
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.ok(" M src/main/java/FeatureFlags.java")));
        DevPackageBuilder.build(ws, "story-w6");
        assertEquals(
                WorkflowStage.VERIFICATION,
                StoryWorkflowMachine.advance(ws, "story-w6").stage());
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-w6/development/changed-files.md")));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-w6/packages/development/round-1/manifest.md")));
    }
}
