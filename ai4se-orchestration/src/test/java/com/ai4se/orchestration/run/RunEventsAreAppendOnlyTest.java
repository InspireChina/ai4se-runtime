package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RunEventsAreAppendOnlyTest {

    @TempDir
    Path temp;

    @Test
    void eventsJsonlOnlyGrowsAndPreservesPriorLines() throws Exception {
        Path ws = temp.resolve("events");
        Files.createDirectories(ws);
        String storyId = "story-events";
        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun("src/main/java", 3);
        ledger.stageStarted(WorkflowStage.ANALYSIS);
        ledger.stageCompleted(WorkflowStage.ANALYSIS);

        List<String> before = ledger.readEventLines();
        assertEquals(3, before.size());
        String snapshot = new String(Files.readAllBytes(ledger.eventsPath()), StandardCharsets.UTF_8);

        ledger.stageStarted(WorkflowStage.PLANNING);
        ledger.stageCompleted(WorkflowStage.PLANNING);

        List<String> after = ledger.readEventLines();
        assertEquals(5, after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i), after.get(i));
        }
        String grown = new String(Files.readAllBytes(ledger.eventsPath()), StandardCharsets.UTF_8);
        assertTrue(grown.startsWith(snapshot));
        assertTrue(grown.length() > snapshot.length());
        assertEquals(5L, ledger.readState().lastEventSequence);
    }
}
