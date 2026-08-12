package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.LowRiskPlanApproval;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlanAllowedMustBeSubsetOfOperatorWriteScopeTest {

    @TempDir
    Path temp;

    @Test
    void acceptsPlanFileUnderWriteScopeDirectory() throws Exception {
        assertDoesNotThrow(() -> OperatorWriteScope.requirePlanAllowedSubset(
                Collections.singletonList("src/main/java/com/example/A.java"),
                Collections.singletonList("src/main/java")));
    }

    @Test
    void refusesPlanFileOutsideWriteScope() {
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> OperatorWriteScope.requirePlanAllowedSubset(
                        Collections.singletonList("src/main/java/A.java"),
                        Collections.singletonList("src/test/java")));
        assertTrue(ex.getMessage().contains("subset"), ex.getMessage());
    }

    @Test
    void lowRiskApprovalHonorsDirectoryWriteScopeHint() throws Exception {
        Path ws = prepare(temp, "src/main/java/com/example/Foo.java");
        assertTrue(LowRiskPlanApproval.isEligible(
                ws,
                "s1",
                Collections.singletonList("src/main/java"),
                "true",
                false));
        assertTrue(LowRiskPlanApproval.ineligibleReason(
                        ws,
                        "s1",
                        Collections.singletonList("src/test/java"),
                        "true",
                        false)
                .contains("超出 hint"));
    }

    private static Path prepare(Path temp, String allowedInPlan) throws Exception {
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
        PlanRecords.writeFormalPlan(ws, "s1", "design", Collections.singletonList(allowedInPlan));
        return ws;
    }
}
