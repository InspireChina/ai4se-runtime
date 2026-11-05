package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Pr4BackgroundRunCaptureScriptTest {

    @TempDir
    Path temp;

    @Test
    void recordsPropertiesWhenChildReturnsPolicyExit50() throws Exception {
        Path script = java.nio.file.Paths.get("scripts", "pr4-background-run-capture.sh");
        if (!Files.isRegularFile(script)) {
            script = java.nio.file.Paths.get("..", "scripts", "pr4-background-run-capture.sh");
        }
        script = script.toAbsolutePath();
        assertTrue(Files.isRegularFile(script), script.toString());
        Path output = temp.resolve("run.properties");

        Process process = new ProcessBuilder(
                "bash", script.toString(), output.toString(), "--", "sh", "-c", "exit 50")
                .start();

        assertEquals(50, process.waitFor());
        String properties = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        assertTrue(properties.contains("runtime_exit_code=50"), properties);
        assertTrue(properties.contains("started_at_utc="), properties);
        assertTrue(properties.contains("ended_at_utc="), properties);
    }
}
