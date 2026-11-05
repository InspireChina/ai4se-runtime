package com.ai4se.runtime.workers;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.error.ReasonCode;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.WorkResultStatus;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.worker.api.WorkerDescriptor;
import com.ai4se.runtime.worker.api.WorkerHealth;
import com.ai4se.runtime.worker.api.WorkerKind;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes text files under the Task workspace (relative paths only).
 * Used for First Production Delivery plan / patch / report files.
 * No Kernel / Runtime dependency.
 */
public final class FileEditWorker implements Worker {

    public static final WorkerId ID = new WorkerId("worker_file_edit");
    public static final String ARTIFACT_NAME = "file-edit-manifest.txt";

    private final WorkerDescriptor descriptor;

    public FileEditWorker() {
        this.descriptor = new WorkerDescriptor(
                ID,
                WorkerKind.CUSTOM,
                "FileEditWorker",
                "0.1.0",
                Collections.singletonList("file.edit"),
                1,
                Collections.singleton("local-fs"),
                true);
    }

    @Override
    public WorkerId workerId() {
        return ID;
    }

    @Override
    public WorkerDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public WorkerHealth health() {
        return WorkerHealth.UP;
    }

    @Override
    @SuppressWarnings("unchecked")
    public WorkResult execute(WorkRequest request, ExecutionContextView context) {
        if (request == null || context == null) {
            throw new IllegalArgumentException("request and context required");
        }
        if (!request.getTaskId().equals(context.taskId())) {
            throw new IllegalArgumentException("WorkRequest.taskId must match ExecutionContext.taskId");
        }

        long started = System.nanoTime();
        File workDir = resolveWorkDir(request);
        Object rawFiles = request.getParams().get("files");
        if (!(rawFiles instanceof Map)) {
            return fail("FILE_EDIT_BAD_PARAMS", "params.files must be a Map<path,content>", started);
        }
        Map<?, ?> files = (Map<?, ?>) rawFiles;
        if (files.isEmpty()) {
            return fail("FILE_EDIT_EMPTY", "params.files is empty", started);
        }

        List<String> written = new ArrayList<String>();
        try {
            File canonicalRoot = workDir.getCanonicalFile();
            for (Map.Entry<?, ?> entry : files.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    return fail("FILE_EDIT_BAD_ENTRY", "null path or content", started);
                }
                String relative = entry.getKey().toString().trim();
                if (relative.isEmpty() || relative.startsWith("/") || relative.contains("..")) {
                    return fail("FILE_EDIT_PATH_REJECTED", "unsafe path: " + relative, started);
                }
                File target = new File(canonicalRoot, relative).getCanonicalFile();
                if (!target.getPath().startsWith(canonicalRoot.getPath() + File.separator)
                        && !target.getPath().equals(canonicalRoot.getPath())) {
                    return fail("FILE_EDIT_PATH_ESCAPE", "path escapes workspace: " + relative, started);
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return fail("FILE_EDIT_MKDIR", "cannot create dirs for " + relative, started);
                }
                Writer writer = new OutputStreamWriter(
                        Files.newOutputStream(target.toPath()), Charset.forName("UTF-8"));
                try {
                    writer.write(entry.getValue().toString());
                } finally {
                    writer.close();
                }
                written.add(relative);
            }
        } catch (Exception ex) {
            return fail("FILE_EDIT_IO", ex.getMessage(), started);
        }

        StringBuilder manifest = new StringBuilder();
        manifest.append("written=").append(written.size()).append('\n');
        for (String path : written) {
            manifest.append(path).append('\n');
        }
        Map<String, Object> metrics = new HashMap<String, Object>();
        metrics.put("workerName", "FileEditWorker");
        metrics.put("command", "file.edit");
        metrics.put("exitCode", Integer.valueOf(0));
        metrics.put("durationMs", Long.valueOf(durationMs(started)));
        metrics.put("stdout", manifest.toString());
        metrics.put("stderr", "");
        metrics.put("workdir", workDir.getAbsolutePath());
        metrics.put("artifactName", ARTIFACT_NAME);
        metrics.put("writtenFiles", written.toString());
        return new WorkResult(
                WorkResultStatus.OK,
                Collections2.<ArtifactId>emptyList(),
                Optional.<ReasonCode>empty(),
                Optional.of("wrote " + written.size() + " file(s)"),
                metrics);
    }

    private static File resolveWorkDir(WorkRequest request) {
        Object workdir = request.getParams().get("workdir");
        if (workdir != null && !workdir.toString().trim().isEmpty()) {
            return new File(workdir.toString());
        }
        return new File(System.getProperty("user.dir"));
    }

    private static long durationMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1000000L;
    }

    private static WorkResult fail(String reason, String message, long startedNanos) {
        Map<String, Object> metrics = new HashMap<String, Object>();
        metrics.put("workerName", "FileEditWorker");
        metrics.put("command", "file.edit");
        metrics.put("exitCode", Integer.valueOf(-1));
        metrics.put("durationMs", Long.valueOf(durationMs(startedNanos)));
        metrics.put("stdout", "");
        metrics.put("stderr", message == null ? reason : message);
        metrics.put("artifactName", ARTIFACT_NAME);
        return new WorkResult(
                WorkResultStatus.FATAL_FAIL,
                Collections2.<ArtifactId>emptyList(),
                Optional.of(new ReasonCode(reason)),
                Optional.of(message == null ? reason : message),
                metrics);
    }
}
