package com.ai4se.execution.cursor;

import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 03-facing submission: hand a built Context Package to an Adapter.
 * Does not interpret Retry / next stage — returns {@link AdapterResult} as-is.
 */
public final class PackageAdapterSubmission {

    private PackageAdapterSubmission() {
    }

    public static AdapterResult submit(
            ModelCliAdapter adapter,
            Path workspace,
            ContextPackageResult pkg,
            Duration timeout) {
        return submit(adapter, workspace, pkg, timeout, Collections.<String, String>emptyMap());
    }

    public static AdapterResult submit(
            ModelCliAdapter adapter,
            Path workspace,
            ContextPackageResult pkg,
            Duration timeout,
            Map<String, String> env) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(pkg, "pkg");
        Map<String, String> merged = new LinkedHashMap<String, String>();
        if (env != null) {
            merged.putAll(env);
        }
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
