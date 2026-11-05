package com.ai4se.runtime.kernel.trace;

import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.util.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Trace root bound to exactly one Task (R1/R2). Append-only projection; not a Task state authority (R6).
 */
public final class TraceRoot {

    /** Vertical-slice required nodes. */
    public static final String SPAN_SUBMIT = "submit";
    public static final String SPAN_WORKER = "worker";
    public static final String SPAN_ARTIFACT = "artifact";
    public static final String SPAN_FINISH = "finish";

    private final String traceId;
    private final TaskId taskId;
    private final List<TraceSpan> spans = new CopyOnWriteArrayList<TraceSpan>();
    private boolean closed;

    public TraceRoot(String traceId, TaskId taskId) {
        this.traceId = Strings.requireNonBlank(traceId, "traceId");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.closed = false;
    }

    public String traceId() {
        return traceId;
    }

    public TaskId taskId() {
        return taskId;
    }

    public boolean closed() {
        return closed;
    }

    public List<TraceSpan> spans() {
        return Collections.unmodifiableList(new ArrayList<TraceSpan>(spans));
    }

    public List<String> spanNames() {
        List<String> names = new ArrayList<String>();
        for (TraceSpan span : spans) {
            names.add(span.name());
        }
        return Collections.unmodifiableList(names);
    }

    public void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("TraceRoot is closed: " + traceId);
        }
    }

    public void appendSpan(TraceSpan span) {
        ensureOpen();
        Objects.requireNonNull(span, "span");
        if (!traceId.equals(span.traceId()) || !taskId.equals(span.taskId())) {
            throw new IllegalArgumentException("Span must belong to this TraceRoot/taskId");
        }
        if (span.parentSpanId().isPresent()) {
            boolean parentFound = false;
            for (TraceSpan existing : spans) {
                if (existing.spanId().equals(span.parentSpanId().get())) {
                    parentFound = true;
                    break;
                }
            }
            if (!parentFound) {
                throw new IllegalStateException("parentSpanId not in this Trace: " + span.parentSpanId().get());
            }
        }
        spans.add(span);
    }

    public void close() {
        if (closed) {
            throw new IllegalStateException("TraceRoot already closed: " + traceId);
        }
        this.closed = true;
    }
}
