package com.ai4se.runtime.demo.pathway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FieldPathwayEntriesGuardTest {

    @TempDir
    Path temp;

    @Test
    void doesNotOverwriteHonestEntries() throws Exception {
        Path ws = temp.resolve("cust");
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.createDirectories(ws.resolve(".story"));
        Path entries = ws.resolve(".ai4se/repository/entries.yaml");
        String original = ""
                + "build:\n"
                + "  - mvn -q -DskipTests package\n"
                + "test:\n"
                + "  - mvn -pl yudao-framework/yudao-common -am -Dtest=CollectionUtilsTest test\n";
        Files.write(entries, original.getBytes(StandardCharsets.UTF_8));
        Files.write(
                ws.resolve(".ai4se/repository/baseline.md"),
                "# Repository Baseline\n\n## Modules\n- demo\n".getBytes(StandardCharsets.UTF_8));
        Files.write(
                ws.resolve(".ai4se/index/knowledge.yaml"),
                "entries: []\n".getBytes(StandardCharsets.UTF_8));

        FieldPathwayMain.ensureEntriesPresent(ws, "mvn -q test");
        String after = new String(Files.readAllBytes(entries), StandardCharsets.UTF_8);
        assertTrue(after.contains("CollectionUtilsTest"), after);
        assertFalse(after.contains("FieldPathway will not overwrite") && after.equals(original) == false
                        && !after.equals(original),
                "should keep original content");
        assertTrue(after.equals(original), after);
    }
}
