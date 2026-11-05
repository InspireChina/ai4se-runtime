package com.ai4se.runtime.kernel.artifact;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import java.util.List;
import java.util.Optional;

public interface ArtifactStore {

    Artifact put(Artifact artifact);

    Optional<Artifact> get(ArtifactId artifactId);

    List<Artifact> listByTask(TaskId taskId);
}
