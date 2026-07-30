package com.ai4se.runtime.demo.delivery;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint A follow-up metric: declared targets vs actual patch paths.
 * Demo layer only.
 */
public final class PatchCoverage {

    public final int declaredCount;
    public final int patchedCount;
    public final int coveredCount;
    public final int undeclaredCount;
    public final List<String> undeclaredPaths;
    public final List<String> unpatchedDeclared;

    public PatchCoverage(
            int declaredCount,
            int patchedCount,
            int coveredCount,
            int undeclaredCount,
            List<String> undeclaredPaths,
            List<String> unpatchedDeclared) {
        this.declaredCount = declaredCount;
        this.patchedCount = patchedCount;
        this.coveredCount = coveredCount;
        this.undeclaredCount = undeclaredCount;
        this.undeclaredPaths = undeclaredPaths;
        this.unpatchedDeclared = unpatchedDeclared;
    }

    public static PatchCoverage compute(List<String> declared, Map<String, String> patches) {
        Set<String> declaredSet = new LinkedHashSet<String>();
        if (declared != null) {
            for (String d : declared) {
                declaredSet.add(norm(d));
            }
        }
        Set<String> patchedSet = new LinkedHashSet<String>();
        List<String> undeclared = new java.util.ArrayList<String>();
        if (patches != null) {
            for (String p : patches.keySet()) {
                String n = norm(p);
                patchedSet.add(n);
                if (!declaredSet.contains(n)) {
                    undeclared.add(n);
                }
            }
        }
        List<String> unpatched = new java.util.ArrayList<String>();
        int covered = 0;
        for (String d : declaredSet) {
            if (patchedSet.contains(d)) {
                covered++;
            } else {
                unpatched.add(d);
            }
        }
        return new PatchCoverage(
                declaredSet.size(),
                patchedSet.size(),
                covered,
                undeclared.size(),
                undeclared,
                unpatched);
    }

    public String ratio() {
        return coveredCount + "/" + declaredCount;
    }

    public Map<String, String> toSummaryMap() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("declared", String.valueOf(declaredCount));
        m.put("patched", String.valueOf(patchedCount));
        m.put("coverage", ratio());
        m.put("undeclared", String.valueOf(undeclaredCount));
        return Collections.unmodifiableMap(m);
    }

    private static String norm(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }
}
