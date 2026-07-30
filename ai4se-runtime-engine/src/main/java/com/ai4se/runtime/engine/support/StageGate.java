package com.ai4se.runtime.engine.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * S5/S6 StageGate — Engine support only (not Kernel Domain, not Workflow DSL).
 * Worker cannot disable this table.
 */
public final class StageGate {

    public static final String KIND_HIT_SET = "discovery.hit-set";
    public static final String KIND_PLAN_DESIGN = "plan.design";
    public static final String KIND_PLAN_TEST = "plan.test-strategy";
    public static final String KIND_PLAN_APPROVED = "plan.approved";
    public static final String KIND_CLARIFY_Q = "clarification.questionnaire";

    public static final class Result {
        public final boolean ok;
        public final List<String> missing;

        Result(boolean ok, List<String> missing) {
            this.ok = ok;
            this.missing = missing;
        }
    }

    private StageGate() {
    }

    /** v0 static table: stage/goal type → required COMMITTED artifact kinds. */
    public static List<String> requiredKinds(String goalType) {
        if (goalType == null || goalType.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String t = normalize(goalType);
        if ("PLAN".equals(t) || "PLANNING".equals(t) || "PLAN_DESIGN".equals(t)) {
            return Collections.singletonList(KIND_HIT_SET);
        }
        if ("PLAN_TEST".equals(t)) {
            return Collections.singletonList(KIND_PLAN_DESIGN);
        }
        if ("EXECUTION".equals(t) || "CODING".equals(t) || "CODE".equals(t)) {
            return Collections.unmodifiableList(Arrays.asList(KIND_PLAN_APPROVED, KIND_PLAN_TEST));
        }
        if ("VERIFY".equals(t) || "VERIFICATION".equals(t) || "TEST".equals(t)) {
            return Collections.singletonList(KIND_PLAN_TEST);
        }
        return Collections.emptyList();
    }

    /**
     * Artifact kind written on Worker OK for this goal type (S6 dual-product kinds).
     * Coding stages must not emit plan.test-strategy (编码不能改测试方案).
     */
    public static String outputKind(String goalType) {
        String t = normalize(goalType);
        if ("PLAN".equals(t) || "PLANNING".equals(t) || "PLAN_DESIGN".equals(t)) {
            return KIND_PLAN_DESIGN;
        }
        if ("PLAN_TEST".equals(t)) {
            return KIND_PLAN_TEST;
        }
        if ("VERIFY".equals(t) || "VERIFICATION".equals(t) || "TEST".equals(t)) {
            return "verify.report";
        }
        if ("DISCOVERY".equals(t) || "DISCOVER".equals(t) || "MAP_SEARCH".equals(t)) {
            return KIND_HIT_SET;
        }
        return "worker.output";
    }

    public static boolean isVerifyGoal(String goalType) {
        String t = normalize(goalType);
        return "VERIFY".equals(t) || "VERIFICATION".equals(t) || "TEST".equals(t);
    }

    public static boolean isDiscoveryGoal(String goalType) {
        String t = normalize(goalType);
        return "DISCOVERY".equals(t) || "DISCOVER".equals(t) || "MAP_SEARCH".equals(t);
    }

    public static Result check(String goalType, Set<String> presentCommittedKinds) {
        List<String> required = requiredKinds(goalType);
        if (required.isEmpty()) {
            return new Result(true, Collections.<String>emptyList());
        }
        Set<String> present = presentCommittedKinds == null
                ? Collections.<String>emptySet()
                : presentCommittedKinds;
        List<String> missing = new ArrayList<String>();
        for (String kind : required) {
            if (!present.contains(kind)) {
                missing.add(kind);
            }
        }
        return new Result(missing.isEmpty(), missing);
    }

    public static Set<String> newKindSet() {
        return new LinkedHashSet<String>();
    }

    private static String normalize(String goalType) {
        return goalType == null ? "" : goalType.trim().toUpperCase(Locale.ROOT);
    }
}
