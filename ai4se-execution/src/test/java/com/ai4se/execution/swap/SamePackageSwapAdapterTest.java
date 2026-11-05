package com.ai4se.execution.swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.cursor.PackageAdapterSubmission;
import com.ai4se.execution.support.ScriptedProcessInvoker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Appendix A · 同 Package 换 Adapter：Cursor 与 Claude 输入契约相同（同一 prompt 语义）。
 */
final class SamePackageSwapAdapterTest {

    @TempDir
    Path temp;

    @Test
    void sameAnalysisPackageYieldsSamePromptBodyForCursorAndClaude() throws Exception {
        Path ws = temp.resolve("cust");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve("seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac-1\n")
                .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "swap-1", seed);
        ContextPackageResult pkg = AnalysisPackageBuilder.build(ws, "swap-1");

        ScriptedProcessInvoker cursorInvoker = new ScriptedProcessInvoker(0, "cursor-ok", "", false);
        ScriptedProcessInvoker claudeInvoker = new ScriptedProcessInvoker(0, "claude-ok", "", false);

        AdapterResult cursor = PackageAdapterSubmission.submit(
                new CursorCliAdapter(cursorInvoker, "agent"), ws, pkg, Duration.ofSeconds(10));
        AdapterResult claude = PackageAdapterSubmission.submit(
                new ClaudeCliAdapter(claudeInvoker, "claude"), ws, pkg, Duration.ofSeconds(10));

        assertTrue(cursor.success());
        assertTrue(claude.success());
        assertEquals("cursor-cli", cursor.details().get("adapter"));
        assertEquals("claude-cli", claude.details().get("adapter"));

        String cursorPrompt = lastArg(cursorInvoker);
        String claudePrompt = lastArg(claudeInvoker);
        assertEquals(cursorPrompt, claudePrompt, "Package prompt contract must be adapter-invariant");
        assertTrue(cursorPrompt.contains("role=Analysis"));
        assertTrue(cursorPrompt.contains("Do NOT decide workflow stages"));
        assertTrue(cursorPrompt.contains("manifest.md"));
        assertTrue(cursorPrompt.contains("ac-1") || cursorPrompt.contains("acceptance"));
    }

    private static String lastArg(ScriptedProcessInvoker invoker) {
        java.util.List<String> argv = invoker.argvHistory().get(0);
        return argv.get(argv.size() - 1);
    }
}
