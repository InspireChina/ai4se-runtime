package com.ai4se.runtime.demo.delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provenance Gate: Plan.ids == Matrix.ids == Review.ids == Delivery.ids.
 * Demo/Delivery layer — not Runtime Kernel. Not JaCoCo / not Acceptance Graph.
 */
public final class AcceptanceProvenanceGate {

    private static final Pattern ID_TOKEN = Pattern.compile("\\b(A\\d+)\\b");
    private static final Pattern PROVENANCE_SECTION = Pattern.compile(
            "##\\s+Acceptance ID set \\(provenance\\)([\\s\\S]*?)(?=\\n##\\s|\\z)");

    private AcceptanceProvenanceGate() {
    }

    public static final class Result {
        public final boolean pass;
        public final List<String> planIds;
        public final List<String> matrixIds;
        public final List<String> reviewIds;
        public final List<String> deliveryIds;
        public final String message;

        public Result(
                boolean pass,
                List<String> planIds,
                List<String> matrixIds,
                List<String> reviewIds,
                List<String> deliveryIds,
                String message) {
            this.pass = pass;
            this.planIds = planIds;
            this.matrixIds = matrixIds;
            this.reviewIds = reviewIds;
            this.deliveryIds = deliveryIds;
            this.message = message;
        }
    }

    public static Result check(
            List<String> planIds,
            List<String> matrixIds,
            List<String> reviewIds,
            List<String> deliveryIds) {
        List<String> p = copy(planIds);
        List<String> m = copy(matrixIds);
        List<String> r = copy(reviewIds);
        List<String> d = copy(deliveryIds);
        if (p.isEmpty()) {
            return new Result(false, p, m, r, d, "Plan Acceptance ID set is empty");
        }
        if (!sameSet(p, m)) {
            return new Result(false, p, m, r, d, "Plan IDs != Matrix IDs: plan=" + p + " matrix=" + m);
        }
        if (!sameSet(p, r)) {
            return new Result(false, p, m, r, d, "Plan IDs != Review IDs: plan=" + p + " review=" + r);
        }
        if (!sameSet(p, d)) {
            return new Result(false, p, m, r, d, "Plan IDs != Delivery IDs: plan=" + p + " delivery=" + d);
        }
        return new Result(true, p, m, r, d, "Plan=Matrix=Review=Delivery ID sets equal: " + p);
    }

    /** Prefer explicit provenance section; else collect A# tokens (may be noisy — tests use section). */
    public static List<String> extractProvenanceIds(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return Collections.emptyList();
        }
        Matcher section = PROVENANCE_SECTION.matcher(markdown);
        if (section.find()) {
            return extractIdsOrdered(section.group(1));
        }
        return Collections.emptyList();
    }

    /** Fail if markdown invents Acceptance IDs not in plan (Review/Delivery discipline). */
    public static List<String> findInventedIds(String markdown, List<String> planIds) {
        Set<String> allowed = new LinkedHashSet<String>(copy(planIds));
        List<String> invented = new ArrayList<String>();
        for (String id : extractIdsOrdered(markdown)) {
            if (!allowed.contains(id) && !invented.contains(id)) {
                invented.add(id);
            }
        }
        return invented;
    }

    public static String renderIdSetSection(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Acceptance ID set (provenance)\n\n");
        sb.append("Consumers must not add IDs. Source = Plan only.\n\n");
        for (String id : copy(ids)) {
            sb.append("- ").append(id).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    private static List<String> extractIdsOrdered(String text) {
        List<String> ids = new ArrayList<String>();
        Matcher m = ID_TOKEN.matcher(text == null ? "" : text);
        while (m.find()) {
            String id = m.group(1);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static boolean sameSet(List<String> a, List<String> b) {
        Set<String> sa = new LinkedHashSet<String>(a);
        Set<String> sb = new LinkedHashSet<String>(b);
        return sa.equals(sb);
    }

    private static List<String> copy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }
}
