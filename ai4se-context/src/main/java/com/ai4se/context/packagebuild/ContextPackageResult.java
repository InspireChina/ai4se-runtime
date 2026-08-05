package com.ai4se.context.packagebuild;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Successful Analysis Context Package write result. */
public final class ContextPackageResult {

    private final String role;
    private final String storyId;
    private final Path packageDir;
    private final Path manifestPath;
    private final List<String> priority1;

    public ContextPackageResult(
            String role,
            String storyId,
            Path packageDir,
            Path manifestPath,
            List<String> priority1) {
        this.role = Objects.requireNonNull(role, "role");
        this.storyId = Objects.requireNonNull(storyId, "storyId");
        this.packageDir = Objects.requireNonNull(packageDir, "packageDir");
        this.manifestPath = Objects.requireNonNull(manifestPath, "manifestPath");
        this.priority1 = Collections.unmodifiableList(priority1);
    }

    public String role() {
        return role;
    }

    public String storyId() {
        return storyId;
    }

    public Path packageDir() {
        return packageDir;
    }

    public Path manifestPath() {
        return manifestPath;
    }

    public List<String> priority1() {
        return priority1;
    }
}
