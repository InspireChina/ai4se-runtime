package com.ai4se.orchestration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FailureFingerprintTest {

    @Test
    void capturesFullMultiWordFailingCommand() {
        String why = "VERIFY_FAIL exit=1 failing_command=mvn -q test"
                + " per_command=[mvn -q test exit=1 FAIL]"
                + " verdict_basis=entry_exit_codes"
                + " acceptance_scoring=not_performed_all_impacted_via_entry_fail"
                + " stderr_excerpt=AssertionError: expected true";
        FailureFingerprint fp = FailureFingerprint.fromWhyFailed(why);
        assertEquals("mvn -q test", fp.failingEntry);
        assertEquals(1, fp.exitCode);
        assertTrue(fp.logDigest.length() == 64);
    }

    @Test
    void capturesQuotedFailingCommand() {
        String why = "VERIFY_FAIL exit=2 failing_command=\"npm run test -- --ci\""
                + " per_command=[npm run test -- --ci exit=2 FAIL]"
                + " verdict_basis=entry_exit_codes"
                + " stderr_excerpt=failed";
        FailureFingerprint fp = FailureFingerprint.fromWhyFailed(why);
        assertEquals("npm run test -- --ci", fp.failingEntry);
        assertEquals(2, fp.exitCode);
    }

    @Test
    void differentCommandsProduceDifferentFingerprintsEvenIfShareFirstToken() {
        FailureFingerprint a = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=mvn -q test"
                        + " per_command=[x] verdict_basis=y stderr_excerpt=same");
        FailureFingerprint b = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=mvn -q verify"
                        + " per_command=[x] verdict_basis=y stderr_excerpt=same");
        assertNotEquals(a, b);
        assertEquals("mvn -q test", a.failingEntry);
        assertEquals("mvn -q verify", b.failingEntry);
    }

    @Test
    void differentStdoutOnlyFailuresProduceDifferentFingerprints() {
        FailureFingerprint a = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=mvn -q test"
                        + " per_command=[mvn -q test exit=1 FAIL]"
                        + " verdict_basis=entry_exit_codes"
                        + " acceptance_scoring=x"
                        + " stdout_excerpt=Tests run: 1, Failures: 1 AssertionError A");
        FailureFingerprint b = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=mvn -q test"
                        + " per_command=[mvn -q test exit=1 FAIL]"
                        + " verdict_basis=entry_exit_codes"
                        + " acceptance_scoring=x"
                        + " stdout_excerpt=Tests run: 1, Failures: 1 AssertionError B");
        assertEquals("mvn -q test", a.failingEntry);
        assertEquals(1, a.exitCode);
        assertEquals(a.failingEntry, b.failingEntry);
        assertEquals(a.exitCode, b.exitCode);
        assertNotEquals(a.logDigest, b.logDigest);
        assertNotEquals(a, b);
    }

    @Test
    void prefersStderrExcerptOverStdoutWhenBothPresent() {
        FailureFingerprint withStderr = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=true"
                        + " per_command=[true exit=1 FAIL] verdict_basis=y"
                        + " stderr_excerpt=from-stderr stdout_excerpt=from-stdout");
        FailureFingerprint stderrOnly = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=true"
                        + " per_command=[true exit=1 FAIL] verdict_basis=y"
                        + " stderr_excerpt=from-stderr");
        assertEquals(withStderr, stderrOnly);
    }

    @Test
    void emptyLogFallsBackWithoutCollapsingDistinctCommands() {
        FailureFingerprint a = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=mvn -q test"
                        + " per_command=[x] verdict_basis=y");
        FailureFingerprint b = FailureFingerprint.fromWhyFailed(
                "VERIFY_FAIL exit=1 failing_command=mvn -q verify"
                        + " per_command=[x] verdict_basis=y");
        assertNotEquals(a, b);
    }
}
