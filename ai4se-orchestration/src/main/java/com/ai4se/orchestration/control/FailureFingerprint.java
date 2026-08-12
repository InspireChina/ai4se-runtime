package com.ai4se.orchestration.control;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.verification.VerificationControl.VerificationRecord;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal failure fingerprint: failing entry + exit code + normalized log digest.
 * Parsed from Defect {@code why_failed} (written by VerificationControl).
 */
public final class FailureFingerprint {

    /** Full command until the next known why_failed field (commands may contain spaces). */
    private static final Pattern FAILING_COMMAND = Pattern.compile(
            "failing_command=(?:\"([^\"]*)\"|(.+?))(?=\\s+per_command=|\\s+verdict_basis="
                    + "|\\s+acceptance_scoring=|\\s+stderr_excerpt=|\\s+stdout_excerpt=|$)");
    private static final Pattern EXIT =
            Pattern.compile("VERIFY_FAIL exit=(-?\\d+)");
    private static final Pattern STDERR_EXCERPT =
            Pattern.compile("stderr_excerpt=(.+?)(?=\\s+stdout_excerpt=|$)");
    private static final Pattern STDOUT_EXCERPT =
            Pattern.compile("stdout_excerpt=(.*)$");

    public final String failingEntry;
    public final int exitCode;
    public final String logDigest;

    public FailureFingerprint(String failingEntry, int exitCode, String logDigest) {
        this.failingEntry = failingEntry == null ? "" : failingEntry.trim();
        this.exitCode = exitCode;
        this.logDigest = logDigest == null ? "" : logDigest.trim();
    }

    public static FailureFingerprint fromDefectFile(Path defectOrNull) throws IOException {
        if (defectOrNull == null || !Files.isRegularFile(defectOrNull)) {
            throw new StageGateException("FailureFingerprint requires Defect Package file");
        }
        String text = new String(Files.readAllBytes(defectOrNull), StandardCharsets.UTF_8);
        String why = "";
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("- why_failed:")) {
                why = t.substring("- why_failed:".length()).trim();
                break;
            }
        }
        if (Strings.isBlank(why)) {
            throw new StageGateException("Defect missing why_failed for fingerprint");
        }
        return fromWhyFailed(why);
    }

    public static FailureFingerprint fromWhyFailed(String whyFailed) {
        String why = whyFailed == null ? "" : whyFailed.trim();
        Matcher cmd = FAILING_COMMAND.matcher(why);
        Matcher exit = EXIT.matcher(why);
        String entry = "unknown-entry";
        if (cmd.find()) {
            entry = cmd.group(1) != null ? cmd.group(1) : cmd.group(2);
            if (entry == null) {
                entry = "unknown-entry";
            }
        }
        int code = exit.find() ? Integer.parseInt(exit.group(1)) : -999;
        String excerpt = extractLogExcerpt(why);
        return new FailureFingerprint(entry, code, sha256Hex(normalizeLog(excerpt)));
    }

    /**
     * Prefer stderr_excerpt; fall back to stdout_excerpt (mvn/npm often fail on stdout only).
     * If neither is present, hash the whole why_failed string (legacy / empty-log cases).
     */
    static String extractLogExcerpt(String why) {
        if (why == null) {
            return "";
        }
        Matcher err = STDERR_EXCERPT.matcher(why);
        if (err.find()) {
            String s = err.group(1).trim();
            if (!Strings.isBlank(s)) {
                return s;
            }
        }
        Matcher out = STDOUT_EXCERPT.matcher(why);
        if (out.find()) {
            String s = out.group(1).trim();
            if (!Strings.isBlank(s)) {
                return s;
            }
        }
        return why;
    }

    public static FailureFingerprint fromVerificationFail(VerificationRecord record) throws IOException {
        if (record == null || record.defectOrNull == null) {
            throw new StageGateException("FAIL record without Defect — cannot fingerprint");
        }
        return fromDefectFile(record.defectOrNull);
    }

    static String normalizeLog(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("\\d{4}-\\d{2}-\\d{2}[t ]\\d{2}:\\d{2}:\\d{2}", "TIMESTAMP")
                .replaceAll("0x[0-9a-f]+", "HEX")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 required", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FailureFingerprint)) {
            return false;
        }
        FailureFingerprint that = (FailureFingerprint) o;
        return exitCode == that.exitCode
                && Objects.equals(failingEntry, that.failingEntry)
                && Objects.equals(logDigest, that.logDigest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failingEntry, exitCode, logDigest);
    }

    @Override
    public String toString() {
        return failingEntry + "|exit=" + exitCode + "|log=" + logDigest;
    }

    /**
     * Parse {@link #toString()} form persisted across process resume.
     *
     * @return null when blank or unparseable
     */
    public static FailureFingerprint parsePersistedOrNull(String persisted) {
        if (Strings.isBlank(persisted)) {
            return null;
        }
        String s = persisted.trim();
        int exitAt = s.lastIndexOf("|exit=");
        int logAt = s.lastIndexOf("|log=");
        if (exitAt < 0 || logAt < 0 || logAt < exitAt) {
            return null;
        }
        String entry = s.substring(0, exitAt);
        String exitPart = s.substring(exitAt + "|exit=".length(), logAt);
        String logPart = s.substring(logAt + "|log=".length());
        try {
            return new FailureFingerprint(entry, Integer.parseInt(exitPart.trim()), logPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
