package com.ai4se.execution.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Input to a Tool Adapter. Built by 02/03 — Adapter must not roam the knowledge tree.
 */
public final class AdapterRequest {

    private final Path workspace;
    private final Path packageDir;
    private final String role;
    private final String storyId;
    private final Duration timeout;
    private final Map<String, String> env;

    public AdapterRequest(
            Path workspace,
            Path packageDir,
            String role,
            String storyId,
            Duration timeout,
            Map<String, String> env) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.packageDir = Objects.requireNonNull(packageDir, "packageDir");
        this.role = Objects.requireNonNull(role, "role");
        this.storyId = Objects.requireNonNull(storyId, "storyId");
        this.timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
        this.env = env == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(env);
    }

    public Path workspace() {
        return workspace;
    }

    public Path packageDir() {
        return packageDir;
    }

    public String role() {
        return role;
    }

    public String storyId() {
        return storyId;
    }

    public Duration timeout() {
        return timeout;
    }

    public Map<String, String> env() {
        return env;
    }
}
