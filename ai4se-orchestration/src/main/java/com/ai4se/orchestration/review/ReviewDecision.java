package com.ai4se.orchestration.review;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.util.Locale;

/**
 * Machine Review decision tri-state. Only {@link #PASS} may enter automatic Delivery.
 * <p>
 * Sidecar values must be exact {@code PASS|CONDITIONAL|REJECT}. Human/Markdown tokens are
 * parsed only from an explicit decision field via {@link #parseMarkdownDecision(String)} —
 * never by scanning prose for substrings like {@code pass}.
 */
public enum ReviewDecision {

    PASS,
    CONDITIONAL,
    REJECT;

    public boolean allowsAutomaticDelivery() {
        return this == PASS;
    }

    /**
     * Strict machine sidecar parsing. Only exact enum names (case-insensitive, trimmed).
     * Illegal values such as {@code NOT PASS}, {@code BYPASS}, {@code PASS WITH CONDITIONS}
     * become {@code FAILED_ADAPTER}.
     */
    public static ReviewDecision parseStrict(String raw) {
        if (Strings.isBlank(raw)) {
            throw failedAdapter("Review decision required (PASS|CONDITIONAL|REJECT)");
        }
        String t = raw.trim();
        if ("PASS".equalsIgnoreCase(t)) {
            return PASS;
        }
        if ("CONDITIONAL".equalsIgnoreCase(t)) {
            return CONDITIONAL;
        }
        if ("REJECT".equalsIgnoreCase(t)) {
            return REJECT;
        }
        throw failedAdapter(
                "Review sidecar decision must be exact PASS|CONDITIONAL|REJECT; got: " + t);
    }

    /**
     * Parse a token taken only from {@code decision:} / {@code ## decision} (not full-doc scan).
     * Allows exact enum names plus a closed set of Chinese Review phrases.
     */
    public static ReviewDecision parseMarkdownDecision(String raw) {
        if (Strings.isBlank(raw)) {
            throw failedAdapter("Review decision required (PASS|CONDITIONAL|REJECT)");
        }
        String t = stripMarkdownEmphasis(raw.trim());
        // Exact machine enums first.
        if ("PASS".equalsIgnoreCase(t)
                || "CONDITIONAL".equalsIgnoreCase(t)
                || "REJECT".equalsIgnoreCase(t)) {
            return parseStrict(t);
        }
        String lower = t.toLowerCase(Locale.ROOT);
        // Closed phrase set — whole-token only (no contains("pass")).
        if ("附条件通过".equals(t) || "附条件".equals(t) || "有条件通过".equals(t)
                || "conditional".equals(lower)) {
            return CONDITIONAL;
        }
        if ("驳回".equals(t) || "拒绝".equals(t) || "reject".equals(lower)) {
            return REJECT;
        }
        if ("通过".equals(t) || "接受".equals(t) || "pass".equals(lower)) {
            return PASS;
        }
        throw failedAdapter(
                "Review Markdown decision must be PASS|CONDITIONAL|REJECT "
                        + "(or 通过/附条件/附条件通过/驳回); got: " + t);
    }

    /**
     * @deprecated use {@link #parseStrict} for sidecar or {@link #parseMarkdownDecision} for
     *     explicit Markdown decision tokens. Kept as alias of markdown path for fixture writers.
     */
    @Deprecated
    public static ReviewDecision parse(String raw) {
        return parseMarkdownDecision(raw);
    }

    private static StageGateException failedAdapter(String message) {
        String m = message == null ? "invalid Review decision" : message;
        if (m.startsWith("FAILED_ADAPTER:")) {
            return new StageGateException(m);
        }
        return new StageGateException("FAILED_ADAPTER: " + m);
    }

    private static String stripMarkdownEmphasis(String s) {
        String t = s.trim();
        while (t.startsWith("*") || t.startsWith("_")) {
            t = t.substring(1);
        }
        while (t.endsWith("*") || t.endsWith("_")) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim();
    }
}
