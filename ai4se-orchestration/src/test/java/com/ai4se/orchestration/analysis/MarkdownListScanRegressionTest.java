package com.ai4se.orchestration.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Prevent re-introducing hand-rolled markdown list scans ({@code startsWith("- ")}).
 * Plan/Dev list sections must go through {@code MarkdownLists}.
 * <p>
 * Exclusions: formats that are not the Allowed/Priority1 section problem class
 * (change-list / session / yaml-ish lines still use markers by contract).
 */
final class MarkdownListScanRegressionTest {

    private static final Set<String> EXCLUDE = new HashSet<String>(Arrays.asList(
            "DevelopmentRecords.java",
            "SessionDecisionRecorder.java",
            "VerificationEntries.java"));

    @Test
    void orchestrationMainsMustNotHandRollBulletStartsWith() throws Exception {
        Path mainJava = Paths.get("ai4se-orchestration/src/main/java/com/ai4se/orchestration")
                .toAbsolutePath();
        if (!Files.isDirectory(mainJava)) {
            mainJava = Paths.get("src/main/java/com/ai4se/orchestration").toAbsolutePath();
        }
        assertTrue(Files.isDirectory(mainJava), "orchestration mains: " + mainJava);
        scan(mainJava);
    }

    private static void scan(Path dir) throws Exception {
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
        try {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    scan(p);
                    continue;
                }
                String name = p.getFileName().toString();
                if (!name.endsWith(".java") || EXCLUDE.contains(name)) {
                    continue;
                }
                String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                assertFalse(
                        src.contains("startsWith(\"- \")") || src.contains("startsWith(\"* \")"),
                        name + " must use MarkdownLists — not hand-rolled startsWith(\"- \" / \"* \")");
            }
        } finally {
            stream.close();
        }
    }
}
