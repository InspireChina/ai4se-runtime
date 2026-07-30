package com.ai4se.runtime.engine.support;

import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.Worker;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps unified {@link WorkResult} → Artifact payload/labels.
 * Keeps Runtime free of stdout/stderr/metric branching (ClaudeWorker-ready).
 */
public final class WorkResultMapper {

    private WorkResultMapper() {
    }

    public static String payload(WorkResult workResult) {
        Objects.requireNonNull(workResult, "workResult");
        Object stdout = workResult.getMetrics().get("stdout");
        Object stderr = workResult.getMetrics().get("stderr");
        StringBuilder sb = new StringBuilder();
        if (stdout != null && !stdout.toString().isEmpty()) {
            sb.append(stdout.toString());
        }
        if (stderr != null && !stderr.toString().isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("[stderr]\n").append(stderr.toString());
        }
        if (sb.length() == 0) {
            return workResult.getMessage().orElse("(empty)");
        }
        return sb.toString();
    }

    public static Map<String, String> metadata(Task task, Worker worker, WorkResult workResult) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(workResult, "workResult");
        Map<String, String> labels = new HashMap<String, String>();
        labels.put("taskId", task.taskId().value());
        labels.put("workerName", metric(workResult, "workerName", worker.descriptor().getName()));
        labels.put("command", metric(workResult, "command", WorkRequestFactory.resolveOperation(task)));
        labels.put("exitCode", metric(workResult, "exitCode", ""));
        labels.put("duration", metric(workResult, "durationMs", "0") + "ms");
        labels.put("timestamp", Instant.now().toString());
        String artifactName = artifactName(workResult, null);
        if (artifactName != null) {
            labels.put("artifactName", artifactName);
        }
        return labels;
    }

    /** Optional Worker hint for Artifact.name (e.g. {@code hello.txt}); falls back to defaultName. */
    public static String artifactName(WorkResult workResult, String defaultName) {
        Objects.requireNonNull(workResult, "workResult");
        Object value = workResult.getMetrics().get("artifactName");
        if (value != null && !value.toString().trim().isEmpty()) {
            return value.toString().trim();
        }
        return defaultName;
    }

    private static String metric(WorkResult workResult, String key, String fallback) {
        Object value = workResult.getMetrics().get(key);
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        return value.toString();
    }
}
