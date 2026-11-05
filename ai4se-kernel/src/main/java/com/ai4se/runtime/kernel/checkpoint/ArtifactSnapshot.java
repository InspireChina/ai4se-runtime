package com.ai4se.runtime.kernel.checkpoint;

import com.ai4se.runtime.common.id.ArtifactId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable index of Artifact ids at a durable boundary (no blobs). */
public final class ArtifactSnapshot {

    private final List<ArtifactId> artifactIds;

    public ArtifactSnapshot(List<ArtifactId> artifactIds) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            this.artifactIds = Collections.emptyList();
        } else {
            this.artifactIds = Collections.unmodifiableList(new ArrayList<ArtifactId>(artifactIds));
        }
    }

    public static ArtifactSnapshot empty() {
        return new ArtifactSnapshot(Collections.<ArtifactId>emptyList());
    }

    public static ArtifactSnapshot of(List<ArtifactId> artifactIds) {
        return new ArtifactSnapshot(artifactIds);
    }

    public List<ArtifactId> artifactIds() {
        return artifactIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArtifactSnapshot)) {
            return false;
        }
        ArtifactSnapshot other = (ArtifactSnapshot) o;
        return artifactIds.equals(other.artifactIds);
    }

    @Override
    public int hashCode() {
        return artifactIds.hashCode();
    }

    @Override
    public String toString() {
        return "ArtifactSnapshot" + artifactIds;
    }
}
