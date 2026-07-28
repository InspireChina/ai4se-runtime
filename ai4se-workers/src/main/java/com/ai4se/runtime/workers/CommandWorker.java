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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Local ProcessBuilder worker. No Claude/Codex awareness — executes allowlisted commands only.
 * Captures stdout, stderr, exitCode, duration; Runtime never sees ProcessBuilder.
 */
public final class CommandWorker implements Worker {

    public static final WorkerId ID = new WorkerId("worker_command");
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private final WorkerDescriptor descriptor;

    public CommandWorker() {
        this.descriptor = new WorkerDescriptor(
                ID,
                WorkerKind.PROCESS,
                "CommandWorker",
                "0.1.0",
                Arrays.asList("echo", "pwd", "git.status"),
                1,
                Collections.singleton("local-cli"),
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
    public WorkResult execute(WorkRequest request, ExecutionContextView context) {
        if (request == null || context == null) {
            throw new IllegalArgumentException("request and context required");
        }
        if (!request.getTaskId().equals(context.taskId())) {
            throw new IllegalArgumentException("WorkRequest.taskId must match ExecutionContext.taskId");
        }

        String commandLine = resolveCommandLine(request);
        long started = System.nanoTime();
        List<String> argv;
        try {
            argv = toArgv(commandLine);
        } catch (IllegalArgumentException ex) {
            return fail("COMMAND_NOT_ALLOWED", ex.getMessage(), commandLine, -1, "", "", durationMs(started));
        }

        File workDir = resolveWorkDir(request);
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.directory(workDir);
            Process process = pb.start();
            String stdout = readFully(process.getInputStream());
            String stderr = readFully(process.getErrorStream());
            boolean finished = process.waitFor(timeoutSeconds(request), TimeUnit.SECONDS);
            long duration = durationMs(started);
            if (!finished) {
                process.destroyForcibly();
                return fail("COMMAND_TIMEOUT", "command timed out: " + commandLine,
                        commandLine, -1, stdout, stderr, duration);
            }
            int exit = process.exitValue();
            Map<String, Object> metrics = baseMetrics(commandLine, exit, stdout, stderr, duration, workDir);
            if (exit == 0) {
                return new WorkResult(
                        WorkResultStatus.OK,
                        Collections2.<ArtifactId>emptyList(),
                        Optional.<ReasonCode>empty(),
                        Optional.of(stdout),
                        metrics);
            }
            String msg = stderr.isEmpty() ? (stdout.isEmpty() ? "exitCode=" + exit : stdout) : stderr;
            return new WorkResult(
                    WorkResultStatus.FATAL_FAIL,
                    Collections2.<ArtifactId>emptyList(),
                    Optional.of(new ReasonCode("COMMAND_EXIT_" + exit)),
                    Optional.of(msg),
                    metrics);
        } catch (Exception ex) {
            return fail("COMMAND_EXEC_ERROR", ex.getMessage(), commandLine, -1, "", "", durationMs(started));
        }
    }

    private static Map<String, Object> baseMetrics(
            String commandLine, int exit, String stdout, String stderr, long durationMs, File workDir) {
        Map<String, Object> metrics = new HashMap<String, Object>();
        metrics.put("workerName", "CommandWorker");
        metrics.put("command", commandLine);
        metrics.put("exitCode", Integer.valueOf(exit));
        metrics.put("durationMs", Long.valueOf(durationMs));
        metrics.put("stdout", stdout == null ? "" : stdout);
        metrics.put("stderr", stderr == null ? "" : stderr);
        metrics.put("workdir", workDir.getAbsolutePath());
        return metrics;
    }

    private static String resolveCommandLine(WorkRequest request) {
        Object fromParams = request.getParams().get("command");
        if (fromParams != null && !fromParams.toString().trim().isEmpty()) {
            return fromParams.toString().trim();
        }
        return request.getOperation() == null ? "" : request.getOperation().trim();
    }

    private static File resolveWorkDir(WorkRequest request) {
        Object workdir = request.getParams().get("workdir");
        if (workdir != null && !workdir.toString().trim().isEmpty()) {
            return new File(workdir.toString());
        }
        return new File(System.getProperty("user.dir"));
    }

    private static long timeoutSeconds(WorkRequest request) {
        if (request.getTimeout() != null && !request.getTimeout().isZero()) {
            return Math.max(1L, request.getTimeout().getSeconds());
        }
        return DEFAULT_TIMEOUT_SECONDS;
    }

    private static long durationMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1000000L;
    }

    /** Allowlist: {@code echo ...}, {@code pwd}, {@code git status}. */
    static List<String> toArgv(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            throw new IllegalArgumentException("empty command");
        }
        String trimmed = commandLine.trim().replaceAll("\\s+", " ");
        if (trimmed.equals("pwd")) {
            return Collections.singletonList("/bin/pwd");
        }
        if (trimmed.equals("git status") || trimmed.equals("git.status")) {
            return Arrays.asList("git", "status");
        }
        if (trimmed.equals("echo") || trimmed.startsWith("echo ")) {
            List<String> argv = new ArrayList<String>();
            argv.add("/bin/echo");
            if (trimmed.length() > 5) {
                String rest = trimmed.substring(5).trim();
                if (!rest.isEmpty()) {
                    argv.addAll(Arrays.asList(rest.split(" ")));
                }
            }
            return argv;
        }
        throw new IllegalArgumentException("command not allowlisted: " + commandLine);
    }

    private static WorkResult fail(
            String reason,
            String message,
            String commandLine,
            int exitCode,
            String stdout,
            String stderr,
            long durationMs) {
        Map<String, Object> metrics = new HashMap<String, Object>();
        metrics.put("workerName", "CommandWorker");
        metrics.put("command", commandLine == null ? "" : commandLine);
        metrics.put("exitCode", Integer.valueOf(exitCode));
        metrics.put("durationMs", Long.valueOf(durationMs));
        metrics.put("stdout", stdout == null ? "" : stdout);
        metrics.put("stderr", stderr == null ? "" : stderr);
        return new WorkResult(
                WorkResultStatus.FATAL_FAIL,
                Collections2.<ArtifactId>emptyList(),
                Optional.of(new ReasonCode(reason)),
                Optional.of(message == null ? reason : message),
                metrics);
    }

    private static String readFully(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), Charset.defaultCharset());
    }
}
