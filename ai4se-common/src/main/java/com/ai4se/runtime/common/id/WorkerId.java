package com.ai4se.runtime.common.id;

import com.ai4se.runtime.common.util.Strings;
import java.util.Objects;

public final class WorkerId {

    private final String value;

    public WorkerId(String value) {
        this.value = Strings.requireNonBlank(value, "workerId");
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkerId)) {
            return false;
        }
        WorkerId other = (WorkerId) o;
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
