package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.Strings;

/**
 * Allowed / Declared path line schema for Plan artifacts.
 *
 * <p>Problem class: plan list lines are <strong>products with a grammar</strong>, not free prose.
 * Backticks, trailing notes, quotes are all the same class — reject; do not peel instance-by-instance.
 */
public final class AllowedPathSchema {

    private AllowedPathSchema() {
    }

    /**
     * Validate and normalize one Allowed Files bullet value.
     *
     * @throws StageGateException when the token is not a bare relative path
     */
    public static String requireBareRelativePath(String raw) {
        if (Strings.isBlank(raw)) {
            throw new StageGateException("Allowed path is blank");
        }
        String t = raw.trim();
        if (t.indexOf('`') >= 0) {
            throw new StageGateException(
                    "Allowed path must be bare (no markdown backticks): " + raw);
        }
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            throw new StageGateException(
                    "Allowed path must be bare (no quotes): " + raw);
        }
        // Trailing annotation / prose — same problem class as backticks
        if (t.indexOf(' ') >= 0 || t.indexOf('\t') >= 0) {
            throw new StageGateException(
                    "Allowed path must be a single path token (no trailing notes): " + raw);
        }
        if (t.indexOf('(') >= 0 || t.indexOf('（') >= 0 || t.indexOf('#') >= 0) {
            throw new StageGateException(
                    "Allowed path must not include annotations: " + raw);
        }
        String n = normalizePath(t);
        if (n.isEmpty() || n.startsWith("/") || n.contains("://")) {
            throw new StageGateException("Allowed path must be relative: " + raw);
        }
        if (n.contains("..")) {
            throw new StageGateException("Allowed path must not contain '..': " + raw);
        }
        return n;
    }

    static String normalizePath(String path) {
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }
}
