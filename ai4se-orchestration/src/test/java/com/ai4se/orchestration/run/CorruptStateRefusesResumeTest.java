package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CorruptStateRefusesResumeTest {

    @TempDir
    Path temp;

    @Test
    void sequenceMismatchRefusesResume() throws Exception {
        Path ws = temp.resolve("corrupt");
        Files.createDirectories(ws);
        String storyId = "story-corrupt";
        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun("src/", 3);
        ledger.stageStarted(com.ai4se.orchestration.workflow.WorkflowStage.ANALYSIS);
        // Corrupt: truncate events but leave inflated sequence in state.properties
        Files.write(ledger.eventsPath(), new byte[0]);
        StageGateException ex = assertThrows(
                StageGateException.class,
                ledger::requireConsistentForResume);
        assertTrue(ex.getMessage().toLowerCase().contains("corrupt"), ex.getMessage());
    }

    @Test
    void malformedEventLineRefusesResume() throws Exception {
        Path ws = temp.resolve("corrupt2");
        Files.createDirectories(ws);
        String storyId = "story-corrupt-2";
        RunLedger ledger = RunLedger.open(ws, storyId);
        ledger.beginRun("src/", 3);
        Files.write(
                ledger.eventsPath(),
                ("not-json\n").getBytes(StandardCharsets.UTF_8));
        // Align sequence to 1 so the malformed-line check fires.
        java.util.Properties p = new java.util.Properties();
        p.load(Files.newInputStream(ledger.statePath()));
        p.setProperty("last_event_sequence", "1");
        try (java.io.BufferedWriter w = Files.newBufferedWriter(ledger.statePath())) {
            p.store(w, "ai4se production run state");
        }
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> RunLedger.open(ws, storyId).requireConsistentForResume());
        assertTrue(ex.getMessage().toLowerCase().contains("corrupt"), ex.getMessage());
    }
}
