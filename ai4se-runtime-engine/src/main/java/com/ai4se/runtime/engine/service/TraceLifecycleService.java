package com.ai4se.runtime.engine.service;

import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.util.Strings;
import com.ai4se.runtime.engine.support.Ids;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import com.ai4se.runtime.kernel.trace.TraceSpan;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Trace Lifecycle Service — open / append span / close (R1–R5). Does not mutate Task status (R6).
 */
public class TraceLifecycleService {

    private final ConcurrentMap<String, TraceRoot> byTraceId = new ConcurrentHashMap<String, TraceRoot>();
    private final ConcurrentMap<String, String> traceIdByTask = new ConcurrentHashMap<String, String>();

    public TraceRoot open(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        if (traceIdByTask.containsKey(taskId.value())) {
            throw new IllegalStateException("Task already has TraceRoot: " + taskId);
        }
        TraceRoot root = new TraceRoot(Ids.next("trace"), taskId);
        byTraceId.put(root.traceId(), root);
        traceIdByTask.put(taskId.value(), root.traceId());
        return root;
    }

    public Optional<TraceRoot> get(String traceId) {
        return Optional.ofNullable(byTraceId.get(traceId));
    }

    public Optional<TraceRoot> getByTask(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        String traceId = traceIdByTask.get(taskId.value());
        if (traceId == null) {
            return Optional.empty();
        }
        return get(traceId);
    }

    /**
     * Records a completed span node (open+end) under the root. Parent is previous span if any.
     */
    public TraceSpan recordNode(String traceId, String name) {
        TraceRoot root = requireOpen(traceId);
        Optional<String> parent = Optional.empty();
        if (!root.spans().isEmpty()) {
            parent = Optional.of(root.spans().get(root.spans().size() - 1).spanId());
        }
        TraceSpan span = new TraceSpan(
                Ids.next("span"),
                root.traceId(),
                root.taskId(),
                parent,
                name,
                Instant.now());
        root.appendSpan(span);
        span.end();
        // Walking Skeleton: Trace as log projection (no external observer yet).
        System.out.println("[TRACE] traceId=" + traceId + " span=" + name);
        return span;
    }

    public void close(String traceId) {
        TraceRoot root = require(traceId);
        root.close();
        System.out.println("[TRACE] traceId=" + traceId + " closed");
    }

    /** Terminal span + close — shared by success/fail so Runtime does not duplicate finish logic. */
    public void finishAndClose(String traceId) {
        recordNode(traceId, TraceRoot.SPAN_FINISH);
        close(traceId);
    }

    private TraceRoot requireOpen(String traceId) {
        TraceRoot root = require(traceId);
        root.ensureOpen();
        return root;
    }

    private TraceRoot require(String traceId) {
        Strings.requireNonBlank(traceId, "traceId");
        TraceRoot root = byTraceId.get(traceId);
        if (root == null) {
            throw new IllegalArgumentException("Unknown traceId: " + traceId);
        }
        return root;
    }
}
