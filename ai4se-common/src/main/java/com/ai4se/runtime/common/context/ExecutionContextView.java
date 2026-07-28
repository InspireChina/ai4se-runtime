package com.ai4se.runtime.common.context;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.ExecutionContextId;
import com.ai4se.runtime.common.id.TaskId;
import java.util.List;
import java.util.Optional;

/**
 * Read-only ExecutionContext surface for collaborators (e.g. Worker).
 * Lives in common so Kernel does not depend on worker-api.
 */
public interface ExecutionContextView {

    ExecutionContextId contextId();

    TaskId taskId();

    boolean frozen();

    List<ArtifactId> listArtifactIds();

    Optional<String> profileId();
}
