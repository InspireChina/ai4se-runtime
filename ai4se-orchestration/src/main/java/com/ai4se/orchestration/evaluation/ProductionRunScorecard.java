package com.ai4se.orchestration.evaluation;

import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Read-only M1 PR4 scorecard metrics from a Story run directory (no Adapter invocation).
 *
 * <p>Used for real Story A/B sign-off evidence. Does not invent human judgments — fields such as
 * mid-chat interventions and final diff verdict remain operator-filled.
 */
public final class ProductionRunScorecard {

    public static final String CSV_HEADER = ""
            + "story_id,arm,terminal,exit_code,awaiting_acceptance,rounds_used,max_dev_rounds,"
            + "last_round_outcome,verify_report_rounds,dev_packages,adapter_audits,"
            + "package_bytes,commit_sha,write_scope_violation_events,human_interventions,"
            + "missed_acceptance,diff_verdict,wall_time_sec,notes";

    private ProductionRunScorecard() {
    }

    public static Metrics collect(Path workspace, String storyId) throws IOException {
        if (workspace == null || Strings.isBlank(storyId)) {
            throw new IllegalArgumentException("workspace and storyId required");
        }
        Path storyRoot = workspace.resolve(".story").resolve(storyId.trim());
        RunLedger ledger = RunLedger.open(workspace, storyId.trim());
        RunLedger.RunStateSnapshot snap = ledger.readState();
        String terminal = snap.terminalOrNull == null ? "" : snap.terminalOrNull.trim();
        int exit = exitCodeFor(terminal);
        boolean awaiting = ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE.name().equals(terminal);
        String commit = "";
        try {
            String sha = DeliveryRecords.readCommitShaOrNull(workspace, storyId);
            commit = sha == null ? "" : sha;
        } catch (Exception ignored) {
            commit = "";
        }
        Path packages = storyRoot.resolve("packages");
        long packageBytes = sumBytes(packages);
        int devPackages = countDirs(packages.resolve("development"), "round-");
        int verifyReports = countFiles(
                storyRoot.resolve("verification"), "report-round-", ".md");
        int adapterAudits = countFiles(storyRoot.resolve("execution"), "adapter-", ".md");
        int writeScopeHits = countEventHints(ledger, "write scope", "Diff exceeds Allowed", "outside:");
        return new Metrics(
                storyId.trim(),
                terminal,
                exit,
                awaiting,
                snap.roundsUsed,
                snap.maxDevRoundsOrMinusOne,
                snap.lastRoundOutcomeOrNull == null ? "" : snap.lastRoundOutcomeOrNull,
                verifyReports,
                devPackages,
                adapterAudits,
                packageBytes,
                commit,
                writeScopeHits);
    }

    public static String toCsvLine(Metrics m, String arm, String humanInterventions,
            String missedAcceptance, String diffVerdict, String wallTimeSec, String notes) {
        return csv(
                m.storyId,
                arm == null ? "" : arm,
                m.terminal,
                Integer.toString(m.exitCode),
                m.awaitingAcceptance ? "1" : "0",
                Integer.toString(m.roundsUsed),
                Integer.toString(m.maxDevRoundsOrMinusOne),
                m.lastRoundOutcomeOrNull,
                Integer.toString(m.verifyReportRounds),
                Integer.toString(m.devPackages),
                Integer.toString(m.adapterAudits),
                Long.toString(m.packageBytes),
                m.commitShaOrEmpty,
                Integer.toString(m.writeScopeViolationEvents),
                humanInterventions == null ? "" : humanInterventions,
                missedAcceptance == null ? "" : missedAcceptance,
                diffVerdict == null ? "" : diffVerdict,
                wallTimeSec == null ? "" : wallTimeSec,
                notes == null ? "" : notes);
    }

    private static int exitCodeFor(String terminal) {
        if (Strings.isBlank(terminal)) {
            return -1;
        }
        try {
            return ProductionTerminal.valueOf(terminal.trim().toUpperCase(Locale.ROOT)).exitCode;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private static int countEventHints(RunLedger ledger, String... needles) throws IOException {
        int n = 0;
        for (String line : ledger.readEventLines()) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (lower.contains(needle.toLowerCase(Locale.ROOT))) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    private static long sumBytes(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return 0L;
        }
        final long[] total = new long[] {0L};
        Files.walk(root).forEach(p -> {
            if (Files.isRegularFile(p)) {
                try {
                    total[0] += Files.size(p);
                } catch (IOException ignored) {
                    // skip
                }
            }
        });
        return total[0];
    }

    private static int countDirs(Path parent, String prefix) throws IOException {
        if (parent == null || !Files.isDirectory(parent)) {
            return 0;
        }
        int n = 0;
        for (Path p : Files.newDirectoryStream(parent)) {
            if (Files.isDirectory(p) && p.getFileName().toString().startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    private static int countFiles(Path parent, String prefix, String suffix) throws IOException {
        if (parent == null || !Files.isDirectory(parent)) {
            return 0;
        }
        int n = 0;
        for (Path p : Files.newDirectoryStream(parent)) {
            String name = p.getFileName().toString();
            if (Files.isRegularFile(p) && name.startsWith(prefix) && name.endsWith(suffix)) {
                n++;
            }
        }
        return n;
    }

    private static String csv(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    private static String escape(String raw) {
        String s = raw == null ? "" : raw;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    public static final class Metrics {
        public final String storyId;
        public final String terminal;
        public final int exitCode;
        public final boolean awaitingAcceptance;
        public final int roundsUsed;
        public final int maxDevRoundsOrMinusOne;
        public final String lastRoundOutcomeOrNull;
        public final int verifyReportRounds;
        public final int devPackages;
        public final int adapterAudits;
        public final long packageBytes;
        public final String commitShaOrEmpty;
        public final int writeScopeViolationEvents;

        public Metrics(
                String storyId,
                String terminal,
                int exitCode,
                boolean awaitingAcceptance,
                int roundsUsed,
                int maxDevRoundsOrMinusOne,
                String lastRoundOutcomeOrNull,
                int verifyReportRounds,
                int devPackages,
                int adapterAudits,
                long packageBytes,
                String commitShaOrEmpty,
                int writeScopeViolationEvents) {
            this.storyId = storyId;
            this.terminal = terminal;
            this.exitCode = exitCode;
            this.awaitingAcceptance = awaitingAcceptance;
            this.roundsUsed = roundsUsed;
            this.maxDevRoundsOrMinusOne = maxDevRoundsOrMinusOne;
            this.lastRoundOutcomeOrNull = lastRoundOutcomeOrNull;
            this.verifyReportRounds = verifyReportRounds;
            this.devPackages = devPackages;
            this.adapterAudits = adapterAudits;
            this.packageBytes = packageBytes;
            this.commitShaOrEmpty = commitShaOrEmpty == null ? "" : commitShaOrEmpty;
            this.writeScopeViolationEvents = writeScopeViolationEvents;
        }

        public String toHumanSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("story=").append(storyId).append('\n');
            sb.append("terminal=").append(terminal).append(" exit=").append(exitCode).append('\n');
            sb.append("awaiting_acceptance=").append(awaitingAcceptance).append('\n');
            sb.append("rounds_used=").append(roundsUsed)
                    .append(" max_dev_rounds=").append(maxDevRoundsOrMinusOne).append('\n');
            sb.append("last_round_outcome=").append(lastRoundOutcomeOrNull).append('\n');
            sb.append("verify_reports=").append(verifyReportRounds)
                    .append(" dev_packages=").append(devPackages)
                    .append(" adapter_audits=").append(adapterAudits).append('\n');
            sb.append("package_bytes=").append(packageBytes).append('\n');
            sb.append("commit=").append(Strings.isBlank(commitShaOrEmpty) ? "-" : commitShaOrEmpty)
                    .append('\n');
            sb.append("write_scope_violation_events=").append(writeScopeViolationEvents).append('\n');
            return sb.toString();
        }
    }
}
