package com.ai4se.runtime.common.util;

import java.util.Objects;

/** Java 8 helpers (String.isBlank is Java 11+). */
public final class Strings {

    private Strings() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
