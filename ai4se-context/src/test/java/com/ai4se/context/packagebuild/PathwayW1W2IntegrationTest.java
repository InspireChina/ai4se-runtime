package com.ai4se.context.packagebuild;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.context.workspace.WorkspaceSlotVerifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W1→W2 组合：onboard → open story → build / refuse。
 * 覆盖手册组合验证「建槽→开 Story→装包」，不依赖人手复跑 CLI。
 */
final class PathwayW1W2IntegrationTest {

    @TempDir
    Path temp;

    @Test
    void onboardOpenBuildHappyPath() throws Exception {
        Path ws = temp.resolve("happy");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        WorkspaceSlotVerifier.requireValid(ws);

        Path seed = temp.resolve("ok.md");
        Files.write(seed, (""
                + "# Story\n\n## raw\nhealth\n\n## goal\nhealth endpoint\n\n"
                + "## in_scope\n- GET /health\n\n## out_of_scope\n- auth\n\n"
                + "## acceptance\n- GET /health returns 200\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "health-1", seed);

        ContextPackageResult pkg = AnalysisPackageBuilder.build(ws, "health-1");
        assertTrue(Files.isRegularFile(pkg.manifestPath()));
        String manifest = new String(Files.readAllBytes(pkg.manifestPath()), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("role: Analysis"));
        assertTrue(manifest.contains("story_id: health-1"));
        assertTrue(manifest.contains("forbidden_filtered"));
        String acceptanceSlice = new String(
                Files.readAllBytes(pkg.packageDir().resolve("slices/acceptance.md")),
                StandardCharsets.UTF_8);
        assertTrue(acceptanceSlice.contains("GET /health returns 200"));
    }

    @Test
    void refuseDoesNotLeaveAnalysisManifest() throws Exception {
        Path ws = temp.resolve("refuse");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve("bad.md");
        Files.write(seed, (""
                + "## raw\nx\n\n## goal\ny\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- 看着办\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "bad-1", seed);

        assertThrows(PackageRefuseException.class, () -> AnalysisPackageBuilder.build(ws, "bad-1"));
        assertFalse(Files.exists(ws.resolve(".story/bad-1/packages/analysis/manifest.md")));
    }

    @Test
    void templateSeedAloneIsNotEnoughToBuild() throws Exception {
        Path ws = temp.resolve("template");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        StoryOpener.open(ws, "tmpl-1", null);
        assertThrows(PackageRefuseException.class, () -> AnalysisPackageBuilder.build(ws, "tmpl-1"));
    }

    @Test
    void cannotBuildWithoutStory() throws Exception {
        Path ws = temp.resolve("nostory");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        assertThrows(Exception.class, () -> AnalysisPackageBuilder.build(ws, "no-such"));
    }

    @Test
    void packageStaysUnderStoryId() throws Exception {
        Path ws = temp.resolve("isolate");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve("s.md");
        Files.write(seed, (""
                + "## raw\na\n\n## goal\nb\n\n## in_scope\n- c\n\n## out_of_scope\n- d\n\n"
                + "## acceptance\n- concrete check\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "alpha", seed);
        StoryOpener.open(ws, "beta", seed);
        AnalysisPackageBuilder.build(ws, "alpha");
        assertTrue(Files.isRegularFile(ws.resolve(".story/alpha/packages/analysis/manifest.md")));
        assertFalse(Files.exists(ws.resolve(".story/beta/packages/analysis/manifest.md")));
    }
}
