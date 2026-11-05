package com.ai4se.execution.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Raw Adapter outcome for 03 Control. No next-stage / retry decisions.
 */
public final class AdapterResult {

    private final boolean success;
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final String message;
    private final Map<String, String> details;

    public AdapterResult(
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            String message,
            Map<String, String> details) {
        this.success = success;
        this.exitCode = exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.message = message == null ? "" : message;
        this.details = details == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(details));
    }

    public static AdapterResult ok(int exitCode, String stdout, String stderr, Map<String, String> details) {
        return new AdapterResult(true, exitCode, stdout, stderr, "", details);
    }

    public static AdapterResult failure(
            int exitCode, String stdout, String stderr, String message, Map<String, String> details) {
        return new AdapterResult(false, exitCode, stdout, stderr, message, details);
    }

    public boolean success() {
        return success;
    }

    public int exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    /** Human-readable failure as returned from the tool — Control decides what to do. */
    public String message() {
        return message;
    }

    public Map<String, String> details() {
        return details;
    }

    public boolean hasNextStageHint() {
        return details.containsKey("next_stage")
                || details.containsKey("retry")
                || details.containsKey("skip_verification");
    }
}
