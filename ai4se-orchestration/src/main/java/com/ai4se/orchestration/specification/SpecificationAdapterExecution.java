package com.ai4se.orchestration.specification;

import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.context.packagebuild.SpecificationPackageBuilder;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.cursor.PackageAdapterSubmission;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Executes exactly one controlled specification turn, before the production workflow exists. */
public final class SpecificationAdapterExecution {

    private SpecificationAdapterExecution() {
    }

    public static SpecificationRecords.Outcome submit(
            Path workspace, String storyId, ModelCliAdapter adapter, Duration timeout)
            throws IOException {
        if (adapter == null) {
            throw new StageGateException("Specification adapter required");
        }
        ContextPackageResult pkg = SpecificationPackageBuilder.build(
                workspace, storyId, PackageBudget.PRODUCTION_P1);
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("AI4SE_STORY_ID", storyId);
        env.put("AI4SE_ROLE", SpecificationPackageBuilder.ROLE);
        AdapterResult result = PackageAdapterSubmission.submit(
                adapter, workspace, pkg, timeout == null ? Duration.ofMinutes(10) : timeout, env, null);
        writeAudit(workspace, storyId, adapter.name(), pkg.packageDir(), result);
        if (result.hasNextStageHint()) {
            throw new StageGateException("Specification adapter must not decide workflow stage/retry");
        }
        if (!result.success()) {
            throw new StageGateException("Specification adapter failed: "
                    + (Strings.isBlank(result.message()) ? "exit=" + result.exitCode() : result.message()));
        }
        return SpecificationRecords.requireOutcome(workspace, storyId);
    }

    private static void writeAudit(
            Path workspace, String storyId, String adapter, Path pkg, AdapterResult result) throws IOException {
        Path dir = workspace.resolve(".story").resolve(storyId).resolve("execution");
        Files.createDirectories(dir);
        String body = "# Adapter Specification submission\n\n"
                + "- adapter: " + adapter + "\n"
                + "- model: " + result.details().get("model") + "\n"
                + "- role: Specification\n"
                + "- package: " + pkg + "\n"
                + "- success: " + result.success() + "\n"
                + "- exit_code: " + result.exitCode() + "\n"
                + "- submitted_once: true\n"
                + "- requires_result_contract: true\n";
        Files.write(dir.resolve("adapter-specification.md"), body.getBytes(StandardCharsets.UTF_8));
    }
}
