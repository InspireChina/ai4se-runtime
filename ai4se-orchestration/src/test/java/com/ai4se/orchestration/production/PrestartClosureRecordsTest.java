package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.run.ProductionTerminal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PrestartClosureRecordsTest {

    @TempDir
    Path temp;

    @Test
    void closesOnlyStoppedPlanningWithoutBusinessMutation() throws Exception {
        Path story = story();
        Path record = PrestartClosureRecords.close(temp, "s1", "operator", "scope needs refinement", gitClean());

        assertTrue(Files.isRegularFile(record));
        assertTrue(PrestartClosureRecords.isClosedBeforeUnattended(story));
    }

    @Test
    void refusesIfBusinessSourceChanged() throws Exception {
        story();
        assertThrows(StageGateException.class,
                () -> PrestartClosureRecords.close(temp, "s1", "operator", "scope needs refinement",
                        new com.ai4se.execution.support.SequenceProcessInvoker(
                                com.ai4se.execution.support.SequenceProcessInvoker.ok(" M src/Main.java\n"))));
    }

    @Test
    void closesAnalysisPolicyStopBeforeAnyDevelopment() throws Exception {
        Path root = story();
        Files.delete(root.resolve("planning"));
        Files.write(root.resolve("workflow-state.properties"), ("story_id=s1\n"
                + "stage=ANALYSIS\nstatus=STOPPED\n").getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("run/state.properties"), ("terminal="
                + ProductionTerminal.FAILED_POLICY.name() + "\n").getBytes(StandardCharsets.UTF_8));

        Path record = PrestartClosureRecords.close(temp, "s1", "operator", "invalid prestart assumption", gitClean());
        assertTrue(PrestartClosureRecords.isClosedBeforeUnattended(root));
        assertTrue(new String(Files.readAllBytes(record), StandardCharsets.UTF_8)
                .contains("previous_terminal=FAILED_POLICY"));
    }

    private Path story() throws Exception {
        Path root = temp.resolve(".story/s1");
        Files.createDirectories(root.resolve("planning"));
        Files.createDirectories(root.resolve("run"));
        Files.write(root.resolve("workflow-state.properties"), ("story_id=s1\n"
                + "stage=PLANNING\nstatus=STOPPED\n").getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("run/state.properties"), ("terminal="
                + ProductionTerminal.STOPPED_NEEDS_PLAN_APPROVAL.name() + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("run/events.jsonl"), "{\"seq\":1}\n".getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static ProcessInvoker gitClean() {
        return new com.ai4se.execution.support.SequenceProcessInvoker(
                com.ai4se.execution.support.SequenceProcessInvoker.ok(""));
    }
}
