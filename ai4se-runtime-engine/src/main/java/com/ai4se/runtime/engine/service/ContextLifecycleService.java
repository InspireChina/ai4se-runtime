package com.ai4se.runtime.engine.service;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.ExecutionContextId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.kernel.artifact.ArtifactLifecycle;
import com.ai4se.runtime.kernel.context.ExecutionContext;
import com.ai4se.runtime.kernel.context.ExecutionContextPhase;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Context Lifecycle Service — Materializing → Active → Frozen (E1–E7).
 */
public class ContextLifecycleService {

    private final ConcurrentMap<String, ExecutionContext> byId = new ConcurrentHashMap<String, ExecutionContext>();

    public ExecutionContext materialize(ExecutionContextId contextId, TaskId taskId, String profileId) {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(taskId, "taskId");
        ExecutionContext context = new ExecutionContext(contextId, taskId, profileId);
        if (context.phase() != ExecutionContextPhase.MATERIALIZING) {
            throw new IllegalStateException("New context must start MATERIALIZING");
        }
        ExecutionContext previous = byId.putIfAbsent(contextId.value(), context);
        if (previous != null) {
            throw new IllegalStateException("ExecutionContext already materialized: " + contextId);
        }
        return context;
    }

    public void activate(ExecutionContext context) {
        Objects.requireNonNull(context, "context");
        context.activate();
    }

    public Optional<ExecutionContext> get(ExecutionContextId contextId) {
        return Optional.ofNullable(byId.get(contextId.value()));
    }

    /**
     * Indexes an artifact only after it is COMMITTED and Context is ACTIVE (G5 / E4).
     */
    public void indexCommitted(ExecutionContext context, ArtifactId artifactId, ArtifactLifecycle lifecycle) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(artifactId, "artifactId");
        if (lifecycle != ArtifactLifecycle.COMMITTED) {
            throw new IllegalStateException(
                    "Refuse to index non-COMMITTED artifact: " + artifactId + " lifecycle=" + lifecycle);
        }
        context.indexArtifact(artifactId);
    }

    public void freeze(ExecutionContext context) {
        Objects.requireNonNull(context, "context");
        context.freeze();
    }
}
