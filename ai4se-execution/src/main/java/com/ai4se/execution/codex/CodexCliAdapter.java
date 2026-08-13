package com.ai4se.execution.codex;

import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.model.RoleModelResolver;
import com.ai4se.execution.support.CliVendor;
import com.ai4se.execution.support.ContextPackagePrompt;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.UnattendedPermissionPolicy;
import com.ai4se.execution.support.UnattendedWriteScope;
import com.ai4se.runtime.common.util.ShellExecutable;
import com.ai4se.runtime.common.util.Strings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlled non-interactive Codex CLI adapter.
 *
 * <p>Codex's {@code --approve-for-me} selects its controlled workspace-write automatic approval
 * mode. The adapter deliberately does not pass {@code --sandbox}; Codex rejects that option when
 * combined with {@code --approve-for-me}.
 */
public final class CodexCliAdapter implements ModelCliAdapter {

    public static final String NAME = "codex-cli";
    public static final String ENV_BIN = "AI4SE_CODEX_BIN";
    public static final String MAC_CODEX_BIN =
            "/Applications/ChatGPT.app/Contents/Resources/codex";

    private final ProcessInvoker invoker;
    private final String binary;
    private final String defaultModel;

    public CodexCliAdapter() {
        this(new ProcessInvoker.RealProcessInvoker(), resolveBinary(null), null);
    }

    public CodexCliAdapter(ProcessInvoker invoker, String binary) {
        this(invoker, binary, null);
    }

    public CodexCliAdapter(ProcessInvoker invoker, String binary, String defaultModel) {
        this.invoker = invoker == null ? new ProcessInvoker.RealProcessInvoker() : invoker;
        this.binary = Strings.isBlank(binary) ? resolveBinary(null) : binary.trim();
        this.defaultModel = Strings.isBlank(defaultModel) ? null : defaultModel.trim();
    }

    public static CodexCliAdapter withModel(String model) {
        return new CodexCliAdapter(new ProcessInvoker.RealProcessInvoker(), resolveBinary(null), model);
    }

    @Override
    public String name() {
        return NAME;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public String resolvedBinary() {
        return binary;
    }

    @Override
    public AdapterResult execute(AdapterRequest request) {
        if (request == null) {
            return AdapterResult.failure(-1, "", "", "AdapterRequest is null", details("error", "null_request"));
        }
        Path packageDir = request.packageDir();
        Path manifest = packageDir.resolve("manifest.md");
        if (!Files.isDirectory(packageDir) || !Files.isRegularFile(manifest)) {
            return AdapterResult.failure(
                    -1, "", "", "Context Package missing or incomplete: " + packageDir,
                    details("error", "package_missing"));
        }

        String model;
        List<String> argv;
        try {
            model = RoleModelResolver.modelFor(request, defaultModel);
            argv = buildArgv(request, manifest, model);
        } catch (IOException e) {
            return AdapterResult.failure(
                    -1, "", "", "Failed to read package: " + e.getMessage(),
                    details("error", "package_io"));
        }

        Map<String, String> env = new LinkedHashMap<String, String>();
        env.putAll(request.env());
        Map<String, String> meta = details(
                "adapter", NAME,
                "binary", binary,
                "role", request.role(),
                "story_id", request.storyId(),
                "package_dir", packageDir.toString());
        meta.put("model", Strings.isBlank(model) ? "(cli-default)" : model);
        meta.put("unattended_write_scope", UnattendedWriteScope.forRole(request.role()).name());
        meta.put("argv", join(argv));
        try {
            ProcessInvoker.ProcessOutcome outcome =
                    invoker.run(argv, request.workspace(), env, request.timeout());
            if (outcome.timedOut) {
                return AdapterResult.failure(
                        outcome.exitCode, outcome.stdout, outcome.stderr, "Codex CLI timed out", meta);
            }
            if (outcome.exitCode != 0) {
                String msg = "Codex CLI exit=" + outcome.exitCode;
                if (!Strings.isBlank(outcome.stderr)) {
                    msg += ": " + trim(outcome.stderr, 500);
                } else if (!Strings.isBlank(outcome.stdout)) {
                    msg += ": " + trim(outcome.stdout, 500);
                }
                return AdapterResult.failure(outcome.exitCode, outcome.stdout, outcome.stderr, msg, meta);
            }
            return AdapterResult.ok(outcome.exitCode, outcome.stdout, outcome.stderr, meta);
        } catch (IOException e) {
            return AdapterResult.failure(-1, "", "", "Codex CLI IO error: " + e.getMessage(), details("error", "io"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AdapterResult.failure(-1, "", "", "Codex CLI interrupted", details("error", "interrupted"));
        }
    }

    List<String> buildArgv(AdapterRequest request, Path manifest) throws IOException {
        return buildArgv(request, manifest, RoleModelResolver.modelFor(request, defaultModel));
    }

    List<String> buildArgv(AdapterRequest request, Path manifest, String model) throws IOException {
        List<String> afterBinary = new ArrayList<String>();
        afterBinary.add("exec");
        UnattendedPermissionPolicy.apply(CliVendor.CODEX, request.role(), afterBinary);
        if (!Strings.isBlank(model)) {
            // Keep the model option before the final prompt, as required by codex exec.
            afterBinary.add("--model");
            afterBinary.add(model.trim());
        }
        afterBinary.add(ContextPackagePrompt.build(request, manifest));
        return new ArrayList<String>(ShellExecutable.launchArgv(binary, afterBinary));
    }

    public static String resolveBinary(String override) {
        if (!Strings.isBlank(override)) {
            return override.trim();
        }
        String env = System.getenv(ENV_BIN);
        if (!Strings.isBlank(env)) {
            return env.trim();
        }
        if (Files.isRegularFile(Paths.get(MAC_CODEX_BIN))) {
            return MAC_CODEX_BIN;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (dir == null || dir.isEmpty()) {
                    continue;
                }
                Path candidate = Paths.get(dir, "codex");
                Path cmd = Paths.get(dir, "codex.cmd");
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
                if (Files.isRegularFile(cmd)) {
                    return cmd.toString();
                }
            }
        }
        return "codex";
    }

    private static Map<String, String> details(String... kv) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
