package com.ai4se.runtime.demo.delivery;

/**
 * Playbook §2.2 — local rollback advice after Delivery FAIL.
 * Not a new Gate object: renders into Delivery report so operators do not default to Requirement.
 */
final class LoopReturnAdvisor {

    enum FailKind {
        SHELL_VERIFY,
        MATRIX_MISSING,
        REVIEW_ABSENT,
        CLARIFICATION_BLOCKED,
        ANALYSIS_INVENTED
    }

    private LoopReturnAdvisor() {
    }

    static FailKind classify(ReviewVerdict verdict) {
        if (verdict == null) {
            return FailKind.REVIEW_ABSENT;
        }
        if (!verdict.shellVerificationOk) {
            return FailKind.SHELL_VERIFY;
        }
        if (!verdict.matrixComplete) {
            return FailKind.MATRIX_MISSING;
        }
        return FailKind.SHELL_VERIFY;
    }

    /** Default return target per §2.2 table. Never defaults Verification fail → Requirement. */
    static String recommendedReturn(FailKind kind) {
        switch (kind) {
            case SHELL_VERIFY:
            case MATRIX_MISSING:
                return "Execution";
            case REVIEW_ABSENT:
                return "Review";
            case CLARIFICATION_BLOCKED:
                return "Clarification";
            case ANALYSIS_INVENTED:
                return "Analysis";
            default:
                return "Execution";
        }
    }

    static boolean forbidsDefaultRequirement(FailKind kind) {
        return kind == FailKind.SHELL_VERIFY
                || kind == FailKind.MATRIX_MISSING
                || kind == FailKind.REVIEW_ABSENT
                || kind == FailKind.CLARIFICATION_BLOCKED
                || kind == FailKind.ANALYSIS_INVENTED;
    }

    static String renderSection(FailKind kind) {
        String target = recommendedReturn(kind);
        StringBuilder sb = new StringBuilder();
        sb.append("## Loop return (playbook §2.2)\n\n");
        sb.append("- failKind: ").append(kind).append('\n');
        sb.append("- **Recommended return: ").append(target).append("**\n");
        sb.append("- Forbidden default: Requirement ")
                .append("(only if demand vetoed / topic changed)\n");
        sb.append("- Rule: local rollback — do not reopen full pathway from Requirement.\n");
        return sb.toString();
    }

    static String renderForVerdict(ReviewVerdict verdict) {
        return renderSection(classify(verdict));
    }
}
