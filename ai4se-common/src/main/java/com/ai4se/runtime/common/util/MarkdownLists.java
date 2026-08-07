package com.ai4se.runtime.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Extract a bullet-style (or bare-line) list under a {@code ## } markdown header.
 * Shared primitive — do not re-write this scan per caller.
 * <p>
 * Problem class: Contract/prompt may require bare relative paths; models may omit {@code -}/{@code *}
 * markers. Section membership is defined by the {@code ##} header, not by list-marker presence.
 * Markers are optional and stripped when present.
 */
public final class MarkdownLists {

    private MarkdownLists() {
    }

    /**
     * @param headerMatches tested against the lower-cased {@code ## ...} header text
     * @param itemMapper applied to each candidate line (optional bullet marker already stripped);
     *                   may throw to fail loud; return null/blank to skip a line
     */
    public static List<String> extractSection(
            String text, Predicate<String> headerMatches, Function<String, String> itemMapper) {
        if (headerMatches == null || itemMapper == null) {
            throw new IllegalArgumentException("headerMatches and itemMapper required");
        }
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        boolean inSection = false;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.startsWith("## ")) {
                inSection = headerMatches.test(t.toLowerCase(Locale.ROOT));
                continue;
            }
            if (!inSection || t.isEmpty()) {
                continue;
            }
            String candidate = stripListMarker(t);
            String mapped = itemMapper.apply(candidate);
            if (mapped != null) {
                String m = mapped.trim();
                if (!m.isEmpty()) {
                    out.add(m);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Optional {@code - } / {@code * } prefix — bare lines stay as-is. */
    static String stripListMarker(String t) {
        if (t == null) {
            return "";
        }
        if (t.startsWith("- ") || t.startsWith("* ")) {
            return t.substring(2).trim();
        }
        return t;
    }
}
