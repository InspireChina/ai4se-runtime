package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Old approval.md must not bypass current writeScope/hint before Development. */
final class PathwayPlanAllowedWriteScopeGateTest {

    @TempDir
    Path temp;

    @Test
    void refusesOldApprovedPlanOutsideCurrentWriteScope() throws Exception {
        Path ws = prepare(temp);
        PlanRecords.writeFormalPlan(
                ws, "s1", "old plan", Collections.singletonList("src/main/java/Outside.java"));
        ApprovalRecords.approvePlan(ws, "s1", "human", "pre-existing");

        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> PathwayRunner.enforcePlanAllowedWithinHint(
                        ws, "s1", Collections.singletonList("src/test/java")));
        assertTrue(ex.getMessage().contains("subset") || ex.getMessage().contains("outside"),
                ex.getMessage());
    }

    @Test
    void acceptsPlanUnderDirectoryWriteScopeEvenWithOldApproval() throws Exception {
        Path ws = prepare(temp);
        PlanRecords.writeFormalPlan(
                ws,
                "s1",
                "ok plan",
                Collections.singletonList("src/main/java/com/example/A.java"));
        ApprovalRecords.approvePlan(ws, "s1", "human", "pre-existing");

        assertDoesNotThrow(() -> PathwayRunner.enforcePlanAllowedWithinHint(
                ws, "s1", Arrays.asList("src/main/java", "src/test/java")));
    }

    private static Path prepare(Path temp) throws Exception {
        Path ws = temp.resolve("cust");
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(ws.resolve(".story/s1/analysis"));
        Files.write(
                ws.resolve(".story/s1/analysis/discovery.skip.md"),
                ("# skip\n- rationale: t\n- approver: t\n").getBytes(StandardCharsets.UTF_8));
        GapRecords.write(ws, "s1", GapStatus.CLEAR, 0, "ok");
        return ws;
    }
}
