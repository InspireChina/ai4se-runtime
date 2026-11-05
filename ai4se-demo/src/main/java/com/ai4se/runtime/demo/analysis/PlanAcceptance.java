package com.ai4se.runtime.demo.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan "## Acceptance" — sole source of Acceptance IDs (Acceptance Provenance).
 * Optional evidence binding: {@code | verify: Class#method} or {@code | verify: file#contains:needle}.
 * Demo/Delivery layer — not Runtime Kernel.
 */
public final class PlanAcceptance {

    private static final Pattern ITEM = Pattern.compile(
            "^\\s*-\\s+\\*\\*(A\\d+)\\*\\*\\s*:\\s*(.+?)\\s*$");
    private static final Pattern ITEM_PLAIN = Pattern.compile(
            "^\\s*-\\s+(A\\d+)\\s*:\\s*(.+?)\\s*$");

    private PlanAcceptance() {
    }

    public static final class Item {
        public final String id;
        public final String criterion;
        /** May be empty — then matrix marks MISSING evidence. */
        public final String verifyRef;

        public Item(String id, String criterion, String verifyRef) {
            this.id = id;
            this.criterion = criterion;
            this.verifyRef = verifyRef == null ? "" : verifyRef;
        }
    }

    /** Ordered items from Plan (ID + criterion + optional verify). */
    public static List<Item> parseItems(String planMarkdown) {
        if (planMarkdown == null || planMarkdown.isEmpty()) {
            return Collections.emptyList();
        }
        String[] lines = planMarkdown.split("\\r?\\n");
        boolean inSection = false;
        List<Item> out = new ArrayList<Item>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                if (inSection) {
                    break;
                }
                inSection = trimmed.equalsIgnoreCase("## Acceptance")
                        || trimmed.toLowerCase().startsWith("## acceptance");
                continue;
            }
            if (!inSection) {
                continue;
            }
            Matcher m = ITEM.matcher(line);
            if (!m.matches()) {
                m = ITEM_PLAIN.matcher(line);
            }
            if (m.matches()) {
                out.add(splitVerify(m.group(1), m.group(2).trim()));
            }
        }
        return out;
    }

    /** @return ordered map id → criterion text (verify suffix stripped) */
    public static Map<String, String> parse(String planMarkdown) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Item item : parseItems(planMarkdown)) {
            out.put(item.id, item.criterion);
        }
        return out;
    }

    /** @return ordered map id → verify ref (only entries that declared verify) */
    public static Map<String, String> parseVerifyRefs(String planMarkdown) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Item item : parseItems(planMarkdown)) {
            if (item.verifyRef != null && !item.verifyRef.isEmpty()) {
                out.put(item.id, item.verifyRef);
            }
        }
        return out;
    }

    public static List<String> ids(Map<String, String> acceptance) {
        return new ArrayList<String>(acceptance.keySet());
    }

    public static List<String> idsFromItems(List<Item> items) {
        List<String> ids = new ArrayList<String>();
        for (Item item : items) {
            ids.add(item.id);
        }
        return ids;
    }

    private static Item splitVerify(String id, String rest) {
        String criterion = rest;
        String verify = "";
        int idx = rest.toLowerCase().indexOf("| verify:");
        if (idx < 0) {
            idx = rest.toLowerCase().indexOf("|verify:");
        }
        if (idx >= 0) {
            criterion = rest.substring(0, idx).trim();
            String tail = rest.substring(idx);
            int colon = tail.indexOf(':');
            if (colon >= 0 && colon + 1 < tail.length()) {
                verify = tail.substring(colon + 1).trim();
            }
        }
        return new Item(id, criterion, verify);
    }
}
