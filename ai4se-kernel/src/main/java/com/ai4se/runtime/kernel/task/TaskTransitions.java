package com.ai4se.runtime.kernel.task;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TaskTransitions {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED;

    static {
        Map<TaskStatus, Set<TaskStatus>> map = new EnumMap<TaskStatus, Set<TaskStatus>>(TaskStatus.class);
        map.put(TaskStatus.CREATED, freeze(EnumSet.of(TaskStatus.VALIDATING, TaskStatus.CANCELLED)));
        map.put(TaskStatus.VALIDATING, freeze(EnumSet.of(TaskStatus.QUEUED, TaskStatus.FAILED)));
        map.put(TaskStatus.QUEUED, freeze(EnumSet.of(TaskStatus.SCHEDULED, TaskStatus.CANCELLED)));
        map.put(TaskStatus.SCHEDULED, freeze(EnumSet.of(TaskStatus.STARTING, TaskStatus.CANCELLED)));
        map.put(TaskStatus.STARTING, freeze(EnumSet.of(TaskStatus.RUNNING, TaskStatus.FAILED)));
        map.put(TaskStatus.RUNNING, freeze(EnumSet.of(
                TaskStatus.WAITING_DEPENDENCY,
                TaskStatus.RETRY_WAIT,
                TaskStatus.CHECKPOINTING,
                TaskStatus.BLOCKED_POLICY,
                TaskStatus.SUCCEEDED,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED)));
        map.put(TaskStatus.WAITING_DEPENDENCY, freeze(EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED)));
        map.put(TaskStatus.RETRY_WAIT, freeze(EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED)));
        map.put(TaskStatus.CHECKPOINTING, freeze(EnumSet.of(TaskStatus.RUNNING)));
        map.put(TaskStatus.BLOCKED_POLICY, freeze(EnumSet.of(
                TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED)));
        ALLOWED = Collections.unmodifiableMap(map);
    }

    private TaskTransitions() {
    }

    private static Set<TaskStatus> freeze(EnumSet<TaskStatus> set) {
        return Collections.unmodifiableSet(set);
    }

    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isTerminal()) {
            return false;
        }
        Set<TaskStatus> next = ALLOWED.get(from);
        return next != null && next.contains(to);
    }

    public static void requireTransition(TaskStatus from, TaskStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal Task transition: " + from + " -> " + to);
        }
    }
}
