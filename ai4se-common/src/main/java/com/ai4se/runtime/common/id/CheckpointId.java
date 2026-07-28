package com.ai4se.runtime.common.id;

import com.ai4se.runtime.common.util.Strings;
import java.util.Objects;

public final class CheckpointId {

    private final String value;

    public CheckpointId(String value) {
        this.value = Strings.requireNonBlank(value, "checkpointId");
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CheckpointId)) {
            return false;
        }
        CheckpointId other = (CheckpointId) o;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
