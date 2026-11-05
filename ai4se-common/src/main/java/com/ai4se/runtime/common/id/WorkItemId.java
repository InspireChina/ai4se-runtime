package com.ai4se.runtime.common.id;

import com.ai4se.runtime.common.util.Strings;
import java.util.Objects;

public final class WorkItemId {

    private final String value;

    public WorkItemId(String value) {
        this.value = Strings.requireNonBlank(value, "workItemId");
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkItemId)) {
            return false;
        }
        WorkItemId other = (WorkItemId) o;
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
