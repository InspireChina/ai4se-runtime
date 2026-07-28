package com.ai4se.runtime.kernel.artifact;

import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.id.WorkerId;
import java.util.Objects;
import java.util.Optional;

public final class ProducedBy {

    private final WorkItemId workItemId;
    private final WorkerId workerId;
    private final Optional<String> capabilityId;
    private final Optional<String> spanId;

    public ProducedBy(
            WorkItemId workItemId,
            WorkerId workerId,
            Optional<String> capabilityId,
            Optional<String> spanId) {
        this.workItemId = Objects.requireNonNull(workItemId, "workItemId");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.capabilityId = capabilityId == null ? Optional.<String>empty() : capabilityId;
        this.spanId = spanId == null ? Optional.<String>empty() : spanId;
    }

    public WorkItemId getWorkItemId() { return workItemId; }
    public WorkerId getWorkerId() { return workerId; }
    public Optional<String> getCapabilityId() { return capabilityId; }
    public Optional<String> getSpanId() { return spanId; }
}
