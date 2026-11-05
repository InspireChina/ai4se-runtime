package com.ai4se.execution.claude;

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
 * Claude Code CLI Adapter ({@code claude -p / --print ...}).
 * <p>
 * Same Context Package contract as {@link com.ai4se.execution.cursor.CursorCliAdapter}.
 * Owns neither Retry nor stage jumps — failures return as-is for 03 Control.
 * Binary defaults to {@code claude}; override with env {@code AI4SE_CLAUDE_BIN}.
 * Optional {@code --model} from {@link RoleModelResolver} / constructor default.
 */
public final class ClaudeCliAdapter implements ModelCliAdapter {

    public static final String NAME = "claude-cli";
    public static final String ENV_BIN = "AI4SE_CLAUDE_BIN";

    private final ProcessInvoker invoker;
    private final String binary;
    private final String defaultModel;

    public ClaudeCliAdapter() {
        this(new ProcessInvoker.RealProcessInvoker(), resolveBinary(null), null);
    }

    public ClaudeCliAdapter(ProcessInvoker invoker, String binary) {
        this(invoker, binary, null);
    }

    public ClaudeCliAdapter(ProcessInvoker invoker, String binary, String defaultModel) {
        this.invoker = invoker == null ? new ProcessInvoker.RealProcessInvoker() : invoker;
        this.binary = Strings.isBlank(binary) ? resolveBinary(null) : binary;
        this.defaultModel = Strings.isBlank(defaultModel) ? null : defaultModel.trim();
    }

    /** Convenience: vendor binary + fixed model for this Adapter instance. */
    public static ClaudeCliAdapter withModel(String model) {
        return new ClaudeCliAdapter(new ProcessInvoker.RealProcessInvoker(), resolveBinary(null), model);
    }

    @Override
    public String name() {
        return NAME;
    }

    public String defaultModel() {
        return defaultModel;
    }

    /** Binary this instance will actually launch — preflight must check this, not re-derive. */
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
                    -1,
                    "",
                    "",
                    "Context Package missing or incomplete: " + packageDir,
                    details("error", "package_missing"));
        }

        List<String> argv;
        String model;
        try {
            model = RoleModelResolver.modelFor(request, defaultModel);
            argv = buildArgv(request, manifest, model);
        } catch (IOException e) {
            return AdapterResult.failure(
                    -1, "", "", "Failed to read package: " + e.getMessage(), details("error", "package_io"));
        }

        Map<String, String> env = new LinkedHashMap<String, String>();
        env.putAll(request.env());
        try {
            ProcessInvoker.ProcessOutcome outcome =
                    invoker.run(argv, request.workspace(), env, request.timeout());
            Map<String, String> meta = details(
                    "adapter", NAME,
                    "binary", binary,
                    "role", request.role(),
                    "story_id", request.storyId(),
                    "package_dir", packageDir.toString());
            meta.put("model", Strings.isBlank(model) ? "(cli-default)" : model);
            meta.put("unattended_write_scope", UnattendedWriteScope.forRole(request.role()).name());
            meta.put("argv", join(argv));
            if (outcome.timedOut) {
                return AdapterResult.failure(
                        outcome.exitCode,
                        outcome.stdout,
                        outcome.stderr,
                        "Claude CLI timed out",
                        meta);
            }
            if (outcome.exitCode != 0) {
                String msg = "Claude CLI exit=" + outcome.exitCode;
                if (!Strings.isBlank(outcome.stderr)) {
                    msg = msg + ": " + trim(outcome.stderr, 500);
                } else if (!Strings.isBlank(outcome.stdout)) {
                    msg = msg + ": " + trim(outcome.stdout, 500);
                }
                return AdapterResult.failure(outcome.exitCode, outcome.stdout, outcome.stderr, msg, meta);
            }
            return AdapterResult.ok(outcome.exitCode, outcome.stdout, outcome.stderr, meta);
        } catch (IOException e) {
            return AdapterResult.failure(
                    -1, "", "", "Claude CLI IO error: " + e.getMessage(), details("error", "io"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AdapterResult.failure(
                    -1, "", "", "Claude CLI interrupted", details("error", "interrupted"));
        }
    }

    /**
     * {@code claude -p --output-format text [--model id]
     * [--dangerously-skip-permissions | --permission-mode acceptEdits] "<prompt>"}.
     * <p>
     * Permission via {@link UnattendedPermissionPolicy} + {@link CliVendor#CLAUDE}
     * (problem class: Contract write must not hang on interactive approval).
     */
    List<String> buildArgv(AdapterRequest request, Path manifest) throws IOException {
        return buildArgv(request, manifest, RoleModelResolver.modelFor(request, defaultModel));
    }

    List<String> buildArgv(AdapterRequest request, Path manifest, String model) throws IOException {
        List<String> afterBinary = new ArrayList<String>();
        afterBinary.add("-p");
        afterBinary.add("--output-format");
        afterBinary.add("text");
        if (!Strings.isBlank(model)) {
            afterBinary.add("--model");
            afterBinary.add(model.trim());
        }
        String role = request.role();
        UnattendedPermissionPolicy.apply(CliVendor.CLAUDE, role, afterBinary);
        afterBinary.add(ContextPackagePrompt.build(request, manifest));
        return new ArrayList<String>(ShellExecutable.launchArgv(binary, afterBinary));
    }

    public static String resolveBinary(String override) {
        if (!Strings.isBlank(override)) {
            return override;
        }
        String env = System.getenv(ENV_BIN);
        if (!Strings.isBlank(env)) {
            return env.trim();
        }
        // Common install locations (problem class: binary not ready — probe, then fail clearly)
        String appData = System.getenv("APPDATA");
        if (!Strings.isBlank(appData)) {
            Path npmClaude = Paths.get(appData, "npm", "claude.cmd");
            if (Files.isRegularFile(npmClaude)) {
                return npmClaude.toString();
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            String[] dirs = path.split(File.pathSeparator);
            for (int i = 0; i < dirs.length; i++) {
                String dir = dirs[i];
                if (dir == null || dir.isEmpty()) {
                    continue;
                }
                Path unix = Paths.get(dir, "claude");
                Path win = Paths.get(dir, "claude.cmd");
                if (Files.isRegularFile(unix)) {
                    return unix.toString();
                }
                if (Files.isRegularFile(win)) {
                    return win.toString();
                }
            }
        }
        return "claude";
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
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
