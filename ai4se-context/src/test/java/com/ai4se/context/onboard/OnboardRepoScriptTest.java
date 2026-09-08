package com.ai4se.context.onboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.workspace.WorkspaceSlotVerifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W1：真实跑 onboard 脚本，多构建系统维度。 */
final class OnboardRepoScriptTest {

    @TempDir
    Path temp;

    @Test
    void emptyRepoGetsExplicitUnknown() throws Exception {
        Path ws = temp.resolve("empty");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);

        assertTrue(Files.isDirectory(ws.resolve(".ai4se")));
        assertTrue(Files.isDirectory(ws.resolve(".story")));
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/repository/facts.md")));
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/repository/module-map.md")));
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/repository/onboard-report.md")));
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/repository/verification-capabilities.yaml")));
        String entries = read(ws.resolve(".ai4se/repository/entries.yaml"));
        assertTrue(entries.contains("build: unknown"), entries);
        assertTrue(entries.contains("test: unknown"), entries);
        WorkspaceSlotVerifier.requireValid(ws);
    }

    @Test
    void mavenRepoGetsMvnCommands() throws Exception {
        Path ws = temp.resolve("maven");
        Files.createDirectories(ws);
        Files.write(ws.resolve("pom.xml"), "<project/>\n".getBytes(StandardCharsets.UTF_8));
        OnboardRepoScript.run(ws);

        String entries = read(ws.resolve(".ai4se/repository/entries.yaml"));
        assertTrue(entries.contains("mvn"), entries);
        assertTrue(!entries.contains("build: unknown"), entries);
        assertTrue(entries.contains("test: unknown"), entries);
        String capabilities = read(ws.resolve(".ai4se/repository/verification-capabilities.yaml"));
        assertTrue(capabilities.contains("repository_wide_test_entry: not_configured"), capabilities);
        WorkspaceSlotVerifier.requireValid(ws);
    }

    @Test
    void factsMapCitesObservedSourceAndKeepsUnknownsVisible() throws Exception {
        Path ws = temp.resolve("facts-map");
        Files.createDirectories(ws.resolve("module/src/main/java"));
        Files.write(ws.resolve("pom.xml"),
                ("<project>\n<modules>\n<module>module</module>\n</modules>\n</project>\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("module/src/main/java/ExampleController.java"),
                "class ExampleController {}\n".getBytes(StandardCharsets.UTF_8));

        OnboardRepoScript.run(ws);

        String facts = read(ws.resolve(".ai4se/repository/facts.md"));
        String map = read(ws.resolve(".ai4se/repository/module-map.md"));
        String report = read(ws.resolve(".ai4se/repository/onboard-report.md"));
        assertTrue(facts.contains("pom.xml"), facts);
        assertTrue(facts.contains("Unknowns requiring confirmation"), facts);
        assertTrue(map.contains("module"), map);
        assertTrue(map.contains("src/main/java"), map);
        assertTrue(report.contains("FACTS_CAPTURED_NOT_EXECUTION_VERIFIED"), report);
    }

    @Test
    void factsMapRecordsGitMetadataForARepository() throws Exception {
        Path ws = temp.resolve("git-repository");
        Files.createDirectories(ws);
        Process git = new ProcessBuilder("git", "init", ws.toString()).start();
        assertEquals(0, git.waitFor());

        OnboardRepoScript.run(ws);

        String facts = read(ws.resolve(".ai4se/repository/facts.md"));
        assertTrue(facts.contains("git metadata"), facts);
    }

    @Test
    void npmRepoGetsNpmCommands() throws Exception {
        Path ws = temp.resolve("npm");
        Files.createDirectories(ws);
        Files.write(ws.resolve("package.json"), "{}\n".getBytes(StandardCharsets.UTF_8));
        OnboardRepoScript.run(ws);

        String entries = read(ws.resolve(".ai4se/repository/entries.yaml"));
        assertTrue(entries.contains("build: unknown"), entries);
        assertTrue(entries.contains("test: unknown"), entries);
        WorkspaceSlotVerifier.requireValid(ws);
    }

    @Test
    void gradleRepoGetsGradleCommands() throws Exception {
        Path ws = temp.resolve("gradle");
        Files.createDirectories(ws);
        Files.write(ws.resolve("build.gradle"), "\n".getBytes(StandardCharsets.UTF_8));
        OnboardRepoScript.run(ws);

        String entries = read(ws.resolve(".ai4se/repository/entries.yaml"));
        assertTrue(entries.contains("gradlew"), entries);
        WorkspaceSlotVerifier.requireValid(ws);
    }

    @Test
    void idempotentDoesNotOverwriteEntries() throws Exception {
        Path ws = temp.resolve("idem");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path entriesPath = ws.resolve(".ai4se/repository/entries.yaml");
        Files.write(entriesPath, "build: unknown\ntest: unknown\n# custom\n".getBytes(StandardCharsets.UTF_8));
        OnboardRepoScript.run(ws);
        assertEquals(
                "build: unknown\ntest: unknown\n# custom\n",
                read(entriesPath));
    }

    @Test
    void refreshesOnlyKnownV1GeneratedEntriesInsteadOfKeepingGuessedTestMatrix() throws Exception {
        Path ws = temp.resolve("v1-entries");
        Files.createDirectories(ws);
        Files.write(ws.resolve("pom.xml"), "<project/>\n".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.write(ws.resolve(".ai4se/repository/entries.yaml"), ("# Auto-detected build/test entry pointers (edit as needed)\n"
                + "test:\n  - mvn -q test\n").getBytes(StandardCharsets.UTF_8));

        OnboardRepoScript.run(ws);

        String entries = read(ws.resolve(".ai4se/repository/entries.yaml"));
        assertTrue(entries.contains("test: unknown"), entries);
        assertTrue(!entries.contains("mvn -q test"), entries);
    }

    @Test
    void baselineForbidsRecommendationLanguageInTemplate() throws Exception {
        Path ws = temp.resolve("baseline");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        String baseline = read(ws.resolve(".ai4se/repository/baseline.md"));
        assertTrue(baseline.contains("no recommendations") || baseline.contains("facts only")
                || baseline.contains("Facts"), baseline);
        assertTrue(!baseline.contains("建议改"));
        assertTrue(!baseline.contains("应实现"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
