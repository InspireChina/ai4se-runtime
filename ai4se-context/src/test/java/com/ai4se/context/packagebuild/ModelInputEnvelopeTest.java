package com.ai4se.context.packagebuild;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModelInputEnvelopeTest {

    @TempDir
    Path temp;

    @Test
    void embedsEveryPriorityOneSliceWithItsHash() throws Exception {
        Path slices = temp.resolve("slices");
        Files.createDirectories(slices);
        Files.write(slices.resolve("acceptance.md"), "- AC exact\n".getBytes(StandardCharsets.UTF_8));
        Files.write(slices.resolve("rules.md"), "must preserve boundary\n".getBytes(StandardCharsets.UTF_8));

        Path input = ModelInputEnvelope.write(
                temp,
                "Development",
                "story-1",
                "Implement only the approved change.",
                Arrays.asList("slices/acceptance.md", "slices/rules.md"),
                PackageBudget.ofBytes(10_000));

        String text = new String(Files.readAllBytes(input), StandardCharsets.UTF_8);
        assertTrue(text.contains("Implement only the approved change."));
        assertTrue(text.contains("AC exact"));
        assertTrue(text.contains("must preserve boundary"));
        assertTrue(text.contains("sha256:"));
    }

    @Test
    void refusesRatherThanSilentlyDroppingPriorityOneText() throws Exception {
        Path slices = temp.resolve("slices");
        Files.createDirectories(slices);
        Files.write(slices.resolve("large.md"), "this cannot fit\n".getBytes(StandardCharsets.UTF_8));

        PackageRefuseException error = assertThrows(
                PackageRefuseException.class,
                () -> ModelInputEnvelope.write(
                        temp,
                        "Development",
                        "story-1",
                        "task",
                        Arrays.asList("slices/large.md"),
                        PackageBudget.ofBytes(1)));
        assertTrue(error.getMessage().contains("CONTEXT_CONTRACT_TOO_LARGE"));
    }

    @Test
    void countsEnvelopeFramingInTheFiniteBudget() throws Exception {
        Path slices = temp.resolve("slices");
        Files.createDirectories(slices);
        Files.write(slices.resolve("small.md"), "x\n".getBytes(StandardCharsets.UTF_8));

        PackageRefuseException error = assertThrows(
                PackageRefuseException.class,
                () -> ModelInputEnvelope.write(
                        temp,
                        "Development",
                        "story-1",
                        "task",
                        Arrays.asList("slices/small.md"),
                        PackageBudget.ofBytes(64)));
        assertTrue(error.getMessage().contains("actual="));
    }
}
