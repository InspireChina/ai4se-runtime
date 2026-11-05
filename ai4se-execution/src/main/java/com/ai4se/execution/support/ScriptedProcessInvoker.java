package com.ai4se.execution.support;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Scripted invoker for tests — records argv, returns a fixed outcome, never retries. */
public final class ScriptedProcessInvoker implements ProcessInvoker {

    private final List<List<String>> argvHistory = new ArrayList<List<String>>();
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timedOut;

    public ScriptedProcessInvoker(int exitCode, String stdout, String stderr, boolean timedOut) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.timedOut = timedOut;
    }

    public List<List<String>> argvHistory() {
        return argvHistory;
    }

    @Override
    public ProcessOutcome run(
            List<String> argv,
            Path workingDirectory,
            Map<String, String> extraEnv,
            Duration timeout) {
        argvHistory.add(new ArrayList<String>(argv));
        return new ProcessOutcome(exitCode, stdout, stderr, timedOut);
    }
}
