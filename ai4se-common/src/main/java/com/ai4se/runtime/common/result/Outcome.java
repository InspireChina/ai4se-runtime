package com.ai4se.runtime.common.result;

import com.ai4se.runtime.common.error.ErrorRef;
import java.util.Objects;
import java.util.Optional;

public final class Outcome<T> {

    private final T value;
    private final ErrorRef error;

    private Outcome(T value, ErrorRef error) {
        this.value = value;
        this.error = error;
    }

    public static <T> Outcome<T> ok(T value) {
        return new Outcome<T>(Objects.requireNonNull(value, "value"), null);
    }

    public static <T> Outcome<T> fail(ErrorRef error) {
        return new Outcome<T>(null, Objects.requireNonNull(error, "error"));
    }

    public boolean isOk() {
        return error == null;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public Optional<ErrorRef> error() {
        return Optional.ofNullable(error);
    }
}
