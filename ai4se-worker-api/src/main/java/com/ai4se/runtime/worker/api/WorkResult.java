package com.ai4se.runtime.worker.api;

import com.ai4se.runtime.common.error.ReasonCode;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.util.Collections2;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorkResult {

    private final WorkResultStatus status;
    private final List<ArtifactId> outputArtifactIds;
    private final Optional<ReasonCode> reasonCode;
    private final Optional<String> message;
    private final Map<String, Object> metrics;

    public WorkResult(
            WorkResultStatus status,
            List<ArtifactId> outputArtifactIds,
            Optional<ReasonCode> reasonCode,
            Optional<String> message,
            Map<String, Object> metrics) {
        this.status = Objects.requireNonNull(status, "status");
        this.outputArtifactIds = Collections2.copyList(outputArtifactIds);
        this.reasonCode = reasonCode == null ? Optional.<ReasonCode>empty() : reasonCode;
        this.message = message == null ? Optional.<String>empty() : message;
        this.metrics = Collections2.copyMap(metrics);
    }

    public static WorkResult ok(List<ArtifactId> outputs) {
        return new WorkResult(
                WorkResultStatus.OK,
                outputs,
                Optional.<ReasonCode>empty(),
                Optional.<String>empty(),
                Collections2.<String, Object>emptyMap());
    }

    public WorkResultStatus getStatus() { return status; }
    public List<ArtifactId> getOutputArtifactIds() { return outputArtifactIds; }
    public Optional<ReasonCode> getReasonCode() { return reasonCode; }
    public Optional<String> getMessage() { return message; }
    public Map<String, Object> getMetrics() { return metrics; }
}
