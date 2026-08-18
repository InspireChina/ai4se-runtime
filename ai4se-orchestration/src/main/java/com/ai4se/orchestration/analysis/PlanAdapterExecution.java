package com.ai4se.orchestration.analysis;

import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.packagebuild.PlanningPackageBuilder;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.cursor.PackageAdapterSubmission;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planning 挂机最小切片：Planning Package → Adapter 一次 → 必须留下 formal plan.md（含 Allowed Files）。
 * Approval 仍可由人闸；Adapter 不得跳阶段 / Retry。
 */
public final class PlanAdapterExecution {

    public static final String AUDIT_DIR = "execution";
    public static final String AUDIT_FILE = "adapter-planning.md";

    private PlanAdapterExecution() {
    }

    public static AdapterResult submitPlanPackage(
            Path workspace,
            String storyId,
            ModelCliAdapter adapter,
            Duration timeout,
            List<String> allowedHint) throws IOException {
        return submitPlanPackage(workspace, storyId, adapter, timeout, allowedHint, null);
    }

    public static AdapterResult submitPlanPackage(
            Path workspace,
            String storyId,
            ModelCliAdapter adapter,
            Duration timeout,
            List<String> allowedHint,
            com.ai4se.execution.model.RoleModelConfig roleModels) throws IOException {
        if (adapter == null) {
            throw new StageGateException("Plan Adapter required");
        }
        DiscoveryRecords.requireReportOrSkip(workspace, storyId);
        GapRecords.requireNotBlocked(workspace, storyId);

        List<String> hint = allowedHint == null ? new ArrayList<String>() : allowedHint;
        ContextPackageResult pkg = PlanningPackageBuilder.build(
                workspace,
                storyId,
                hint,
                com.ai4se.context.packagebuild.PackageBudget.PRODUCTION_P1);

        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("AI4SE_STORY_ID", storyId);
        env.put("AI4SE_ROLE", PlanningPackageBuilder.ROLE);

        AdapterResult result = PackageAdapterSubmission.submit(
                adapter, workspace, pkg, timeout == null ? Duration.ofMinutes(10) : timeout, env, roleModels);

        writeAudit(workspace, storyId, adapter.name(), pkg.packageDir(), result);

        if (result.hasNextStageHint()) {
            throw new StageGateException(
                    "Adapter must not decide next stage/retry — got control hints in details");
        }
        if (!result.success()) {
            throw new StageGateException(
                    "Plan Adapter failed (no Adapter retry; Control owns recovery): "
                            + (Strings.isBlank(result.message())
                            ? ("exit=" + result.exitCode())
                            : result.message()));
        }
        PlanRecords.requireFormalPlanWithAllowed(workspace, storyId);
        return result;
    }

    private static void writeAudit(
            Path workspace,
            String storyId,
            String adapterName,
            Path packageDir,
            AdapterResult result) throws IOException {
        Path dir = workspace.resolve(".story").resolve(storyId).resolve(AUDIT_DIR);
        Files.createDirectories(dir);
        Path path = dir.resolve(AUDIT_FILE);
        String body = ""
                + "# Adapter Planning submission\n\n"
                + "- adapter: " + adapterName + "\n"
                + "- model: " + result.details().get("model") + "\n"
                + "- role: " + PlanningPackageBuilder.ROLE + "\n"
                + "- package: " + packageDir + "\n"
                + "- success: " + result.success() + "\n"
                + "- exit_code: " + result.exitCode() + "\n"
                + "- message: " + result.message() + "\n"
                + "- control_hints: " + result.hasNextStageHint() + "\n"
                + "- submitted_once: true\n"
                + "- requires_formal_plan_with_allowed: true\n";
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
    }
}
