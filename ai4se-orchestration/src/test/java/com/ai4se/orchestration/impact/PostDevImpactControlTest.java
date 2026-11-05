package com.ai4se.orchestration.impact;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.codegraph.CodeGraphControl;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.development.DevelopmentRecords;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PostDevImpactControlTest {

    @TempDir
    Path temp;

    @Test
    void postDevRefreshWritesImpactReportWithinAllowedScope() throws Exception {
        Path ws = temp.resolve("cust");
        Files.createDirectories(ws.resolve("src/main/java/com/demo"));
        Files.write(ws.resolve("src/main/java/com/demo/A.java"),
                "package com.demo;\npublic class A { public void x() {} }\n"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/main/java/com/demo/B.java"),
                ("package com.demo;\npublic class B {\n"
                        + "  private A a;\n"
                        + "  public void y() { a.x(); }\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8));
        CodeGraphControl.rebuildFull(ws);

        String storyId = "story-post-dev";
        DiscoveryRecords.writeSkip(ws, storyId, "fixture", "tester");
        GapRecords.write(ws, storyId, GapStatus.CLEAR, 0, "ok");
        Path planning = PlanRecords.planningDir(ws, storyId);
        Files.createDirectories(planning);
        Files.write(planning.resolve(PlanRecords.PLAN_FILE), (""
                + "# Plan\n\n## Design\n\nok\n\n## Allowed Files\n\n"
                + "- src/main/java/com/demo/A.java\n"
                + "- src/main/java/com/demo/B.java\n\n"
                + "## Change Map\n\n- touch A\n\n## Test Strategy\n\n- ENTRY_TEST\n\n"
                + "## Impact Assessment\n\n"
                + "- api: NOT_APPLICABLE\n- data: NOT_APPLICABLE\n- authorization: NOT_APPLICABLE\n"
                + "- ui: NOT_APPLICABLE\n- observability: NOT_APPLICABLE\n")
                .getBytes(StandardCharsets.UTF_8));
        PlanRecords.requireExecutionArtifacts(ws, storyId);

        Files.write(ws.resolve("src/main/java/com/demo/A.java"),
                "package com.demo;\npublic class A { public void x() { int y = 1; } }\n"
                        .getBytes(StandardCharsets.UTF_8));
        DevelopmentRecords.recordDeclaredChanges(
                ws, storyId, Arrays.asList("src/main/java/com/demo/A.java"), "update A");

        PostDevImpactControl.Result result = PostDevImpactControl.inspect(ws, storyId);
        assertFalse(result.stopped);
        Path report = ws.resolve(".story/" + storyId + "/planning/impact/post-dev-impact.md");
        assertTrue(Files.isRegularFile(report), "missing " + report);
        String text = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        assertTrue(text.contains("Actual diff files"), text);
        assertTrue(text.contains("src/main/java/com/demo/A.java"), text);
    }
}
