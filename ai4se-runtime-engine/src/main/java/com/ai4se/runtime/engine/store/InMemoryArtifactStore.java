package com.ai4se.runtime.engine.store;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.artifact.ArtifactStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryArtifactStore implements ArtifactStore {

    private final Map<String, Artifact> byId = new ConcurrentHashMap<String, Artifact>();

    @Override
    public Artifact put(Artifact artifact) {
        byId.put(artifact.artifactId().value(), artifact);
        return artifact;
    }

    @Override
    public Optional<Artifact> get(ArtifactId artifactId) {
        return Optional.ofNullable(byId.get(artifactId.value()));
    }

    @Override
    public List<Artifact> listByTask(TaskId taskId) {
        List<Artifact> list = new ArrayList<Artifact>();
        for (Artifact artifact : byId.values()) {
            if (artifact.taskId().equals(taskId)) {
                list.add(artifact);
            }
        }
        return list;
    }
}
