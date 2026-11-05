package com.ai4se.execution.support;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Routes {@code git} argv to one invoker and everything else (e.g. {@code bash -lc} verify) to another.
 * Enables real local commits while scripting customer test commands in fixtures.
 */
public final class SplitProcessInvoker implements ProcessInvoker {

    private final ProcessInvoker gitInvoker;
    private final ProcessInvoker otherInvoker;
    private final List<List<String>> argvHistory = new ArrayList<List<String>>();

    public SplitProcessInvoker(ProcessInvoker gitInvoker, ProcessInvoker otherInvoker) {
        if (gitInvoker == null || otherInvoker == null) {
            throw new IllegalArgumentException("invokers required");
        }
        this.gitInvoker = gitInvoker;
        this.otherInvoker = otherInvoker;
    }

    public List<List<String>> argvHistory() {
        return argvHistory;
    }

    @Override
    public ProcessOutcome run(
            List<String> argv,
            Path workingDirectory,
            Map<String, String> extraEnv,
            Duration timeout) throws java.io.IOException, InterruptedException {
        argvHistory.add(new ArrayList<String>(argv));
        if (argv != null && !argv.isEmpty() && "git".equals(argv.get(0))) {
            return gitInvoker.run(argv, workingDirectory, extraEnv, timeout);
        }
        return otherInvoker.run(argv, workingDirectory, extraEnv, timeout);
    }
}
