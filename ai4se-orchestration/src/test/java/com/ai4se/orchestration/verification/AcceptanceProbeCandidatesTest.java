package com.ai4se.orchestration.verification;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
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
}
