package com.ai4se.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.context.workspace.WorkspaceSlotVerifier;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W1→W2→W3/W5：建槽 → 开 Story → 装包 → Discovery/Gap → Analysis→Planning / Stop. */
final class PathwayW1W2W3IntegrationTest {

    @TempDir
    Path temp;

    @Test
    void packageThenAdvanceToPlanningPersistsState() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        WorkspaceSlotVerifier.requireValid(ws);

        Path seed = temp.resolve("seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- concrete AC\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "story-w3", seed);
        AnalysisPackageBuilder.build(ws, "story-w3");

        StoryWorkflowMachine.start(ws, "story-w3");
        DiscoveryRecords.writeReport(ws, "story-w3", "facts");
        GapRecords.write(ws, "story-w3", GapStatus.CLEAR, 0, "ok");
        StoryWorkflowState planning = StoryWorkflowMachine.advance(ws, "story-w3");
        assertEquals(WorkflowStage.PLANNING, planning.stage());
        assertTrue(Files.isRegularFile(ws.resolve(".story/story-w3/workflow-state.properties")));
        assertTrue(Files.isRegularFile(ws.resolve(".story/story-w3/packages/analysis/manifest.md")));
    }

    @Test
    void stopAfterAnalysisDoesNotAdvance() throws Exception {
        Path ws = temp.resolve("stop");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve("s.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- ac\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "story-stop", seed);
        StoryWorkflowMachine.start(ws, "story-stop");
        StoryWorkflowMachine.stop(ws, "story-stop", "BLOCKED gap unresolved");
        assertThrows(
                IllegalWorkflowTransitionException.class,
                () -> StoryWorkflowMachine.advance(ws, "story-stop"));
        assertEquals(WorkflowStatus.STOPPED, StoryWorkflowMachine.load(ws, "story-stop").status());
    }
}
