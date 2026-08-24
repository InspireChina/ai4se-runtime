package com.ai4se.orchestration.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SpecificationRecordsTest {

    @TempDir
    Path temp;

    @Test
    void freezesCandidateOnlyAfterCandidateDecisionAndKeepsAttachmentLineage() throws Exception {
        Path story = temp.resolve(".story/s1");
        Files.createDirectories(story.resolve("specification"));
        Files.createDirectories(story.resolve("requirement-attachments"));
        Files.write(story.resolve("requirement-attachments/wireframe.png"), new byte[] {1});
        Files.write(story.resolve("specification/specification.result.properties"),
                "decision=CANDIDATE\n".getBytes(StandardCharsets.UTF_8));
        Files.write(story.resolve("specification/candidate-requirement.md"), (""
                + "# Candidate\n\n## raw\nraw request\n\n## goal\nshow order action\n\n"
                + "## in_scope\n- admin order detail\n\n## out_of_scope\n- order mutation\n\n"
                + "## attachments\n- wireframe.png\n\n## acceptance\n- option is visible\n")
                .getBytes(StandardCharsets.UTF_8));

        Path frozen = SpecificationRecords.freezeCandidate(temp, "s1");

        assertTrue(Files.isRegularFile(frozen));
        assertTrue(Files.isRegularFile(story.resolve("specification/frozen-inputs.properties")));
        assertEquals(SpecificationRecords.Outcome.CANDIDATE,
                SpecificationRecords.requireOutcome(temp, "s1"));
    }

    @Test
    void refusesToFreezeWhenClarificationIsStillRequired() throws Exception {
        Path spec = temp.resolve(".story/s2/specification");
        Files.createDirectories(spec);
        Files.write(spec.resolve("specification.result.properties"),
                "decision=CLARIFICATION_REQUIRED\n".getBytes(StandardCharsets.UTF_8));
        Files.write(spec.resolve("clarification.questions.md"),
                ("# Questions\n\n## Q1\nWhich status should be shown?\n\n"
                        + "## Code Evidence\n\n- litemall-admin-api/src/main/java/Order.java has status.\n")
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> SpecificationRecords.freezeCandidate(temp, "s2"));
    }

    @Test
    void refusesClarificationQuestionWithoutSourceEvidence() throws Exception {
        Path spec = temp.resolve(".story/s3/specification");
        Files.createDirectories(spec);
        Files.write(spec.resolve("specification.result.properties"),
                "decision=CLARIFICATION_REQUIRED\n".getBytes(StandardCharsets.UTF_8));
        Files.write(spec.resolve("clarification.questions.md"),
                "# Questions\n\n## Q1\nWhich status should be shown?\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> SpecificationRecords.requireOutcome(temp, "s3"));
    }

    @Test
    void requiresCandidateToRetainAnsweredChoices() throws Exception {
        Path spec = temp.resolve(".story/s4/specification");
        Files.createDirectories(spec);
        Files.write(spec.resolve("specification.result.properties"),
                "decision=CANDIDATE\n".getBytes(StandardCharsets.UTF_8));
        Files.write(spec.resolve("clarification.resolved.md"),
                "# Resolved\n\n## Answer\n\nQ1=A\n".getBytes(StandardCharsets.UTF_8));
        Files.write(spec.resolve("candidate-requirement.md"), (""
                + "## raw\nraw\n\n## goal\ngoal\n\n## in_scope\n- scope\n\n"
                + "## out_of_scope\n- no mutation\n\n## acceptance\n- visible\n")
                .getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> SpecificationRecords.freezeCandidate(temp, "s4"));
    }
}
