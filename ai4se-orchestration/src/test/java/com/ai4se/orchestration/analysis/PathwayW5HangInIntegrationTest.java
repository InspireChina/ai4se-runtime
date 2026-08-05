package com.ai4se.orchestration.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W1→W5 组合：Discovery|skip → Gap CLEAR → Plan+Allowed → Approval → Development. */
final class PathwayW5HangInIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void fullHangInToDevelopment() throws Exception {
        Path ws = temp.resolve("w5");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve("seed.md");
        Files.write(seed, (""
                + "## raw\nflags\n\n## goal\nfeature flag\n\n## in_scope\n- api\n\n"
                + "## out_of_scope\n- ui\n\n## acceptance\n- isEnabled returns false when off\n")
                .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "story-w5", seed);
        AnalysisPackageBuilder.build(ws, "story-w5");

        StoryWorkflowMachine.start(ws, "story-w5");
        DiscoveryRecords.writeSkip(ws, "story-w5", "sample paths known", "owner");
        GapRecords.write(ws, "story-w5", GapStatus.CLEAR, 0, "ready");
        assertEquals(WorkflowStage.PLANNING, StoryWorkflowMachine.advance(ws, "story-w5").stage());

        PlanRecords.writeFormalPlan(
                ws,
                "story-w5",
                "Add FeatureFlags API",
                Arrays.asList("src/main/java/FeatureFlags.java", "src/test/java/FeatureFlagsTest.java"));
        ApprovalRecords.approvePlan(ws, "story-w5", "reviewer", "approved for W5");
        assertEquals(WorkflowStage.DEVELOPMENT, StoryWorkflowMachine.advance(ws, "story-w5").stage());

        assertTrue(Files.isRegularFile(ws.resolve(".story/story-w5/analysis/discovery.skip.md")));
        assertTrue(Files.isRegularFile(ws.resolve(".story/story-w5/planning/plan.md")));
        assertTrue(Files.isRegularFile(ws.resolve(".story/story-w5/planning/approval.md")));
        assertEquals(2, PlanRecords.readAllowedFiles(ws, "story-w5").size());
    }
}
