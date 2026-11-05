package com.ai4se.runtime.common.error;

import com.ai4se.runtime.common.util.Strings;

public final class ReasonCode {

    private final String value;

    public ReasonCode(String value) {
        this.value = Strings.requireNonBlank(value, "reasonCode");
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReasonCode)) {
            return false;
        }
        return value.equals(((ReasonCode) o).value);
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
