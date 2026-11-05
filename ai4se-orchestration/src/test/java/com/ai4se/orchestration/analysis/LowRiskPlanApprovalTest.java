package com.ai4se.orchestration.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LowRiskPlanApprovalTest {

    @TempDir
    Path temp;

    @Test
    void eligibleWhenAllowedSubsetAndVerifyInEntries() throws Exception {
        Path ws = prepare(temp, "src/test/java/FooTest.java");
        assertTrue(LowRiskPlanApproval.isEligible(
                ws,
                "s1",
                Arrays.asList("src/test/java/FooTest.java", "src/main/java/Foo.java"),
                "true",
                false));
        assertNull(LowRiskPlanApproval.ineligibleReason(
                ws,
                "s1",
                Arrays.asList("src/test/java/FooTest.java", "src/main/java/Foo.java"),
                "true",
                false));
    }

    @Test
    void ineligibleWhenPlanExpandsBeyondHint() throws Exception {
        Path ws = prepare(temp, "src/main/java/Extra.java");
        assertFalse(LowRiskPlanApproval.isEligible(
                ws, "s1", Collections.singletonList("src/test/java/FooTest.java"), "true", false));
        assertTrue(LowRiskPlanApproval.ineligibleReason(
                        ws, "s1", Collections.singletonList("src/test/java/FooTest.java"), "true", false)
                .contains("超出 hint"));
    }

    @Test
    void ineligibleWhenRequireTestPathsAndMainPresent() throws Exception {
        Path ws = prepare(temp, "src/main/java/Foo.java");
        assertFalse(LowRiskPlanApproval.isEligible(
                ws, "s1", Collections.singletonList("src/main/java/Foo.java"), "true", true));
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
