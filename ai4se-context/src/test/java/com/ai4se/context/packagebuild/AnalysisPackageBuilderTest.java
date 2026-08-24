package com.ai4se.context.packagebuild;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.story.StoryOpener;
import com.ai4se.context.story.StoryRequirement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AnalysisPackageBuilderTest {

    @TempDir
    Path temp;

    @Test
    void refusesWhenAcceptanceEmpty() throws Exception {
        Path ws = onboardedWorkspace();
        StoryOpener.open(ws, "s-empty", null);
        writeRequirement(ws, "s-empty", ""
                + "## raw\n\ndo something\n\n"
                + "## goal\ngoal\n\n"
                + "## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n"
                + "## acceptance\n\n");

        PackageRefuseException ex = assertThrows(
                PackageRefuseException.class,
                () -> AnalysisPackageBuilder.build(ws, "s-empty"));
        assertTrue(ex.getMessage().contains("Acceptance"));
        assertFalse(Files.exists(ws.resolve(".story/s-empty/packages/analysis/manifest.md")));
    }

    @Test
    void refusesWhenAcceptanceIsPlaceholder() throws Exception {
        Path ws = onboardedWorkspace();
        writeRequirement(ws, "s-placeholder", ""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- i\n\n## out_of_scope\n- o\n\n"
                + "## acceptance\n- 看着办\n");

        assertThrows(PackageRefuseException.class, () -> AnalysisPackageBuilder.build(ws, "s-placeholder"));
    }

    @Test
    void buildsAnalysisPackageWhenAcceptancePresent() throws Exception {
        Path ws = onboardedWorkspace();
        writeRequirement(ws, "s-ok", ""
                + "## raw\nAdd health endpoint\n\n"
                + "## goal\nExpose /health returning 200\n\n"
                + "## in_scope\n- HTTP GET /health\n\n"
                + "## out_of_scope\n- auth\n\n"
                + "## acceptance\n"
                + "- GET /health returns 200\n"
                + "- response body contains status UP\n");

        ContextPackageResult result = AnalysisPackageBuilder.build(ws, "s-ok");
        assertTrue(Files.isRegularFile(result.manifestPath()));
        assertTrue(Files.isRegularFile(result.packageDir().resolve("slices/acceptance.md")));
        String manifest = new String(Files.readAllBytes(result.manifestPath()), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("role: Analysis"));
        assertTrue(manifest.contains("slices/acceptance.md"));
    }

    @Test
    void analysisAcceptanceSliceKeepsWrappedFieldNameConstraints() throws Exception {
        Path ws = onboardedWorkspace();
        writeRequirement(ws, "s-wrap", ""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## Acceptance\n"
                + "1. Given JSON keys that exactly\n"
                + "   match both field names (including static field names such as `STATIC_VALUE`):\n"
                + "3. Add a regression test. The regression JSON keys\n"
                + "   MUST be identical to the Java field names under test (no camelCase rewrite\n"
                + "   of `STATIC_VALUE` → `staticValue`).\n");
        ContextPackageResult result = AnalysisPackageBuilder.build(ws, "s-wrap");
        String acc = new String(
                Files.readAllBytes(result.packageDir().resolve("slices/acceptance.md")),
                StandardCharsets.UTF_8);
        assertTrue(acc.contains("exactly match both field names"), acc);
        assertTrue(acc.contains("STATIC_VALUE"), acc);
        assertTrue(acc.contains("staticValue"), acc);
    }

    @Test
    void analysisIncludesVerifiedRepositoryEntryCommandsInPriorityOne() throws Exception {
        Path ws = onboardedWorkspace();
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n- mvn -q -DskipTests package\n"
                        + "test:\n- mvn -pl app -am -Dtest=SmokeTest test\n")
                        .getBytes(StandardCharsets.UTF_8));
        writeRequirement(ws, "s-entries", ""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- a response is returned\n");

        ContextPackageResult result = AnalysisPackageBuilder.build(ws, "s-entries");
        Path entry = result.packageDir().resolve("slices/verification-entry.yaml");
        assertTrue(Files.isRegularFile(entry));
        String text = new String(Files.readAllBytes(entry), StandardCharsets.UTF_8);
        assertTrue(text.contains("SmokeTest"), text);
        String manifest = new String(Files.readAllBytes(result.manifestPath()), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("slices/verification-entry.yaml"), manifest);
    }

    @Test
    void analysisIncludesSmallOnboardFactMapInPriorityOneWhenAvailable() throws Exception {
        Path ws = onboardedWorkspace();
        Files.write(ws.resolve(".ai4se/repository/facts.md"),
                "# Repository Facts\n\n- source: pom.xml\n- unknown: domain owner\n"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/module-map.md"),
                "# Module Map\n\n- app/src/main/java\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/baseline.md"),
                "# Baseline\n\n- test: not yet executed\n".getBytes(StandardCharsets.UTF_8));
        writeRequirement(ws, "s-facts", ""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- response is returned\n");

        ContextPackageResult result = AnalysisPackageBuilder.build(ws, "s-facts");

        String input = new String(Files.readAllBytes(
                result.packageDir().resolve("model-input.md")), StandardCharsets.UTF_8);
        assertTrue(input.contains("repository-facts.md"), input);
        assertTrue(input.contains("domain owner"), input);
        assertTrue(input.contains("module-map.md"), input);
        assertTrue(input.contains("test: not yet executed"), input);
    }

    @Test
    void analysisIncludesOperatorWriteScopeCeilingInPriorityOne() throws Exception {
        Path ws = onboardedWorkspace();
        writeRequirement(ws, "s-scope", ""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- response is returned\n");

        ContextPackageResult result = AnalysisPackageBuilder.build(
                ws,
                "s-scope",
                Arrays.asList("api/src/main/java", "api/src/test/java"),
                PackageBudget.UNLIMITED);

        String input = new String(Files.readAllBytes(
                result.packageDir().resolve("model-input.md")), StandardCharsets.UTF_8);
        assertTrue(input.contains("api/src/main/java"), input);
        assertTrue(input.contains("operator-provided ceiling"), input);
    }

    @Test
    void analysisCarriesPreFreezeBusinessDecisionsAsPriorityOne() throws Exception {
        Path ws = onboardedWorkspace();
        writeRequirement(ws, "s-decisions", ""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- response is returned\n");
        Path resolved = ws.resolve(".story/s-decisions/specification/clarification.resolved.md");
        Files.createDirectories(resolved.getParent());
        Files.write(resolved, "# Resolved\n\n## Answer\n\nQ1=A\n".getBytes(StandardCharsets.UTF_8));

        ContextPackageResult result = AnalysisPackageBuilder.build(ws, "s-decisions");

        String input = new String(Files.readAllBytes(
                result.packageDir().resolve("model-input.md")), StandardCharsets.UTF_8);
        assertTrue(input.contains("slices/specification-decisions.md"), input);
        assertTrue(input.contains("Q1=A"), input);
    }

    @Test
    void acceptanceGateDetectsPlaceholders() {
        StoryRequirement bad = new StoryRequirement(
                "x", "r", "g", "i", "o", Collections.singletonList("看着办"));
        assertFalse(AcceptanceGate.validate(bad).isEmpty());

        StoryRequirement ok = new StoryRequirement(
                "x", "r", "g", "i", "o", Arrays.asList("returns 200", "logged"));
        assertTrue(AcceptanceGate.validate(ok).isEmpty());
    }

    private Path onboardedWorkspace() throws Exception {
        Files.createDirectories(temp.resolve(".ai4se/repository"));
        Files.createDirectories(temp.resolve(".story"));
        return temp;
    }

    private void writeRequirement(Path ws, String storyId, String body) throws Exception {
        Path dir = ws.resolve(".story").resolve(storyId);
        Files.createDirectories(dir.resolve("packages"));
        Files.write(dir.resolve("requirement.md"), ("# Story\n\n" + body).getBytes(StandardCharsets.UTF_8));
    }
}
