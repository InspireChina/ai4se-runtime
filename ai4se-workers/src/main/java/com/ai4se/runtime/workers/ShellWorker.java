package com.ai4se.runtime.workers;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.error.ReasonCode;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.common.util.ShellExecutable;
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
 * Sprint-7 Real Worker — executes allowlisted local process commands.
 * Captures stdout / stderr / exitCode / duration; Runtime never sees ProcessBuilder.
 * No Scheduler / Capability / Claude — Worker SPI only.
 */
public final class ShellWorker implements Worker {

    public static final WorkerId ID = new WorkerId("worker_shell");
    public static final String ARTIFACT_NAME = "shell-stdout.txt";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private final WorkerDescriptor descriptor;

    public ShellWorker() {
        this.descriptor = new WorkerDescriptor(
                ID,
                WorkerKind.PROCESS,
                "ShellWorker",
                "0.1.0",
                Arrays.asList("echo", "pwd", "git.status", "mvn.test"),
                1,
                Collections.singleton("local-shell"),
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
        File workDir = resolveWorkDir(request);
        List<String> argv;
        try {
            argv = toArgv(commandLine, workDir);
        } catch (IllegalArgumentException ex) {
            return fail("SHELL_NOT_ALLOWED", ex.getMessage(), commandLine, -1, "", "", durationMs(started));
        }

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
                return fail("SHELL_TIMEOUT", "command timed out: " + commandLine,
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
                    Optional.of(new ReasonCode("SHELL_EXIT_" + exit)),
                    Optional.of(msg),
                    metrics);
        } catch (Exception ex) {
            return fail("SHELL_EXEC_ERROR", ex.getMessage(), commandLine, -1, "", "", durationMs(started));
        }
    }

    private static Map<String, Object> baseMetrics(
            String commandLine, int exit, String stdout, String stderr, long durationMs, File workDir) {
        Map<String, Object> metrics = new HashMap<String, Object>();
        metrics.put("workerName", "ShellWorker");
        metrics.put("command", commandLine);
        metrics.put("exitCode", Integer.valueOf(exit));
        metrics.put("durationMs", Long.valueOf(durationMs));
        metrics.put("stdout", stdout == null ? "" : stdout);
        metrics.put("stderr", stderr == null ? "" : stderr);
        metrics.put("workdir", workDir.getAbsolutePath());
        metrics.put("artifactName", ARTIFACT_NAME);
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

    /**
     * Allowlist only — real process argv, no shell metacharacters.
     * Supported: {@code echo ...}, {@code pwd}, {@code git status},
     * {@code mvn -f <pom> -q test} where pom resolves under workDir.
     */
    static List<String> toArgv(String commandLine) {
        return toArgv(commandLine, new File(System.getProperty("user.dir")));
    }

    static List<String> toArgv(String commandLine, File workDir) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            throw new IllegalArgumentException("empty command");
        }
        String trimmed = commandLine.trim().replaceAll("\\s+", " ");
        if (trimmed.equals("pwd")) {
            return platformPwd();
        }
        if (trimmed.equals("git status") || trimmed.equals("git.status")) {
            return Arrays.asList(ShellExecutable.resolveCommand("git"), "status");
        }
        if (trimmed.equals("echo") || trimmed.startsWith("echo ")) {
            return platformEcho(trimmed);
        }
        if (trimmed.startsWith("mvn -f ") && trimmed.endsWith(" -q test")) {
            String middle = trimmed.substring("mvn -f ".length(), trimmed.length() - " -q test".length()).trim();
            if (middle.isEmpty() || middle.contains(" ") || middle.contains("..")) {
                throw new IllegalArgumentException("invalid mvn pom path: " + middle);
            }
            try {
                File root = workDir.getCanonicalFile();
                File pom = new File(middle);
                if (!pom.isAbsolute()) {
                    pom = new File(root, middle);
                }
                pom = pom.getCanonicalFile();
                if (!pom.getName().equals("pom.xml")) {
                    throw new IllegalArgumentException("mvn -f target must be pom.xml");
                }
                String pomPath = pom.getPath();
                String rootPath = root.getPath();
                if (!pomPath.startsWith(rootPath + File.separator) && !pomPath.equals(rootPath)) {
                    throw new IllegalArgumentException("mvn pom escapes workdir: " + middle);
                }
                // Windows: bare "mvn" → CreateProcess error=2; resolve mvn.cmd on PATH.
                return Arrays.asList(
                        ShellExecutable.resolveCommand("mvn"),
                        "-f",
                        pom.getAbsolutePath(),
                        "-q",
                        "test");
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("cannot resolve mvn pom: " + ex.getMessage());
            }
        }
        throw new IllegalArgumentException("command not allowlisted: " + commandLine);
    }

    private static List<String> platformPwd() {
        if (ShellExecutable.isWindows()) {
            return Arrays.asList(ShellExecutable.resolveCommand("cmd"), "/c", "cd");
        }
        return Collections.singletonList("/bin/pwd");
    }

    private static List<String> platformEcho(String trimmed) {
        List<String> argv = new ArrayList<String>();
        if (ShellExecutable.isWindows()) {
            argv.add(ShellExecutable.resolveCommand("cmd"));
            argv.add("/c");
            argv.add("echo");
        } else {
            argv.add("/bin/echo");
        }
        if (trimmed.length() > 5) {
            String rest = trimmed.substring(5).trim();
            if (!rest.isEmpty()) {
                argv.addAll(Arrays.asList(rest.split(" ")));
            }
        }
        return argv;
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
        metrics.put("workerName", "ShellWorker");
        metrics.put("command", commandLine == null ? "" : commandLine);
        metrics.put("exitCode", Integer.valueOf(exitCode));
        metrics.put("durationMs", Long.valueOf(durationMs));
        metrics.put("stdout", stdout == null ? "" : stdout);
        metrics.put("stderr", stderr == null ? "" : stderr);
        metrics.put("artifactName", ARTIFACT_NAME);
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
