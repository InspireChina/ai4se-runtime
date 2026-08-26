package com.ai4se.orchestration.verification;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AcceptanceProbeCandidatesTest {

    @TempDir
    Path temp;

    @Test
    void freezesReviewedCandidateAndValidatesEveryAcceptanceProbe() throws Exception {
        String story = "s1";
        Files.createDirectories(temp.resolve(".story/s1"));
        Files.write(temp.resolve(".story/s1/requirement.md"), (""
                + "## raw\nx\n\n## goal\ny\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- one\n") .getBytes(StandardCharsets.UTF_8));
        DiscoveryRecords.writeReport(temp, story, "facts");
        GapRecords.write(temp, story, GapStatus.CLEAR, 0, 0, "clear");
        Path planning = PlanRecords.planningDir(temp, story);
        Files.createDirectories(planning);
        Files.write(planning.resolve("plan.md"), (""
                + "## Design\n\nx\n\n## Allowed Files\n\n- src/A.java\n\n## Change Map\n\n- x\n\n"
                + "## Test Strategy\n\n- AC1: probe\n\n## Impact Assessment\n\n"
                + "- api: NOT_APPLICABLE\n- data: NOT_APPLICABLE\n- authorization: NOT_APPLICABLE\n"
                + "- ui: NOT_APPLICABLE\n- observability: NOT_APPLICABLE\n")
                .getBytes(StandardCharsets.UTF_8));
        Path candidate = AcceptanceProbeCandidates.candidateRoot(temp, story);
        Files.createDirectories(candidate);
        Path probe = candidate.resolve("ac1.sh");
        Files.write(probe, "#!/usr/bin/env bash\ntrue\n".getBytes(StandardCharsets.UTF_8));
        String sha = AcceptanceProbeSet.sha256(probe);
        Files.write(candidate.resolve("probes.properties"), (""
                + "ac.count=1\n"
                + "ac.1.path=.ai4se/acceptance-probes/s1/ac1.sh\n"
                + "ac.1.sha256=" + sha + "\n"
                + "ac.1.command=bash .ai4se/acceptance-probes/s1/ac1.sh\n")
                .getBytes(StandardCharsets.UTF_8));

        Path frozen = AcceptanceProbeCandidates.freeze(temp, story);

        assertTrue(Files.isRegularFile(frozen.resolve("probes.properties")));
        assertTrue(Files.isRegularFile(frozen.resolve("ac1.sh")));
    }

    @Test
    void rejectsCandidateThatLetsMissingSelectedMavenTestsPass() throws Exception {
        String story = "s-bypass";
        preparePlan(story);
        Path candidate = AcceptanceProbeCandidates.candidateRoot(temp, story);
        Files.createDirectories(candidate);
        Path probe = candidate.resolve("ac1.sh");
        Files.write(probe, ("#!/usr/bin/env bash\n"
                + "mvn -Dtest=MissingTest -DfailIfNoTests=false test\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(candidate.resolve("probes.properties"), manifest(story, probe)
                .getBytes(StandardCharsets.UTF_8));

        StageGateException ex = assertThrows(
                StageGateException.class, () -> AcceptanceProbeCandidates.freeze(temp, story));
        assertTrue(ex.getMessage().contains("permits missing selected tests"), ex.getMessage());
        assertTrue(!Files.exists(temp.resolve(".ai4se/acceptance-probes").resolve(story)));
    }

    @Test
    void permitsMultiModuleSelectedTestWhenTargetTestMustStillExist() throws Exception {
        String story = "s-reactor";
        preparePlan(story);
        Path candidate = AcceptanceProbeCandidates.candidateRoot(temp, story);
        Files.createDirectories(candidate);
        Path probe = candidate.resolve("ac1.sh");
        Files.write(probe, ("#!/usr/bin/env bash\n"
                + "mvn -pl core -am -Dtest=StoryTest#ac1 -DfailIfNoTests=false "
                + "-Dsurefire.failIfNoSpecifiedTests=true test\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(candidate.resolve("probes.properties"), manifest(story, probe)
                .getBytes(StandardCharsets.UTF_8));

        Path frozen = AcceptanceProbeCandidates.freeze(temp, story);
        assertTrue(Files.isRegularFile(frozen.resolve("ac1.sh")));
    }

    @Test
    void frozenUncommittedProbeIsAcceptedUntilItsShaActuallyChanges() throws Exception {
        String story = "s-sha";
        preparePlan(story);
        Path candidate = AcceptanceProbeCandidates.candidateRoot(temp, story);
        Files.createDirectories(candidate);
        Path probe = candidate.resolve("ac1.sh");
        Files.write(probe, "#!/usr/bin/env bash\ntrue\n".getBytes(StandardCharsets.UTF_8));
        Files.write(candidate.resolve("probes.properties"), manifest(story, probe)
                .getBytes(StandardCharsets.UTF_8));

        Path frozen = AcceptanceProbeCandidates.freeze(temp, story);
        AcceptanceProbeSet set = AcceptanceProbeSet.load(temp, story, 1);
        set.requireUnmodified(java.util.Collections.singletonList(
                ".ai4se/acceptance-probes/" + story + "/ac1.sh"));

        Files.write(frozen.resolve("ac1.sh"), "#!/usr/bin/env bash\nfalse\n"
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(StageGateException.class, () -> set.requireUnmodified(
                java.util.Collections.singletonList(
                        ".ai4se/acceptance-probes/" + story + "/ac1.sh")));
    }

    private void preparePlan(String story) throws Exception {
        Files.createDirectories(temp.resolve(".story").resolve(story));
        Files.write(temp.resolve(".story").resolve(story).resolve("requirement.md"), (""
                + "## raw\nx\n\n## goal\ny\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- one\n").getBytes(StandardCharsets.UTF_8));
        DiscoveryRecords.writeReport(temp, story, "facts");
        GapRecords.write(temp, story, GapStatus.CLEAR, 0, 0, "clear");
        Path planning = PlanRecords.planningDir(temp, story);
        Files.createDirectories(planning);
        Files.write(planning.resolve("plan.md"), (""
                + "## Design\n\nx\n\n## Allowed Files\n\n- src/A.java\n\n## Change Map\n\n- x\n\n"
                + "## Test Strategy\n\n- AC1: probe\n\n## Impact Assessment\n\n"
                + "- api: NOT_APPLICABLE\n- data: NOT_APPLICABLE\n- authorization: NOT_APPLICABLE\n"
                + "- ui: NOT_APPLICABLE\n- observability: NOT_APPLICABLE\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private String manifest(String story, Path probe) throws Exception {
        return "ac.count=1\n"
                + "ac.1.path=.ai4se/acceptance-probes/" + story + "/ac1.sh\n"
                + "ac.1.sha256=" + AcceptanceProbeSet.sha256(probe) + "\n"
                + "ac.1.command=bash .ai4se/acceptance-probes/" + story + "/ac1.sh\n";
    }
}
