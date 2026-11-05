package com.ai4se.context.codegraph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ImpactSubgraphPackageTest {

    @TempDir
    Path temp;

    @Test
    void analysisPackageIncludesImpactSubgraphSlice() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws.resolve("src/main/java/com/demo"));
        Files.write(ws.resolve("pom.xml"), "<project/>\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/main/java/com/demo/OrderService.java"),
                ("package com.demo;\n"
                        + "public class OrderService {\n"
                        + "  public int total(int a, int b) { return a + b; }\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8));
        OnboardRepoScript.run(ws);
        assertTrue(Files.isRegularFile(CodeGraphPaths.manifest(ws)));
        assertTrue(Files.isRegularFile(CodeGraphPaths.sourceInventory(ws)));

        String storyId = "story-impact-pkg";
        Path dir = ws.resolve(".story").resolve(storyId);
        Files.createDirectories(dir);
        Files.write(dir.resolve("requirement.md"), (""
                + "# Story\n\n"
                + "## Goal\n\nOrder total projection\n\n"
                + "## In Scope\n\nOrderService\n\n"
                + "## Out of Scope\n\nnone\n\n"
                + "## Acceptance Criteria\n\n"
                + "- OrderService total returns sum\n")
                .getBytes(StandardCharsets.UTF_8));

        ContextPackageResult pkg = AnalysisPackageBuilder.build(ws, storyId);
        assertTrue(Files.isRegularFile(pkg.packageDir().resolve("slices/impact-subgraph.md")));
        assertTrue(Files.isRegularFile(pkg.packageDir().resolve("slices/code-graph-refresh.properties")));
        String refresh = new String(Files.readAllBytes(
                pkg.packageDir().resolve("slices/code-graph-refresh.properties")), StandardCharsets.UTF_8);
        assertTrue(refresh.contains("mode="), refresh);
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/" + storyId + "/planning/impact/direct-references.md")));
    }
}
