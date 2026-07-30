package com.ai4se.runtime.engine.support;

import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.kernel.context.ExecutionContext;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory hold for Tasks waiting on human Artifacts (S4). Not a Scheduler. */
public final class HumanWaitRegistry {

    public static final class Session {
        public final Task task;
        public final ExecutionContext context;
        public final TraceRoot trace;
        public final LifecycleJournal journal;
        public final long startedNanos;
        public final WorkItemId lastWorkItemId;

        public Session(
                Task task,
                ExecutionContext context,
                TraceRoot trace,
                LifecycleJournal journal,
                long startedNanos,
                WorkItemId lastWorkItemId) {
            this.task = Objects.requireNonNull(task, "task");
            this.context = Objects.requireNonNull(context, "context");
            this.trace = Objects.requireNonNull(trace, "trace");
            this.journal = Objects.requireNonNull(journal, "journal");
            this.startedNanos = startedNanos;
            this.lastWorkItemId = Objects.requireNonNull(lastWorkItemId, "lastWorkItemId");
        }
    }

    private final ConcurrentHashMap<String, Session> byTaskId = new ConcurrentHashMap<String, Session>();

    public void put(Session session) {
        byTaskId.put(session.task.taskId().value(), session);
    }

    public Session get(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return byTaskId.get(taskId.value());
    }

    public Session remove(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return byTaskId.remove(taskId.value());
    }
}
