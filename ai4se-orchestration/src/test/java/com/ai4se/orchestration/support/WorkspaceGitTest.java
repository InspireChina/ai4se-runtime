package com.ai4se.orchestration.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.SequenceProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
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
}
