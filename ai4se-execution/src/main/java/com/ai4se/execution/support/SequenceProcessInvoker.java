package com.ai4se.execution.support;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test invoker that returns scripted outcomes in call order.
 * Use when a flow runs git then customer command then git again.
 */
public final class SequenceProcessInvoker implements ProcessInvoker {

    private final List<ProcessOutcome> outcomes;
    private final List<List<String>> argvHistory = new ArrayList<List<String>>();
    private int index;

    public SequenceProcessInvoker(ProcessOutcome... outcomes) {
        this.outcomes = new ArrayList<ProcessOutcome>();
        if (outcomes != null) {
            for (ProcessOutcome o : outcomes) {
                this.outcomes.add(o);
            }
        }
    }

    public static ProcessOutcome ok(String stdout) {
        return new ProcessOutcome(0, stdout, "", false);
    }

    public static ProcessOutcome exit(int code, String stdout, String stderr) {
        return new ProcessOutcome(code, stdout, stderr, false);
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
        if (index >= outcomes.size()) {
            return new ProcessOutcome(1, "", "no more scripted outcomes", false);
        }
        return outcomes.get(index++);
    }
}
