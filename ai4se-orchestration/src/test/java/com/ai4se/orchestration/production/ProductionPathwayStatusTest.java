package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProductionPathwayStatusTest {

    @TempDir
    Path temp;

    @Test
    void statusExplainsNextActionForRawIntakeBeforeAProductionRunExists() throws Exception {
        Path input = temp.resolve(".story/s1/input");
        Files.createDirectories(input);
        Files.write(input.resolve("intake.properties"),
                "status=RAW_CAPTURED\n".getBytes(StandardCharsets.UTF_8));

        String status = ProductionPathway.formatStatus(temp, "s1");

        assertTrue(status.contains("run=absent"), status);
        assertTrue(status.contains("intake=RAW_CAPTURED"), status);
        assertTrue(status.contains("next=specify"), status);
    }

    @Test
    void statusPointsToSpecificationQuestionWhenCandidateCannotYetBeFrozen() throws Exception {
        Path spec = temp.resolve(".story/s2/specification");
        Files.createDirectories(spec);
        Files.write(spec.resolve("specification.result.properties"),
                "decision=CLARIFICATION_REQUIRED\n".getBytes(StandardCharsets.UTF_8));

        String status = ProductionPathway.formatStatus(temp, "s2");

        assertTrue(status.contains("question_file=.story/s2/specification/clarification.questions.md"), status);
        assertTrue(status.contains("next=answer-spec, then specify"), status);
    }
}
