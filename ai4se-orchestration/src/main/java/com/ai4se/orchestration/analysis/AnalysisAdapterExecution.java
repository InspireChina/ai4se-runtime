package com.ai4se.orchestration.analysis;

import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.cursor.PackageAdapterSubmission;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Analysis 挂机最小切片：Analysis Package → Adapter 一次 → 必须留下 discovery.report 或 discovery.skip。
 * Adapter 不得跳阶段 / Retry；Discovery 正文由 Adapter（或人预置）产生，不由 Runner 代写事实。
 */
public final class AnalysisAdapterExecution {

    public static final String AUDIT_DIR = "execution";
    public static final String AUDIT_FILE = "adapter-analysis.md";

    private AnalysisAdapterExecution() {
    }

    public static AdapterResult submitAnalysisPackage(
            Path workspace,
            String storyId,
            ModelCliAdapter adapter,
            Duration timeout) throws IOException {
        if (adapter == null) {
            throw new StageGateException("Analysis Adapter required");
        }
        ContextPackageResult pkg = AnalysisPackageBuilder.build(workspace, storyId);

        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("AI4SE_STORY_ID", storyId);
        env.put("AI4SE_ROLE", AnalysisPackageBuilder.ROLE);

        AdapterResult result = PackageAdapterSubmission.submit(
                adapter, workspace, pkg, timeout == null ? Duration.ofMinutes(10) : timeout, env);

        writeAudit(workspace, storyId, adapter.name(), pkg.packageDir(), result);

        if (result.hasNextStageHint()) {
            throw new StageGateException(
                    "Adapter must not decide next stage/retry — got control hints in details");
        }
        if (!result.success()) {
            throw new StageGateException(
                    "Analysis Adapter failed (no Adapter retry; Control owns recovery): "
                            + (Strings.isBlank(result.message())
                            ? ("exit=" + result.exitCode())
                            : result.message()));
        }
        DiscoveryRecords.requireReportOrSkip(workspace, storyId);
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
                + "# Adapter Analysis submission\n\n"
                + "- adapter: " + adapterName + "\n"
                + "- role: " + AnalysisPackageBuilder.ROLE + "\n"
                + "- package: " + packageDir + "\n"
                + "- success: " + result.success() + "\n"
                + "- exit_code: " + result.exitCode() + "\n"
                + "- message: " + result.message() + "\n"
                + "- control_hints: " + result.hasNextStageHint() + "\n"
                + "- submitted_once: true\n"
                + "- requires_discovery_report_or_skip: true\n";
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
    }
}
