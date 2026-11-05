package com.ai4se.runtime.kernel.checkpoint;

import com.ai4se.runtime.common.util.Strings;
import java.util.Objects;

/** Integrity digest for a Checkpoint payload (C5). Immutable after create. */
public final class CheckpointIntegrity {

    private final String digest;

    public CheckpointIntegrity(String digest) {
        this.digest = Strings.requireNonBlank(digest, "integrity");
    }

    public String digest() {
        return digest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CheckpointIntegrity)) {
            return false;
        }
        CheckpointIntegrity other = (CheckpointIntegrity) o;
        return digest.equals(other.digest);
    }

    @Override
    public int hashCode() {
        return digest.hashCode();
    }

    @Override
    public String toString() {
        return digest;
    }
}
