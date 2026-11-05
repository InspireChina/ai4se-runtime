package com.ai4se.context.codegraph;

/** Deterministic Code Graph contract failure (missing/stale/corrupt). */
public final class CodeGraphException extends RuntimeException {

    public CodeGraphException(String message) {
        super(message);
    }

    public CodeGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
