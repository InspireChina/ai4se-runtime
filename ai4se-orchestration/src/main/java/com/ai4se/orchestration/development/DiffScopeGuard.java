package com.ai4se.orchestration.development;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Diff path set must be ⊆ Allowed Files. */
public final class DiffScopeGuard {

    private DiffScopeGuard() {
    }

    public static List<String> findViolations(List<String> changed, List<String> allowed) {
        Set<String> allow = new HashSet<String>();
        if (allowed != null) {
            for (String a : allowed) {
                if (a != null) {
                    allow.add(normalize(a));
                }
            }
        }
        List<String> bad = new ArrayList<String>();
        if (changed == null) {
            return bad;
        }
        for (String c : changed) {
            if (c == null) {
                continue;
            }
            String n = normalize(c);
            if (!allow.contains(n) && !coveredByDirectoryAllow(n, allow)) {
                bad.add(c);
            }
        }
        return bad;
    }

    static boolean coveredByDirectoryAllow(String path, Set<String> allow) {
        for (String a : allow) {
            if (a.endsWith("/") && path.startsWith(a)) {
                return true;
            }
            if (!a.contains(".") && path.startsWith(a + "/")) {
                return true;
            }
        }
        return false;
    }

    /** Path semantics only — Allowed tokens must already pass {@code AllowedPathSchema}. */
    static String normalize(String path) {
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }
}
