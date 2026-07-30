package com.ai4se.runtime.engine.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * S7 — parse plan.test-strategy Artifact body (inline text).
 * Format lines:
 *   command: &lt;shell allowlist command&gt;
 *   acceptance: A1, A2
 */
public final class PlanTestStrategy {

    public final String command;
    public final List<String> acceptanceIds;
    public final boolean skipExplicit;

    PlanTestStrategy(String command, List<String> acceptanceIds, boolean skipExplicit) {
        this.command = command == null ? "" : command.trim();
        this.acceptanceIds = acceptanceIds == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(acceptanceIds));
        this.skipExplicit = skipExplicit;
    }

    public boolean mustRunTests() {
        return !skipExplicit && !command.isEmpty();
    }

    public boolean isSkipOrEmpty() {
        return skipExplicit || command.isEmpty();
    }

    public boolean citesAllAcceptances(String reportText) {
        if (acceptanceIds.isEmpty()) {
            return false;
        }
        String hay = reportText == null ? "" : reportText;
        for (String id : acceptanceIds) {
            if (!hay.contains(id)) {
                return false;
            }
        }
        return true;
    }

    public static PlanTestStrategy parse(String body) {
        if (body == null || body.trim().isEmpty()) {
            return new PlanTestStrategy("", Collections.<String>emptyList(), false);
        }
        String command = "";
        List<String> ids = new ArrayList<String>();
        boolean skip = false;
        String[] lines = body.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if ("command".equals(key) || "verify".equals(key) || "test".equals(key)) {
                command = value;
                String lower = value.toLowerCase(Locale.ROOT);
                if (lower.equals("skip") || lower.equals("none") || lower.equals("n/a")) {
                    skip = true;
                    command = "";
                }
            } else if ("acceptance".equals(key) || "acceptances".equals(key) || "acceptance-ids".equals(key)) {
                for (String part : value.split("[,\\s]+")) {
                    if (!part.isEmpty()) {
                        ids.add(part.trim());
                    }
                }
            }
        }
        return new PlanTestStrategy(command, ids, skip);
    }
}
