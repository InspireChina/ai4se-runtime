package com.ai4se.orchestration.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.story.StoryIntake;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SpecificationAdapterExecutionTest {

    @TempDir
    Path temp;

    @Test
    void acceptsCandidateOnlyWhenAdapterWritesTheMachineResultContract() throws Exception {
        Files.createDirectories(temp.resolve(".ai4se/repository"));
        Files.createDirectories(temp.resolve(".story"));
        StoryIntake.capture(temp, "s1", "add a detail field", Collections.<Path>emptyList());
        FunctionalModelCliAdapter adapter = new FunctionalModelCliAdapter("spec", request -> {
            try {
                Path dir = request.workspace().resolve(".story/s1/specification");
                Files.createDirectories(dir);
                Files.write(dir.resolve("specification.result.properties"),
                        "decision=CANDIDATE\nsummary=ready\n".getBytes(StandardCharsets.UTF_8));
                Files.write(dir.resolve("candidate-requirement.md"), (""
                        + "## raw\nx\n\n## goal\ny\n\n## in_scope\n- a\n\n"
                        + "## out_of_scope\n- b\n\n## acceptance\n- works\n")
                        .getBytes(StandardCharsets.UTF_8));
                return AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap());
            } catch (Exception e) {
                return AdapterResult.failure(1, "", "", e.getMessage(),
                        Collections.<String, String>emptyMap());
            }
        });

        SpecificationRecords.Outcome outcome = SpecificationAdapterExecution.submit(
                temp, "s1", adapter, Duration.ofMinutes(1));

        assertEquals(SpecificationRecords.Outcome.CANDIDATE, outcome);
        assertTrue(Files.isRegularFile(temp.resolve(".story/s1/execution/adapter-specification.md")));
    }
}
