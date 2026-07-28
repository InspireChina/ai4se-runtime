package com.ai4se.runtime.engine.support;

import com.ai4se.runtime.common.id.ExecutionContextId;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.kernel.context.ExecutionContext;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import java.util.Objects;

/**
 * Bootstraps Context + Trace for a Task that is already STARTING.
 * Checkpoint / Resume should add alternate entry points here — not fork Runtime.submit.
 */
public final class ExecutionBootstrap {

    private ExecutionBootstrap() {
    }

    public static Session open(
            Task task,
            TaskLifecycleService tasks,
            ContextLifecycleService contexts,
            TraceLifecycleService traces,
            LifecycleJournal journal) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(contexts, "contexts");
        Objects.requireNonNull(traces, "traces");
        Objects.requireNonNull(journal, "journal");

        ExecutionContextId contextId = new ExecutionContextId(Ids.next("ctx"));
        ExecutionContext context = contexts.materialize(contextId, task.taskId(), task.profileId());
        journal.record("CONTEXT MATERIALIZING id=" + contextId + " phase=" + context.phase());

        tasks.bindExecutionContext(task, contextId);
        journal.record("TASK bindExecutionContext");

        TraceRoot trace = traces.open(task.taskId());
        tasks.bindTraceId(task, trace.traceId());
        journal.record("TRACE open id=" + trace.traceId());

        traces.recordNode(trace.traceId(), TraceRoot.SPAN_SUBMIT);
        journal.record("TRACE span=" + TraceRoot.SPAN_SUBMIT);

        contexts.activate(context);
        journal.record("CONTEXT ACTIVE phase=" + context.phase());

        tasks.markRunning(task);
        journal.record("TASK → RUNNING");

        return new Session(context, trace);
    }

    /** Immutable handle for the active run; Resume can restore an equivalent Session later. */
    public static final class Session {
        private final ExecutionContext context;
        private final TraceRoot trace;

        public Session(ExecutionContext context, TraceRoot trace) {
            this.context = Objects.requireNonNull(context, "context");
            this.trace = Objects.requireNonNull(trace, "trace");
        }

        public ExecutionContext context() {
            return context;
        }

        public TraceRoot trace() {
            return trace;
        }
    }
}
