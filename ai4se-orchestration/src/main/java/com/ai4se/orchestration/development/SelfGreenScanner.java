package com.ai4se.orchestration.development;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bans Development self-green claims — Verification owns PASS/FAIL.
 */
public final class SelfGreenScanner {

    private static final String[] BANNED = new String[] {
            "测试已通过",
            "测试通过",
            "可以交付",
            "验收通过",
            "已验绿",
            "verification passed",
            "tests passed",
            "test passed",
            "ready to deliver",
            "acceptance passed",
            "all tests green"
    };

    private SelfGreenScanner() {
    }

    public static List<String> findHits(String text) {
        List<String> hits = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return hits;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String banned : BANNED) {
            String needle = banned.toLowerCase(Locale.ROOT);
            if (lower.contains(needle) || text.contains(banned)) {
                hits.add(banned);
            }
        }
        return hits;
    }
}
