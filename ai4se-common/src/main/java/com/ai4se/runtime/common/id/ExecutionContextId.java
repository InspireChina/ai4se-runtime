package com.ai4se.runtime.common.id;

import com.ai4se.runtime.common.util.Strings;
import java.util.Objects;

public final class ExecutionContextId {

    private final String value;

    public ExecutionContextId(String value) {
        this.value = Strings.requireNonBlank(value, "executionContextId");
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExecutionContextId)) {
            return false;
        }
        ExecutionContextId other = (ExecutionContextId) o;
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
