package com.ai4se.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.cursor.PackageAdapterSubmission;
import com.ai4se.execution.support.ScriptedProcessInvoker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W4：Adapter 成功/失败都不得擅自推进 03 状态机。 */
final class AdapterDoesNotAdvanceWorkflowTest {

    @TempDir
    Path temp;

    @Test
    void adapterSuccessLeavesStageAtAnalysis() throws Exception {
        Path ws = prepare("iso-ok");
        ContextPackageResult pkg = AnalysisPackageBuilder.build(ws, "iso-ok");
        StoryWorkflowMachine.start(ws, "iso-ok");

        CursorCliAdapter adapter = new CursorCliAdapter(
                new ScriptedProcessInvoker(0, "ok", "", false), "agent");
        AdapterResult result = PackageAdapterSubmission.submit(adapter, ws, pkg, Duration.ofSeconds(5));
        assertEquals(true, result.success());
        assertEquals(WorkflowStage.ANALYSIS, StoryWorkflowMachine.load(ws, "iso-ok").stage());
        assertFalse(result.hasNextStageHint());
    }

    @Test
    void adapterFailureLeavesStageAtAnalysis() throws Exception {
        Path ws = prepare("iso-fail");
        ContextPackageResult pkg = AnalysisPackageBuilder.build(ws, "iso-fail");
        StoryWorkflowMachine.start(ws, "iso-fail");

        CursorCliAdapter adapter = new CursorCliAdapter(
                new ScriptedProcessInvoker(2, "", "nope", false), "agent");
        AdapterResult result = PackageAdapterSubmission.submit(adapter, ws, pkg, Duration.ofSeconds(5));
        assertEquals(false, result.success());
        assertEquals(WorkflowStage.ANALYSIS, StoryWorkflowMachine.load(ws, "iso-fail").stage());
    }

    private Path prepare(String id) throws Exception {
        Path ws = temp.resolve(id);
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve(id + ".md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- ac\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, id, seed);
        return ws;
    }
}
