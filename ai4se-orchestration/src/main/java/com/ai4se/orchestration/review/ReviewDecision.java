package com.ai4se.orchestration.review;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.util.Locale;

/**
 * Machine Review decision tri-state. Only {@link #PASS} may enter automatic Delivery.
 */
public enum ReviewDecision {

    PASS,
    CONDITIONAL,
    REJECT;

    public boolean allowsAutomaticDelivery() {
        return this == PASS;
    }

    /**
     * Normalize human/adapter wording into a machine decision.
     * Order matters: {@code 附条件通过} must not collapse to PASS.
     */
    public static ReviewDecision parse(String raw) {
        if (Strings.isBlank(raw)) {
            throw new StageGateException("Review decision required (PASS/CONDITIONAL/REJECT)");
        }
        String t = raw.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (t.contains("附条件") || lower.contains("conditional") || lower.equals("conditional")
                || "CONDITIONAL".equalsIgnoreCase(t)) {
            return CONDITIONAL;
        }
        if (t.contains("驳回") || lower.contains("reject") || "REJECT".equalsIgnoreCase(t)) {
            return REJECT;
        }
        if (t.contains("通过") || lower.contains("pass") || "PASS".equalsIgnoreCase(t)
                || lower.equals("accept") || t.contains("接受")) {
            // Bare "通过" / pass — but not when already matched 附条件 above.
            if (t.contains("附条件") || lower.contains("conditional")) {
                return CONDITIONAL;
            }
            return PASS;
        }
        throw new StageGateException(
                "Review decision must be PASS/CONDITIONAL/REJECT (通过/附条件/驳回); got: " + t);
    }
}
