package com.ai4se.runtime.kernel.artifact;

import com.ai4se.runtime.common.util.Strings;
import java.util.Objects;

public final class ArtifactStorage {

    private final ArtifactStorageKind kind;
    private final String locator;

    public ArtifactStorage(ArtifactStorageKind kind, String locator) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.locator = Strings.requireNonBlank(locator, "locator");
    }

    public ArtifactStorageKind getKind() { return kind; }
    public String getLocator() { return locator; }
}
