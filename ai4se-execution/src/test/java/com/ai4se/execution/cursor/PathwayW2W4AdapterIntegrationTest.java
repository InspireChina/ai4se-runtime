package com.ai4se.execution.cursor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.ScriptedProcessInvoker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W2→W4：合法 Package 可交 Adapter；失败原样；不在 Adapter 内 Retry。 */
final class PathwayW2W4AdapterIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void legalPackageSubmittedOnceOnSuccess() throws Exception {
        Path ws = prepareStory("ok-1");
        ContextPackageResult pkg = AnalysisPackageBuilder.build(ws, "ok-1");

        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(0, "SPEC draft", "", false);
        CursorCliAdapter adapter = new CursorCliAdapter(invoker, "agent");
        AdapterResult result = PackageAdapterSubmission.submit(adapter, ws, pkg, Duration.ofSeconds(10));

        assertTrue(result.success());
        assertEquals(1, invoker.argvHistory().size());
        assertFalse(result.hasNextStageHint());
    }

    @Test
    void failureReturnedAsIsWithoutRetry() throws Exception {
        Path ws = prepareStory("fail-1");
        ContextPackageResult pkg = AnalysisPackageBuilder.build(ws, "fail-1");

        ScriptedProcessInvoker invoker = new ScriptedProcessInvoker(3, "out", "adapter boom", false);
        CursorCliAdapter adapter = new CursorCliAdapter(invoker, "agent");
        AdapterResult result = PackageAdapterSubmission.submit(adapter, ws, pkg, Duration.ofSeconds(10));

        assertFalse(result.success());
        assertEquals(3, result.exitCode());
        assertTrue(result.message().contains("adapter boom"));
        assertEquals(1, invoker.argvHistory().size());
    }

    @Test
    void stubScriptActsAsCursorBinary() throws Exception {
        Path ws = prepareStory("stub-1");
        ContextPackageResult pkg = AnalysisPackageBuilder.build(ws, "stub-1");
        Path stub = temp.resolve("fake-agent");
        Files.write(stub, (""
                + "#!/usr/bin/env bash\n"
                + "echo \"stub-agent argv: $*\"\n"
                + "exit 0\n").getBytes(StandardCharsets.UTF_8));
        stub.toFile().setExecutable(true);

        CursorCliAdapter adapter = new CursorCliAdapter(new ProcessInvoker.RealProcessInvoker(), stub.toString());
        AdapterResult result = PackageAdapterSubmission.submit(adapter, ws, pkg, Duration.ofSeconds(10));
        assertTrue(result.success(), result.message());
        assertTrue(result.stdout().contains("stub-agent"));
        assertTrue(result.details().get("argv").contains("-p"));
    }

    private Path prepareStory(String id) throws Exception {
        Path ws = temp.resolve(id + "-ws");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve(id + "-seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n## out_of_scope\n- b\n\n"
                + "## acceptance\n- concrete AC for " + id + "\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, id, seed);
        return ws;
    }
}
