package com.ai4se.context.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.context.packagebuild.PackageRefuseException;
import com.ai4se.context.story.StoryOpener;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Appendix A · 适用 Rule 触顶不丢. */
final class ApplicableRuleBudgetTest {

    @TempDir
    Path temp;

    @Test
    void tinyBudgetWithApplicableRuleRefusesInsteadOfDropping() throws Exception {
        Path ws = ready("story-rule-fail");
        writeRule(ws, "refund-invariant.md", ""
                + "# id: refund-invariant\n"
                + "# roles: Analysis,Development\n"
                + "# applicable: true\n\n"
                + "Must not invent refund tables without Clarification.\n"
                + repeat("X", 2000));

        PackageRefuseException ex = assertThrows(PackageRefuseException.class, () ->
                AnalysisPackageBuilder.build(ws, "story-rule-fail", PackageBudget.ofBytes(500)));
        assertTrue(ex.getMessage().contains("Applicable Rule"));
        assertTrue(ex.getMessage().contains("expand budget") || ex.getMessage().contains("must not drop"));
        // No successful package with rule silently omitted
        assertFalse(Files.isRegularFile(
                ws.resolve(".story/story-rule-fail/packages/analysis/slices/rules/refund-invariant.md")));
    }

    @Test
    void adequateBudgetInstallsApplicableRuleInP1() throws Exception {
        Path ws = ready("story-rule-ok");
        writeRule(ws, "no-push.md", ""
                + "# id: no-push\n"
                + "# roles: Analysis\n"
                + "# applicable: true\n\n"
                + "Commit must not Push.\n");

        ContextPackageResult pkg = AnalysisPackageBuilder.build(
                ws, "story-rule-ok", PackageBudget.ofBytes(50_000));
        assertTrue(pkg.priority1().contains("slices/rules/no-push.md"));
        String manifest = new String(Files.readAllBytes(pkg.manifestPath()), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("rule:no-push"));
        assertTrue(manifest.contains("must not drop") || manifest.contains("applicable Rules"));
        assertTrue(Files.isRegularFile(pkg.packageDir().resolve("slices/rules/no-push.md")));
    }

    @Test
    void nonApplicableRoleRuleNotRequiredInBudget() throws Exception {
        Path ws = ready("story-rule-other");
        writeRule(ws, "dev-only.md", ""
                + "# id: dev-only\n"
                + "# roles: Development\n"
                + "# applicable: true\n\n"
                + repeat("Y", 5000));

        // The production envelope has its own fixed framing cost. A budget adequate for that
        // envelope must still not include an inapplicable Development-only rule.
        ContextPackageResult pkg = AnalysisPackageBuilder.build(
                ws, "story-rule-other", PackageBudget.ofBytes(50_000));
        assertFalse(pkg.priority1().stream().anyMatch(p -> p.contains("dev-only")));
    }

    @Test
    void applicableFalseIsIgnored() throws Exception {
        Path ws = ready("story-rule-off");
        writeRule(ws, "off.md", ""
                + "# id: off\n"
                + "# roles: Analysis\n"
                + "# applicable: false\n\n"
                + repeat("Z", 5000));
        ContextPackageResult pkg = AnalysisPackageBuilder.build(
                ws, "story-rule-off", PackageBudget.ofBytes(50_000));
        assertFalse(pkg.priority1().stream().anyMatch(p -> p.contains("off")));
    }

    private Path ready(String storyId) throws Exception {
        Path ws = temp.resolve(storyId + "-ws");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve(storyId + "-seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac-1\n")
                .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, storyId, seed);
        return ws;
    }

    private static void writeRule(Path ws, String name, String body) throws Exception {
        Path dir = ws.resolve(".ai4se/rules");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), body.getBytes(StandardCharsets.UTF_8));
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(n * s.length());
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
