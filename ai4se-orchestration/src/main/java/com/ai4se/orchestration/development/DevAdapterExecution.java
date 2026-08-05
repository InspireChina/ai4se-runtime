package com.ai4se.orchestration.development;

import com.ai4se.context.packagebuild.ContextPackageResult;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * W4-on-spine for Development: build/use Dev Package → submit Adapter once → Control handles FAIL.
 * Adapter must not hint next stage / retry.
 */
public final class DevAdapterExecution {

    public static final String AUDIT_DIR = "execution";

    private DevAdapterExecution() {
    }

    public static AdapterResult submitDevPackage(
            Path workspace,
            String storyId,
            int developmentRound,
            ModelCliAdapter adapter,
            Duration timeout) throws IOException {
        if (adapter == null) {
            throw new StageGateException("Dev Adapter required");
        }
        if (!DevPackageBuilder.hasPackage(workspace, storyId)) {
            DevPackageBuilder.build(workspace, storyId);
        }
        Path manifest = DevPackageBuilder.latestManifest(workspace, storyId);
        if (manifest == null || !Files.isRegularFile(manifest)) {
            throw new StageGateException("Dev Package missing before Adapter submit");
        }
        Path packageDir = manifest.getParent();
        ContextPackageResult pkg = new ContextPackageResult(
                DevPackageBuilder.ROLE,
                storyId,
                packageDir,
                manifest,
                priority1FromManifest(manifest));

        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("AI4SE_DEV_ROUND", String.valueOf(developmentRound));
        env.put("AI4SE_STORY_ID", storyId);
        env.put("AI4SE_ROLE", DevPackageBuilder.ROLE);

        AdapterResult result = PackageAdapterSubmission.submit(
                adapter, workspace, pkg, timeout == null ? Duration.ofMinutes(10) : timeout, env);

        writeAudit(workspace, storyId, developmentRound, adapter.name(), packageDir, result);

        if (result.hasNextStageHint()) {
            throw new StageGateException(
                    "Adapter must not decide next stage/retry — got control hints in details");
        }
        if (!result.success()) {
            throw new StageGateException(
                    "Dev Adapter failed (no Adapter retry; Control owns recovery): "
                            + (Strings.isBlank(result.message())
                            ? ("exit=" + result.exitCode())
                            : result.message()));
        }
        return result;
    }

    private static void writeAudit(
            Path workspace,
            String storyId,
            int round,
            String adapterName,
            Path packageDir,
            AdapterResult result) throws IOException {
        Path dir = workspace.resolve(".story").resolve(storyId).resolve(AUDIT_DIR);
        Files.createDirectories(dir);
        Path path = dir.resolve("adapter-dev-round-" + round + ".md");
        String body = ""
                + "# Adapter Dev submission\n\n"
                + "- round: " + round + "\n"
                + "- adapter: " + adapterName + "\n"
                + "- role: " + DevPackageBuilder.ROLE + "\n"
                + "- package: " + packageDir + "\n"
                + "- success: " + result.success() + "\n"
                + "- exit_code: " + result.exitCode() + "\n"
                + "- message: " + result.message() + "\n"
                + "- control_hints: " + result.hasNextStageHint() + "\n"
                + "- submitted_once: true\n";
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> priority1FromManifest(Path manifest) throws IOException {
        String text = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
        List<String> out = new ArrayList<String>();
        boolean inP1 = false;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.startsWith("## ")) {
                inP1 = t.toLowerCase(Locale.ROOT).contains("priority1")
                        || t.toLowerCase(Locale.ROOT).contains("priority 1");
                continue;
            }
            if (inP1 && t.startsWith("- ")) {
                out.add(t.substring(2).trim());
            } else if (inP1 && t.startsWith("#")) {
                break;
            }
        }
        if (out.isEmpty()) {
            out.add("slices/allowed-files.md");
            out.add("slices/diff-ref.md");
        }
        return Collections.unmodifiableList(out);
    }
}
