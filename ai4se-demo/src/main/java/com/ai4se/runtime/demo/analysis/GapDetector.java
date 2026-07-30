package com.ai4se.runtime.demo.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gap Detection — CLEAR / ASSUMABLE / BLOCKED.
 * Honest mode: "是否已有…" must not be guessed; UNKNOWN / fuzzy answers stay blocking.
 */
public final class GapDetector {

    private GapDetector() {
    }

    public static GapReport detect(RepositoryContext context, Map<String, String> clarificationAnswers) {
        return detect(context, clarificationAnswers, false);
    }

    /**
     * @param honestUnknownMode if true, record stronger risks when answers are UNKNOWN/fuzzy.
     *        UNKNOWN and fuzzy answers <strong>never</strong> unlock Planning (playbook §2.1 / §2.3).
     */
    public static GapReport detect(
            RepositoryContext context,
            Map<String, String> clarificationAnswers,
            boolean honestUnknownMode) {
        List<String> known = new ArrayList<String>();
        known.add("confidence=" + context.getConfidence());
        known.add("candidate-files=" + context.getCandidateFiles().size());
        for (String m : context.getRelevantModules()) {
            known.add("module:" + m);
        }
        for (String f : context.getCandidateFiles()) {
            known.add("candidate:" + f);
        }

        List<String> unknown = new ArrayList<String>(context.getUnknown());
        List<String> decisionNeeded = new ArrayList<String>();
        List<String> assumptions = new ArrayList<String>();
        List<String> risks = new ArrayList<String>();

        int blocking = 0;
        int assumable = 0;
        int cleared = 0;

        for (String q : context.getNeedClarification()) {
            String answer = findAnswer(q, clarificationAnswers);
            if (answer != null && !answer.trim().isEmpty()) {
                String trimmed = answer.trim();
                assumptions.add("Q: " + q + " → A: " + trimmed);
                if (isUnknownAnswer(trimmed)) {
                    risks.add("Human confirmed UNKNOWN — must not invent: " + q);
                    decisionNeeded.add(q + " [ANSWER=UNKNOWN — still blocking for Planning]");
                    blocking++;
                    if (honestUnknownMode) {
                        risks.add("Honest mode: stop at Clarification until concrete answer for: " + q);
                    }
                } else if (StopCondition.isRefuseAnswer(trimmed)) {
                    risks.add("Human refused answer — STOP path; must not ASSUMABLE: " + q);
                    decisionNeeded.add(q + " [ANSWER=REFUSED — still blocking for Planning]");
                    blocking++;
                } else if (isFuzzyAnswer(trimmed)) {
                    risks.add("Fuzzy human answer cannot close gap — still blocking: " + q);
                    decisionNeeded.add(q + " [ANSWER=FUZZY — still blocking for Planning]");
                    blocking++;
                    if (honestUnknownMode) {
                        risks.add("Honest mode: require verifiable answer (path/rule/yes-no+detail), not: " + trimmed);
                    }
                } else {
                    // Concrete answer closes this blocking gap; recorded as human-provided assumption.
                    assumable++;
                    cleared++;
                    known.add("clarified:" + shortLabel(q));
                }
            } else if (isAssumableWithoutAnswer(q)) {
                String def = defaultAssumption(q);
                assumptions.add(def);
                risks.add("Assumption may be wrong: " + def);
                assumable++;
            } else {
                decisionNeeded.add(q);
                blocking++;
            }
        }

        GapReport.Status status;
        if (blocking > 0) {
            status = GapReport.Status.BLOCKED;
        } else if (assumable > 0) {
            // Human-provided concrete answers still count as ASSUMABLE (audit trail), not silent CLEAR.
            status = GapReport.Status.ASSUMABLE;
        } else {
            status = GapReport.Status.CLEAR;
        }
        if (cleared > 0 && blocking == 0) {
            risks.add("Gap re-check: " + cleared + " question(s) closed by concrete human answers; mayPlan="
                    + (status != GapReport.Status.BLOCKED));
        }

        return new GapReport(
                status,
                known,
                unknown,
                assumptions,
                risks,
                decisionNeeded,
                blocking,
                assumable);
    }

    private static boolean isUnknownAnswer(String answer) {
        String a = answer.toLowerCase(Locale.ROOT);
        return a.startsWith("unknown")
                || a.contains("未知")
                || a.contains("不清楚")
                || a.contains("待确认");
    }

    /**
     * Vague affirmations that look like answers but cannot lawfully close a blocking gap.
     * Example risk: "Promotion 应该已经有了" treated as CLEAR.
     */
    static boolean isFuzzyAnswer(String answer) {
        String a = answer.toLowerCase(Locale.ROOT).trim();
        if (a.isEmpty()) {
            return true;
        }
        // Explicit markers of hedge / guess (Chinese + English).
        if (a.contains("应该有吧")
                || a.contains("应该已经")
                || a.contains("应该有")
                || a.contains("应该兼容")
                || a.startsWith("应该")
                || a.contains("大概")
                || a.contains("好像")
                || a.contains("可能有")
                || a.contains("可能按")
                || a.contains("或许")
                || a.contains("差不多")
                || a.contains("有吧")
                || a.contains("有的吧")
                || a.contains("估计有")
                || a.contains("估计在")
                || a.contains("i think")
                || a.contains("probably")
                || a.contains("maybe")
                || a.contains("might have")
                || a.contains("should already")
                || a.contains("should be there")
                || a.startsWith("should ")) {
            return true;
        }
        // Bare yes/existence without verifiable detail (path, table, rule, greenfield scope).
        if (a.equals("有")
                || a.equals("有的")
                || a.equals("是")
                || a.equals("yes")
                || a.equals("y")
                || a.equals("已经有了")
                || a.equals("已经有")) {
            return true;
        }
        return false;
    }

    private static String shortLabel(String question) {
        if (question == null) {
            return "?";
        }
        return question.length() <= 40 ? question : question.substring(0, 40) + "…";
    }

    private static String findAnswer(String question, Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) {
            return null;
        }
        if (answers.containsKey(question)) {
            return answers.get(question);
        }
        for (Map.Entry<String, String> e : answers.entrySet()) {
            String key = e.getKey();
            if (question.contains(key) || key.length() > 4 && question.contains(key.substring(0, Math.min(8, key.length())))) {
                return e.getValue();
            }
        }
        if (question.contains("timeout") && answers.containsKey("timeout")) {
            return answers.get("timeout");
        }
        if ((question.contains("Promotion 表") || question.contains("Promotion表")) && answers.containsKey("promotion_table")) {
            return answers.get("promotion_table");
        }
        if (question.contains("最高优惠金额") && answers.containsKey("percent_cap")) {
            return answers.get("percent_cap");
        }
        if (question.contains("最高优惠") && answers.containsKey("best_offer_rule")) {
            return answers.get("best_offer_rule");
        }
        if (question.contains("多个活动") && answers.containsKey("multi_promotion")) {
            return answers.get("multi_promotion");
        }
        if (question.contains("折扣") && answers.containsKey("discount_model")) {
            return answers.get("discount_model");
        }
        if (question.contains("Order") && answers.containsKey("order_calc")) {
            return answers.get("order_calc");
        }
        if (question.contains("兼容") && answers.containsKey("compat")) {
            return answers.get("compat");
        }
        return null;
    }

    private static boolean isAssumableWithoutAnswer(String q) {
        // Only narrow timeout-key questions may be assumed without human.
        // Existence of tables/services must NEVER be guessed.
        if (q.contains("是否已有") || q.contains("是否已经") || q.contains("是否支持") || q.contains("是否必须")) {
            return false;
        }
        return (q.contains("timeout") || q.contains("配置项名称")) && !q.contains("是否");
    }

    private static String defaultAssumption(String q) {
        if (q.contains("timeout") || q.contains("配置项")) {
            return "Assume config key app.order.timeout.ms=3000 (timeout pilot default only).";
        }
        return "Assume project defaults apply for: " + q;
    }
}
