package com.ai4se.orchestration.verification;

import java.util.List;

/**
 * Conservative disclosure used when no repository-neutral coverage mapping is available.
 * It never infers coverage from project names or path conventions.
 */
final class VerifyCoverageGap {
    private VerifyCoverageGap() {
    }

    static Assessment assess(List<String> changedPaths, List<String> verificationCommands) {
        int changedCount = changedPaths == null ? 0 : changedPaths.size();
        int commandCount = verificationCommands == null ? 0 : verificationCommands.size();
        return new Assessment(changedCount, commandCount);
    }

    static final class Assessment {
        private final int changedCount;
        private final int commandCount;

        private Assessment(int changedCount, int commandCount) {
            this.changedCount = changedCount;
            this.commandCount = commandCount;
        }

        String gapLabel() {
            return "not_evaluated";
        }

        String detailLine() {
            return "repository-neutral mapping unavailable; changed_paths="
                    + changedCount
                    + ", verification_commands="
                    + commandCount;
        }
    }
}
