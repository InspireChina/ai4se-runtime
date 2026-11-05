package com.ai4se.execution.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Regression tests for real stdin/stdout/stderr process handling. */
final class RealProcessInvokerTest {

    @Test
    void closesStdinSoChildObservesEof() throws Exception {
        ProcessInvoker.ProcessOutcome outcome = new ProcessInvoker.RealProcessInvoker().run(
                Arrays.asList("sh", "-c", "if read line; then exit 3; else echo eof; fi"),
                null,
                Collections.<String, String>emptyMap(),
                Duration.ofSeconds(2));

        assertFalse(outcome.timedOut);
        assertTrue(outcome.stdout.contains("eof"), outcome.stdout);
    }

    @Test
    void timeoutReturnsPromptlyAndRetainsPartialOutput() throws Exception {
        long start = System.nanoTime();
        ProcessInvoker.ProcessOutcome outcome = new ProcessInvoker.RealProcessInvoker().run(
                Arrays.asList("sh", "-c", "while :; do echo stdout; echo stderr >&2; done"),
                null,
                Collections.<String, String>emptyMap(),
                Duration.ofMillis(120));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(outcome.timedOut);
        assertTrue(elapsedMillis < 3000L, "timeout took " + elapsedMillis + "ms");
        assertTrue(outcome.stdout.contains("stdout"), "stdout was not retained");
        assertTrue(outcome.stderr.contains("stderr"), "stderr was not retained");
    }

    @Test
    void drainsBothStreamsWithoutDeadlock() throws Exception {
        ProcessInvoker.ProcessOutcome outcome = new ProcessInvoker.RealProcessInvoker().run(
                Arrays.asList("sh", "-c", "i=0; while [ $i -lt 20000 ]; do echo out; echo err >&2; i=$((i+1)); done"),
                null,
                Collections.<String, String>emptyMap(),
                Duration.ofSeconds(5));

        assertFalse(outcome.timedOut);
        assertTrue(outcome.stdout.length() > 10000);
        assertTrue(outcome.stderr.length() > 10000);
    }
}
