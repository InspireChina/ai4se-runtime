package com.ai4se.review.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewPackageGeneratorTest {

    @TempDir
    Path temp;

    @Test
    void generate_createsStableSchemaLayout() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo.resolve("review-package").resolve("_template"));
        write(repo.resolve("review-package").resolve("SCHEMA.md"), "# schema\n");
        for (String section : ReviewPackageGenerator.REQUIRED_SECTIONS) {
            write(repo.resolve("review-package").resolve("_template").resolve(section + ".md"),
                    "# " + section + "\n\ntemplate\n");
        }

        Path out = new ReviewPackageGenerator(repo).generate("rp-test-001");

        assertEquals("rp-test-001", out.getFileName().toString());
        assertTrue(Files.isRegularFile(out.resolve("manifest.json")));
        for (String section : ReviewPackageGenerator.REQUIRED_SECTIONS) {
            assertTrue(Files.isRegularFile(out.resolve(section + ".md")), section);
        }

        String manifest = new String(Files.readAllBytes(out.resolve("manifest.json")), Charset.forName("UTF-8"));
        assertTrue(manifest.contains("\"schemaVersion\": \"1.0\""));
        assertTrue(manifest.contains("\"packageId\": \"rp-test-001\""));
        assertTrue(manifest.contains("\"generator\": \"ai4se-review-tools\""));
        for (String section : ReviewPackageGenerator.REQUIRED_SECTIONS) {
            assertTrue(manifest.contains("\"" + section + "\""), "manifest sections: " + section);
        }
    }

    @Test
    void requiredSections_areStableContract() {
        List<String> expected = Arrays.asList(
                "architecture",
                "changed-files",
                "tech-debt",
                "roadmap",
                "review-request");
        assertEquals(expected, ReviewPackageGenerator.REQUIRED_SECTIONS);
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(Charset.forName("UTF-8")));
    }
}
