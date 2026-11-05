package com.ai4se.runtime.worker.api;

import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.common.util.Strings;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class WorkerDescriptor {

    private final WorkerId workerId;
    private final WorkerKind kind;
    private final String name;
    private final String version;
    private final List<String> capabilities;
    private final int maxConcurrent;
    private final Set<String> resourceClasses;
    private final boolean idempotentByDefault;

    public WorkerDescriptor(
            WorkerId workerId,
            WorkerKind kind,
            String name,
            String version,
            List<String> capabilities,
            int maxConcurrent,
            Set<String> resourceClasses,
            boolean idempotentByDefault) {
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Strings.requireNonBlank(name, "name");
        this.version = Strings.requireNonBlank(version, "version");
        this.capabilities = Collections2.copyList(capabilities);
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be >= 1");
        }
        this.maxConcurrent = maxConcurrent;
        this.resourceClasses = Collections2.copySet(resourceClasses);
        this.idempotentByDefault = idempotentByDefault;
    }

    public WorkerId getWorkerId() { return workerId; }
    public WorkerKind getKind() { return kind; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public List<String> getCapabilities() { return capabilities; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public Set<String> getResourceClasses() { return resourceClasses; }
    public boolean isIdempotentByDefault() { return idempotentByDefault; }
}
