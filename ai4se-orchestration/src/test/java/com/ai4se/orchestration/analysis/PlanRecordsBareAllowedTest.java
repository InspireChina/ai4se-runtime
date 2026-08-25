package com.ai4se.orchestration.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression for cloud hang: model followed "bare relative path" and omitted {@code - } markers;
 * reader must accept bare lines under {@code ## Allowed Files}.
 */
final class PlanRecordsBareAllowedTest {

    @TempDir
    Path temp;

    @Test
    void readsBareAllowedLinesWithoutBulletMarkers() throws Exception {
        String storyId = "bare-allowed";
        writePlan(
                storyId,
                "# Plan\n\n## Design\n\nok\n\n## Allowed Files\n\n"
                        + "wmp-be-backend/pom.xml\n"
                        + "wmp-be-backend/src/A.java\n");

        List<String> allowed = PlanRecords.readAllowedFiles(temp, storyId);
        assertEquals(2, allowed.size());
        assertEquals("wmp-be-backend/pom.xml", allowed.get(0));
        assertEquals("wmp-be-backend/src/A.java", allowed.get(1));
    }

    @Test
    void stillReadsBulletMarkedAllowedLines() throws Exception {
        String storyId = "bullet-allowed";
        prepareDiscoveryAndGap(storyId);
        PlanRecords.writeFormalPlan(
                temp, storyId, "design", Arrays.asList("src/A.java", "src/B.java"));
        List<String> allowed = PlanRecords.readAllowedFiles(temp, storyId);
        assertEquals(2, allowed.size());
        assertTrue(allowed.contains("src/A.java"));
        assertTrue(allowed.contains("src/B.java"));
    }

    @Test
    void emptyAllowedSectionStillFails() throws Exception {
        String storyId = "empty-allowed";
        writePlan(storyId, "# Plan\n\n## Design\n\nx\n\n## Allowed Files\n\n");
        StageGateException ex = assertThrows(
                StageGateException.class, () -> PlanRecords.readAllowedFiles(temp, storyId));
        assertTrue(ex.getMessage().contains("Allowed"));
    }

    @Test
    void executionPlanRequiresExplicitImpactAndApiContractWhenApiIsPresent() throws Exception {
        String storyId = "impact";
        prepareDiscoveryAndGap(storyId);
        Files.createDirectories(temp.resolve("src"));
        Files.write(temp.resolve("src/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        Path planning = PlanRecords.planningDir(temp, storyId);
        Files.createDirectories(planning);
        Files.write(planning.resolve(PlanRecords.PLAN_FILE), (""
                + "# Plan\n\n## Design\n\nuse existing endpoint\n\n## Allowed Files\n\n- src/A.java\n\n"
                + "## Change Map\n\n- src/A.java: add projection\n\n## Test Strategy\n\n- AC1: unit test\n\n"
                + "## Impact Assessment\n\n- api: PRESENT\n- data: NOT_APPLICABLE\n"
                + "- authorization: NOT_APPLICABLE\n- ui: NOT_APPLICABLE\n- observability: NOT_APPLICABLE\n")
                .getBytes(StandardCharsets.UTF_8));

        assertThrows(StageGateException.class,
                () -> PlanRecords.requireExecutionArtifacts(temp, storyId));
        Files.write(planning.resolve("api-contract.md"), "# API\n".getBytes(StandardCharsets.UTF_8));
        Files.write(planning.resolve(PlanRecords.PLAN_FILE), (""
                + "# Plan\n\n## Design\n\nuse existing endpoint\n\n## Allowed Files\n\n- src/A.java\n\n"
                + "## Change Map\n\n- src/A.java: add projection\n\n## Test Strategy\n\n- AC1: unit test\n\n"
                + "## Impact Assessment\n\n- api: PRESENT\n- data: NOT_APPLICABLE\n"
                + "- authorization: NOT_APPLICABLE\n- ui: NOT_APPLICABLE\n- observability: NOT_APPLICABLE\n\n"
                + "## Behavioral Scenarios\n\n"
                + "- id: api-projection | evidence: src/A.java | verification: ENTRY_TEST\n")
                .getBytes(StandardCharsets.UTF_8));

        PlanRecords.requireExecutionArtifacts(temp, storyId);
        assertTrue(Files.isRegularFile(planning.resolve("impact-assessment.md")));
        assertTrue(Files.isRegularFile(planning.resolve("impact/impact-index.properties")));
    }

    @Test
    void executionPlanRejectsImpactScenarioWithNonExecutableVerificationReference() throws Exception {
        String storyId = "invalid-impact-verification";
        prepareDiscoveryAndGap(storyId);
        Files.createDirectories(temp.resolve("src"));
        Files.write(temp.resolve("src/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        Path planning = PlanRecords.planningDir(temp, storyId);
        Files.createDirectories(planning);
        Files.write(planning.resolve("api-contract.md"), "# API\n".getBytes(StandardCharsets.UTF_8));
        Files.write(planning.resolve(PlanRecords.PLAN_FILE), (""
                + "# Plan\n\n## Design\n\nok\n\n## Allowed Files\n\n- src/A.java\n\n"
                + "## Change Map\n\n- src/A.java: change\n\n## Test Strategy\n\n- AC1: test\n\n"
                + "## Impact Assessment\n\n- api: PRESENT\n- data: NOT_APPLICABLE\n"
                + "- authorization: NOT_APPLICABLE\n- ui: NOT_APPLICABLE\n- observability: NOT_APPLICABLE\n\n"
                + "## Behavioral Scenarios\n\n"
                + "- id: api-projection | evidence: src/A.java | verification: mvn arbitrary:test\n")
                .getBytes(StandardCharsets.UTF_8));

        StageGateException ex = assertThrows(
                StageGateException.class, () -> PlanRecords.requireExecutionArtifacts(temp, storyId));
        assertTrue(ex.getMessage().contains("ENTRY_TEST"));
    }

    @Test
    void executionPlanAcceptsBareImpactDeclarations() throws Exception {
        String storyId = "bare-impact";
        prepareDiscoveryAndGap(storyId);
        Path planning = PlanRecords.planningDir(temp, storyId);
        Files.createDirectories(planning);
        Files.write(planning.resolve(PlanRecords.PLAN_FILE), (""
                + "# Plan\n\n## Design\n\nok\n\n## Allowed Files\n\n- src/A.java\n\n"
                + "## Change Map\n\n- src/A.java: add projection\n\n## Test Strategy\n\n- AC1: unit test\n\n"
                + "## Impact Assessment\n\napi: NOT_APPLICABLE\ndata: NOT_APPLICABLE\n"
                + "authorization: NOT_APPLICABLE\nui: NOT_APPLICABLE\nobservability: NOT_APPLICABLE\n")
                .getBytes(StandardCharsets.UTF_8));

        PlanRecords.requireExecutionArtifacts(temp, storyId);
        assertTrue(Files.isRegularFile(planning.resolve("impact-assessment.md")));
    }

    @Test
    void executionPlanAcceptsMarkdownImpactTableAndThisStoryFutureProbePath() throws Exception {
        String storyId = "table-impact";
        prepareDiscoveryAndGap(storyId);
        Files.createDirectories(temp.resolve("src"));
        Files.write(temp.resolve("src/A.java"), "class A {}\n".getBytes(StandardCharsets.UTF_8));
        Path planning = PlanRecords.planningDir(temp, storyId);
        Files.createDirectories(planning);
        Files.write(planning.resolve("api-contract.md"), "# API\n".getBytes(StandardCharsets.UTF_8));
        Files.write(planning.resolve(PlanRecords.PLAN_FILE), (""
                + "# Plan\n\n## Design\n\nok\n\n## Allowed Files\n\n- src/A.java\n\n"
                + "## Change Map\n\n- src/A.java: change\n\n## Test Strategy\n\n- AC1: probe\n\n"
                + "## Impact Assessment\n\n| area | conclusion |\n| --- | --- |\n"
                + "| api | PRESENT |\n| data | NOT_APPLICABLE |\n| authorization | NOT_APPLICABLE |\n"
                + "| ui | NOT_APPLICABLE |\n| observability | NOT_APPLICABLE |\n\n"
                + "## Behavioral Scenarios\n\n- id: api-projection | evidence: src/A.java | verification: "
                + ".ai4se/acceptance-probes/" + storyId + "/ac-1.sh\n")
                .getBytes(StandardCharsets.UTF_8));

        PlanRecords.requireExecutionArtifacts(temp, storyId);
        assertTrue(Files.isRegularFile(planning.resolve("impact-assessment.md")));
    }

    private void writePlan(String storyId, String body) throws Exception {
        Path plan = PlanRecords.planningDir(temp, storyId).resolve(PlanRecords.PLAN_FILE);
        Files.createDirectories(plan.getParent());
        Files.write(plan, body.getBytes(StandardCharsets.UTF_8));
    }

    private void prepareDiscoveryAndGap(String storyId) throws Exception {
        Files.createDirectories(temp.resolve(".story").resolve(storyId));
        DiscoveryRecords.writeReport(temp, storyId, "facts");
        GapRecords.write(temp, storyId, GapStatus.CLEAR, 0, "ok");
    }
}
