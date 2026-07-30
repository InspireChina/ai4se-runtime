package com.ai4se.runtime.engine.support;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.worker.api.WorkRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds Worker-agnostic {@link WorkRequest} from Task.
 * Future ClaudeWorker / CodexWorker plug in via goal params ({@code operation}) — Runtime unchanged.
 */
public final class WorkRequestFactory {

    private WorkRequestFactory() {
    }

    public static WorkRequest create(Task task, WorkItemId workItemId, String traceId, Duration timeout) {
        return create(task, workItemId, traceId, timeout, null);
    }

    /**
     * @param commandOverride if non-null/non-blank, becomes operation + params.command (S7 plan.test-strategy).
     */
    public static WorkRequest create(
            Task task,
            WorkItemId workItemId,
            String traceId,
            Duration timeout,
            String commandOverride) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(workItemId, "workItemId");
        String operation = resolveOperation(task);
        Map<String, Object> params = new HashMap<String, Object>(task.goal().getParams());
        params.put("workdir", task.workspaceRef().getRootPath());
        if (commandOverride != null && !commandOverride.trim().isEmpty()) {
            operation = commandOverride.trim();
            params.put("command", operation);
            params.put("operation", operation);
            params.put("fromPlanTestStrategy", Boolean.TRUE);
        } else if (!params.containsKey("operation")) {
            params.put("operation", operation);
        }
        return new WorkRequest(
                workItemId,
                task.taskId(),
                operation,
                Collections2.<ArtifactId>emptyList(),
                params,
                timeout == null ? Duration.ofMinutes(1) : timeout,
                Optional.<String>empty(),
                Optional.ofNullable(traceId));
    }

    public static String resolveOperation(Task task) {
        Object operation = task.goal().getParams().get("operation");
        if (operation != null && !operation.toString().trim().isEmpty()) {
            return operation.toString().trim();
        }
        Object command = task.goal().getParams().get("command");
        if (command != null && !command.toString().trim().isEmpty()) {
            return command.toString().trim();
        }
        return task.goal().getType();
    }
}
