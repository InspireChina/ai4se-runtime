package com.ai4se.runtime.kernel.trace;

import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.util.Strings;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Append-only span under a TraceRoot (R2–R4). Not a Task status authority (R6).
 */
public final class TraceSpan {

    private final String spanId;
    private final String traceId;
    private final TaskId taskId;
    private final Optional<String> parentSpanId;
    private final String name;
    private final Instant startedAt;
    private Instant endedAt;
    private boolean ended;

    public TraceSpan(
            String spanId,
            String traceId,
            TaskId taskId,
            Optional<String> parentSpanId,
            String name,
            Instant startedAt) {
        this.spanId = Strings.requireNonBlank(spanId, "spanId");
        this.traceId = Strings.requireNonBlank(traceId, "traceId");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.parentSpanId = parentSpanId == null ? Optional.<String>empty() : parentSpanId;
        this.name = Strings.requireNonBlank(name, "name");
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
        this.ended = false;
    }

    public String spanId() { return spanId; }
    public String traceId() { return traceId; }
    public TaskId taskId() { return taskId; }
    public Optional<String> parentSpanId() { return parentSpanId; }
    public String name() { return name; }
    public Instant startedAt() { return startedAt; }
    public Optional<Instant> endedAt() { return Optional.ofNullable(endedAt); }
    public boolean ended() { return ended; }

    public void end() {
        if (ended) {
            throw new IllegalStateException("Span already ended: " + spanId);
        }
        this.ended = true;
        this.endedAt = Instant.now();
    }
}
