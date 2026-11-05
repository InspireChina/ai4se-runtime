package com.ai4se.runtime.demo.analysis;

/**
 * Playbook §2.3 Stop Condition — Demo/Analysis 层（≠ Runtime S4 Human-Wait）。
 */
public final class StopCondition {

    public enum Decision {
        CONTINUE,
        STOP_HUMAN
    }

    public enum Reason {
        NONE,
        TWO_CONSECUTIVE_BLOCKED,
        EXCEEDED_THREE_ROUNDS,
        USER_REFUSED
    }

    public static final class Result {
        public final Decision decision;
        public final Reason reason;
        public final String message;

        Result(Decision decision, Reason reason, String message) {
            this.decision = decision;
            this.reason = reason;
            this.message = message;
        }

        public boolean isStop() {
            return decision == Decision.STOP_HUMAN;
        }
    }

    private StopCondition() {
    }

    /**
     * @param clarificationRound 本需求澄清轮次（从 1 起）
     * @param gapBlocked         当前 Gap 为 BLOCKED
     * @param previousRoundBlocked 上一轮亦为 BLOCKED（连续两轮规则）
     * @param userRefused        用户明确拒绝回答
     */
    public static Result evaluate(
            int clarificationRound,
            boolean gapBlocked,
            boolean previousRoundBlocked,
            boolean userRefused) {
        if (userRefused) {
            return new Result(Decision.STOP_HUMAN, Reason.USER_REFUSED,
                    "停止 — 需要人工决策（用户拒绝澄清）。");
        }
        if (clarificationRound > 3 && gapBlocked) {
            return new Result(Decision.STOP_HUMAN, Reason.EXCEEDED_THREE_ROUNDS,
                    "停止 — 需要人工决策（澄清超过 3 轮仍为 BLOCKED）。");
        }
        if (clarificationRound >= 2 && gapBlocked && previousRoundBlocked) {
            return new Result(Decision.STOP_HUMAN, Reason.TWO_CONSECUTIVE_BLOCKED,
                    "停止 — 需要人工决策（连续两轮仍为 BLOCKED）。");
        }
        return new Result(Decision.CONTINUE, Reason.NONE, "继续澄清 / Gap 重算。");
    }

    public static boolean isRefuseAnswer(String answer) {
        if (answer == null) {
            return false;
        }
        String a = answer.trim().toLowerCase();
        return a.equals("拒绝回答")
                || a.equals("refuse")
                || a.startsWith("i refuse")
                || a.contains("拒绝回答");
    }

    public static String reasonLabelZh(Reason reason) {
        switch (reason) {
            case TWO_CONSECUTIVE_BLOCKED:
                return "连续两轮仍阻塞";
            case EXCEEDED_THREE_ROUNDS:
                return "超过三轮仍阻塞";
            case USER_REFUSED:
                return "用户拒绝回答";
            case NONE:
            default:
                return "无";
        }
    }

    public static String decisionLabelZh(Decision decision) {
        return decision == Decision.STOP_HUMAN ? "停止（需人工决策）" : "继续";
    }

    public static String toMarkdown(Result result, int round) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 停止裁决（建造手册 §2.3）\n\n");
        sb.append("- 澄清轮次: ").append(round).append('\n');
        sb.append("- 裁决: **").append(decisionLabelZh(result.decision)).append("**\n");
        sb.append("- 原因码: `").append(result.reason).append("`（")
                .append(reasonLabelZh(result.reason)).append("）\n");
        sb.append("- 说明: ").append(result.message).append('\n');
        sb.append("- Runtime S4 人工等待: **未宣称**（仅 Demo/Analysis 层停止）\n");
        return sb.toString();
    }
}
