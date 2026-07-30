package com.ai4se.runtime.demo.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint A gate: Execution patch paths must be ⊆ Plan "Declared modification targets".
 * Demo/Delivery layer only — not Runtime Kernel.
 */
public final class PlanDeclaredTargets {

    private static final Pattern TARGET_LINE = Pattern.compile("^\\s*-\\s+`([^`]+)`\\s*$");

    private PlanDeclaredTargets() {
    }

    public static List<String> parse(String planMarkdown) {
        if (planMarkdown == null || planMarkdown.isEmpty()) {
            return Collections.emptyList();
        }
        String[] lines = planMarkdown.split("\\r?\\n");
        boolean inSection = false;
        List<String> targets = new ArrayList<String>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                inSection = trimmed.equalsIgnoreCase("## Declared modification targets")
                        || trimmed.toLowerCase().contains("declared modification targets");
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (trimmed.startsWith("## ")) {
                break;
            }
            Matcher m = TARGET_LINE.matcher(line);
            if (m.matches()) {
                targets.add(m.group(1).replace('\\', '/'));
            }
        }
        return targets;
    }

    /**
     * @return empty if all patch keys are allowed; otherwise list of offending paths
     */
    public static List<String> findUndeclaredPatches(List<String> declared, Map<String, String> patches) {
        Set<String> allowed = new LinkedHashSet<String>();
        if (declared != null) {
            for (String d : declared) {
                allowed.add(normalize(d));
            }
        }
        List<String> bad = new ArrayList<String>();
        if (patches == null) {
            return bad;
        }
        for (String key : patches.keySet()) {
            String n = normalize(key);
            if (!allowed.contains(n)) {
                bad.add(n);
            }
        }
        return bad;
    }

    private static String normalize(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }
}
