package com.ai4se.context.packagebuild;

import com.ai4se.context.story.StoryRequirement;
import com.ai4se.runtime.common.util.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * W2 P1 gate: Analysis Package must have usable Acceptance.
 * Empty / placeholder / 「看着办」 ⇒ refuse (do not open CLI).
 */
public final class AcceptanceGate {

    private AcceptanceGate() {
    }

    public static void requireUsable(StoryRequirement requirement) {
        List<String> reasons = validate(requirement);
        if (!reasons.isEmpty()) {
            throw new PackageRefuseException("P1 Acceptance missing or unusable: " + join(reasons));
        }
    }

    public static List<String> validate(StoryRequirement requirement) {
        List<String> reasons = new ArrayList<String>();
        List<String> items = requirement.acceptance();
        if (items == null || items.isEmpty()) {
            reasons.add("acceptance list is empty");
            return reasons;
        }
        int usable = 0;
        for (String item : items) {
            if (!isPlaceholder(item)) {
                usable++;
            }
        }
        if (usable == 0) {
            reasons.add("no usable acceptance item");
            for (String item : items) {
                if (isPlaceholder(item)) {
                    reasons.add("placeholder acceptance: " + item);
                }
            }
        }
        return reasons;
    }

    static boolean isPlaceholder(String item) {
        if (Strings.isBlank(item)) {
            return true;
        }
        String t = item.trim().toLowerCase(Locale.ROOT);
        if (t.contains("看着办") || t.contains("随意") || t.contains("tbd") || t.contains("todo")) {
            return true;
        }
        if (t.startsWith("（") || t.startsWith("(")) {
            return true;
        }
        if ("可检验的通过条件".equals(item.trim())
                || "可检验的通过条件；可测或可人工勾选".equals(item.trim())
                || item.trim().startsWith("可检验的通过条件")) {
            return true;
        }
        return false;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
