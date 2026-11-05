package com.ai4se.context.packagebuild;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.story.StoryIntake;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SpecificationPackageBuilderTest {

    @TempDir
    Path temp;

    @Test
    void embedsRawInputAndRepositoryFactsAsPriorityOne() throws Exception {
        onboard();
        Files.write(temp.resolve(".ai4se/repository/facts.md"),
                "# Facts\n\n- source: pom.xml\n".getBytes(StandardCharsets.UTF_8));
        StoryIntake.capture(temp, "s1", "add order projection", Collections.<Path>emptyList());

        ContextPackageResult result = SpecificationPackageBuilder.build(
                temp, "s1", PackageBudget.PRODUCTION_P1);

        String input = new String(Files.readAllBytes(result.packageDir().resolve("model-input.md")),
                StandardCharsets.UTF_8);
        assertTrue(input.contains("add order projection"), input);
        assertTrue(input.contains("source: pom.xml"), input);
    }

    @Test
    void refusesWhenRawIntakeWasNotCaptured() {
        assertThrows(PackageRefuseException.class,
                () -> SpecificationPackageBuilder.build(temp, "missing", PackageBudget.PRODUCTION_P1));
    }

    @Test
    void carriesAnsweredSpecificationDecisionIntoTheNextModelInput() throws Exception {
        onboard();
        StoryIntake.capture(temp, "s2", "add order projection", Collections.<Path>emptyList());
        Path resolved = temp.resolve(".story/s2/specification/clarification.resolved.md");
        Files.createDirectories(resolved.getParent());
        Files.write(resolved, "# Resolved\n\n## Answer\n\nQ1=A\n".getBytes(StandardCharsets.UTF_8));

        ContextPackageResult result = SpecificationPackageBuilder.build(
                temp, "s2", PackageBudget.PRODUCTION_P1);

        String input = new String(Files.readAllBytes(result.packageDir().resolve("model-input.md")),
                StandardCharsets.UTF_8);
        assertTrue(input.contains("Q1=A"), input);
    }

    private void onboard() throws Exception {
        Files.createDirectories(temp.resolve(".ai4se/repository"));
        Files.createDirectories(temp.resolve(".story"));
    }
}
