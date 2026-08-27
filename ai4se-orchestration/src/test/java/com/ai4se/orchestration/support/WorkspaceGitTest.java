package com.ai4se.orchestration.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceGitTest {

    @TempDir
    Path temp;

    @Test
    void parsePorcelainHandlesRename() {
        List<String> paths = WorkspaceGit.parsePorcelain("R  old.txt -> src/A.java\n M src/B.java\n");
        assertEquals(Arrays.asList("src/A.java", "src/B.java"), paths);
    }

    @Test
    void ignoresStoryAndTargetForMutation() {
        assertTrue(WorkspaceGit.isIgnorableForMutation(".story/x/y.md"));
        assertTrue(WorkspaceGit.isIgnorableForMutation("target/classes/A.class"));
        assertFalse(WorkspaceGit.isIgnorableForMutation("src/A.java"));
    }

    @Test
    void headShaObserved() throws Exception {
        String sha = WorkspaceGit.headSha(
                temp, new SequenceProcessInvoker(SequenceProcessInvoker.ok("deadbeefcafebabe\n")));
        assertEquals("deadbeefcafebabe", sha);
    }

    @Test
    void rejectRemoteSyncedAsPushed() {
        assertThrows(
                StageGateException.class,
                () -> WorkspaceGit.requireNotPushedClean(
                        temp,
                        new SequenceProcessInvoker(
                                SequenceProcessInvoker.ok("## main...origin/main\n"))));
    }

    @Test
    void allowLocalAhead() throws Exception {
        WorkspaceGit.requireNotPushedClean(
                temp,
                new SequenceProcessInvoker(
                        SequenceProcessInvoker.ok("## main...origin/main [ahead 1]\n")));
    }

    @Test
    void activeStoryControlFilesMayContinueButOtherDirtyPathsStillRefuse() throws Exception {
        List<String> allowed = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                temp,
                "s1",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .story/s1/analysis/clarification.resolved.md\n")));
        assertTrue(allowed.isEmpty(), allowed.toString());

        List<String> rejected = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                temp,
                "s1",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .story/s2/requirement.md\n M src/A.java\n M .ai4se/rules/java.md\n")));
        assertEquals(Arrays.asList(".story/s2/requirement.md", "src/A.java", ".ai4se/rules/java.md"),
                rejected);
    }

    @Test
    void repositoryBootstrapArtifactsDoNotRequireAStandaloneCustomerCommit() throws Exception {
        List<String> allowed = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                temp,
                "s1",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .ai4se/repository/facts.md\n"
                                + "?? .ai4se/index/knowledge.yaml\n"
                                + "?? .ai4se/knowledge/orders.md\n"
                                + "?? .story/s1/requirement.md\n")));
        assertTrue(allowed.isEmpty(), allowed.toString());
    }

    @Test
    void completedPredecessorEvidenceAndFrozenProbesDoNotBlockSerialSuccessor() throws Exception {
        Path state = temp.resolve(".story/s1/workflow-state.properties");
        Files.createDirectories(state.getParent());
        Files.write(state, "story_id=s1\nstatus=COMPLETED\n".getBytes(StandardCharsets.UTF_8));

        List<String> allowed = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                temp,
                "s2",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .story/s1/delivery/delivery.md\n?? .ai4se/acceptance-probes/s1/probes.properties\n")));
        assertTrue(allowed.isEmpty(), allowed.toString());
    }

    @Test
    void boundedFailedPredecessorEvidenceDoesNotBlockASeparateSerialStory() throws Exception {
        Path state = temp.resolve(".story/s1/workflow-state.properties");
        Files.createDirectories(state.getParent());
        Files.write(state, "story_id=s1\nstage=DEVELOPMENT\nstatus=STOPPED\n"
                .getBytes(StandardCharsets.UTF_8));
        Path run = temp.resolve(".story/s1/run/state.properties");
        Files.createDirectories(run.getParent());
        Files.write(run, "terminal=FAILED_VERIFICATION_BUDGET\n"
                .getBytes(StandardCharsets.UTF_8));

        List<String> allowed = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                temp,
                "s2",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .story/s1/verification/report-round-3.md\n"
                                + "?? .ai4se/acceptance-probes/s1/probes.properties\n")));
        assertTrue(allowed.isEmpty(), allowed.toString());
    }

    @Test
    void completedStoryEvidenceDoesNotBlockRepositoryLifecycleButSourceStillDoes() throws Exception {
        Path state = temp.resolve(".story/s1/workflow-state.properties");
        Files.createDirectories(state.getParent());
        Files.write(state, "story_id=s1\nstatus=COMPLETED\n".getBytes(StandardCharsets.UTF_8));

        List<String> allowed = WorkspaceGit.productionCleanGateDirtyPathsAllowCompletedStories(
                temp,
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .story/s1/lifecycle/knowledge-stale.md\n"
                                + "?? .ai4se/acceptance-probes/s1/probes.properties\n")));
        assertTrue(allowed.isEmpty(), allowed.toString());

        List<String> rejected = WorkspaceGit.productionCleanGateDirtyPathsAllowCompletedStories(
                temp,
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(" M src/Main.java\n")));
        assertEquals(Arrays.asList("src/Main.java"), rejected);
    }

    @Test
    void inFlightStoryStillBlocksRepositoryLifecycleWork() throws Exception {
        List<String> rejected = WorkspaceGit.productionCleanGateDirtyPathsAllowCompletedStories(
                temp,
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .story/in-flight/analysis/clarification.questions.md\n")));
        assertEquals(Arrays.asList(".story/in-flight/analysis/clarification.questions.md"), rejected);
    }

    @Test
    void runtimeOwnedSerialQueueDoesNotBlockNextStoryButOtherAi4seFilesStillDo() throws Exception {
        List<String> allowed = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                temp,
                "s2",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .ai4se/queue/serial-queue.properties\n")));
        assertTrue(allowed.isEmpty(), allowed.toString());

        List<String> rejected = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                temp,
                "s2",
                new SequenceProcessInvoker(SequenceProcessInvoker.ok(
                        "?? .ai4se/queue/other.properties\n")));
        assertEquals(Arrays.asList(".ai4se/queue/other.properties"), rejected);
    }
}
