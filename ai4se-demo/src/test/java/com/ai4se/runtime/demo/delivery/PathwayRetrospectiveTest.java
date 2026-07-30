package com.ai4se.runtime.demo.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Fast water/honesty checks. Full prior-Main re-run is PathwayRetrospectiveMain (integration).
 */
class PathwayRetrospectiveTest {

    @Test
    void handbookWater_minimaHonest_andFixedMeansPresent() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        String handbook = new String(Files.readAllBytes(
                new File(module, "../docs/build-pathway-playbook.md").toPath().normalize()),
                Charset.forName("UTF-8"));

        assertTrue(Pattern.compile("S0 ADR \\| ✅").matcher(handbook).find());
        assertTrue(Pattern.compile("S4 \\| ✅ 最小").matcher(handbook).find());
        assertTrue(Pattern.compile("S5 \\| ✅ 最小").matcher(handbook).find());
        assertTrue(Pattern.compile("S8a \\| ✅ 最小").matcher(handbook).find());
        assertTrue(Pattern.compile("S8b/c \\| ❌").matcher(handbook).find());
        assertTrue(handbook.contains("验证通路固定手段"));
        assertTrue(handbook.contains("REVIEWER") || handbook.contains("Reviewer"));
        assertTrue(Pattern.compile("禁 Graph|禁止.*Claude|Workflow DSL").matcher(handbook).find());
        assertFalse(Pattern.compile("S4 .*✅\\s*完成(?!（|\\s*最小)").matcher(handbook).find());
        assertTrue(handbook.contains("ALIGN") || handbook.contains("蓝图对齐"));
        assertTrue(handbook.contains("HOLD_SURFACE") || handbook.contains("DEVIATE"));
    }
}
