package com.ai4se.runtime.kernel.artifact;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.common.util.Strings;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Artifact {

    private final ArtifactId artifactId;
    private final TaskId taskId;
    private final String kind;
    private final String name;
    private final String mediaType;
    private final Optional<String> schemaRef;
    private final ArtifactStorage storage;
    private final Optional<String> checksum;
    private final Optional<Long> sizeBytes;
    private final Optional<ProducedBy> producedBy;
    private final ArtifactVisibility visibility;
    private final ArtifactSensitivity sensitivity;
    private final Map<String, String> labels;
    private final Instant createdAt;

    private ArtifactLifecycle lifecycle;

    private Artifact(Builder b) {
        this.artifactId = Objects.requireNonNull(b.artifactId, "artifactId");
        this.taskId = Objects.requireNonNull(b.taskId, "taskId");
        this.kind = Strings.requireNonBlank(b.kind, "kind");
        this.name = Strings.requireNonBlank(b.name, "name");
        this.mediaType = Strings.requireNonBlank(b.mediaType, "mediaType");
        this.schemaRef = optionalNonBlank(b.schemaRef);
        this.storage = Objects.requireNonNull(b.storage, "storage");
        this.checksum = optionalNonBlank(b.checksum);
        this.sizeBytes = Optional.ofNullable(b.sizeBytes);
        this.producedBy = Optional.ofNullable(b.producedBy);
        this.visibility = b.visibility == null ? ArtifactVisibility.TASK : b.visibility;
        this.sensitivity = b.sensitivity == null ? ArtifactSensitivity.NONE : b.sensitivity;
        this.labels = Collections2.copyMap(b.labels);
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.lifecycle = ArtifactLifecycle.PROPOSED;
    }

    private static Optional<String> optionalNonBlank(String value) {
        if (Strings.isBlank(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ArtifactId artifactId() { return artifactId; }
    public TaskId taskId() { return taskId; }
    public String kind() { return kind; }
    public String name() { return name; }
    public String mediaType() { return mediaType; }
    public Optional<String> schemaRef() { return schemaRef; }
    public ArtifactStorage storage() { return storage; }
    public Optional<String> checksum() { return checksum; }
    public Optional<Long> sizeBytes() { return sizeBytes; }
    public Optional<ProducedBy> producedBy() { return producedBy; }
    public ArtifactVisibility visibility() { return visibility; }
    public ArtifactSensitivity sensitivity() { return sensitivity; }
    public Map<String, String> labels() { return labels; }
    public Instant createdAt() { return createdAt; }
    public ArtifactLifecycle lifecycle() { return lifecycle; }

    public void commit() {
        requireLifecycle(ArtifactLifecycle.PROPOSED);
        this.lifecycle = ArtifactLifecycle.COMMITTED;
    }

    public void abandon() {
        if (lifecycle == ArtifactLifecycle.COMMITTED) {
            throw new IllegalStateException("Cannot abandon a COMMITTED artifact");
        }
        this.lifecycle = ArtifactLifecycle.ABANDONED;
    }

    public void supersede() {
        requireLifecycle(ArtifactLifecycle.COMMITTED);
        this.lifecycle = ArtifactLifecycle.SUPERSEDED;
    }

    private void requireLifecycle(ArtifactLifecycle expected) {
        if (lifecycle != expected) {
            throw new IllegalStateException("Expected lifecycle " + expected + " but was " + lifecycle);
        }
    }

    public static final class Builder {
        private ArtifactId artifactId;
        private TaskId taskId;
        private String kind;
        private String name;
        private String mediaType;
        private String schemaRef;
        private ArtifactStorage storage;
        private String checksum;
        private Long sizeBytes;
        private ProducedBy producedBy;
        private ArtifactVisibility visibility;
        private ArtifactSensitivity sensitivity;
        private Map<String, String> labels;
        private Instant createdAt;

        private Builder() {
        }

        public Builder artifactId(ArtifactId artifactId) { this.artifactId = artifactId; return this; }
        public Builder taskId(TaskId taskId) { this.taskId = taskId; return this; }
        public Builder kind(String kind) { this.kind = kind; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder mediaType(String mediaType) { this.mediaType = mediaType; return this; }
        public Builder schemaRef(String schemaRef) { this.schemaRef = schemaRef; return this; }
        public Builder storage(ArtifactStorage storage) { this.storage = storage; return this; }
        public Builder checksum(String checksum) { this.checksum = checksum; return this; }
        public Builder sizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; return this; }
        public Builder producedBy(ProducedBy producedBy) { this.producedBy = producedBy; return this; }
        public Builder visibility(ArtifactVisibility visibility) { this.visibility = visibility; return this; }
        public Builder sensitivity(ArtifactSensitivity sensitivity) { this.sensitivity = sensitivity; return this; }
        public Builder labels(Map<String, String> labels) { this.labels = labels; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Artifact build() {
            return new Artifact(this);
        }
    }
}
