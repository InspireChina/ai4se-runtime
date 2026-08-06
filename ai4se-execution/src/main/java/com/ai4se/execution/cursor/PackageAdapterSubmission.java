package com.ai4se.execution.cursor;

import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.model.RoleModelConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 03-facing submission: hand a built Context Package to an Adapter.
 * Does not interpret Retry / next stage — returns {@link AdapterResult} as-is.
 * Optionally injects per-role {@link RoleModelConfig} as {@code AI4SE_MODEL}.
 */
public final class PackageAdapterSubmission {

    private PackageAdapterSubmission() {
    }

    public static AdapterResult submit(
            ModelCliAdapter adapter,
            Path workspace,
            ContextPackageResult pkg,
            Duration timeout) {
        return submit(adapter, workspace, pkg, timeout, Collections.<String, String>emptyMap(), null);
    }

    public static AdapterResult submit(
            ModelCliAdapter adapter,
            Path workspace,
            ContextPackageResult pkg,
            Duration timeout,
            Map<String, String> env) {
        return submit(adapter, workspace, pkg, timeout, env, null);
    }

    public static AdapterResult submit(
            ModelCliAdapter adapter,
            Path workspace,
            ContextPackageResult pkg,
            Duration timeout,
            Map<String, String> env,
            RoleModelConfig roleModels) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(pkg, "pkg");
        Map<String, String> merged = new LinkedHashMap<String, String>();
        if (env != null) {
            merged.putAll(env);
        }
        RoleModelConfig.putResolvedModel(merged, pkg.role(), roleModels);
        AdapterRequest request = new AdapterRequest(
                workspace,
                pkg.packageDir(),
                pkg.role(),
                pkg.storyId(),
                timeout,
                merged);
        return adapter.execute(request);
    }
}
