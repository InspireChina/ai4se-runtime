package com.ai4se.orchestration.evaluation;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.control.RoundOutcome;
import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.development.DiffScopeGuard;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only M1 PR4 scorecard metrics from a Story run directory (no Adapter invocation).
 *
 * <p>Does not create directories. Rejects unsafe {@code storyId}. Independently audits
 * {@code baseline..delivery} paths vs persisted write_scope, and Verify PASS event order
 * before Review/Delivery/awaiting.
 */
public final class ProductionRunScorecard {

    public static final String NA = "na";

    public static final String CSV_HEADER = ""
            + "pair_id,story_id,arm,baseline_commit,model_id,write_scope,"
            + "terminal,exit_code,run_settled,awaiting_acceptance,rounds_used,max_dev_rounds,"
            + "last_round_outcome,verify_report_rounds,dev_packages,adapter_audits,"
            + "package_bytes,p1_bytes,p2_bytes,"
            + "input_tokens,output_tokens,tool_calls,"
            + "commit_sha,commit_exists,commit_scope_ok,verify_pass_before_review,"
            + "write_scope_violation_events,"
            + "human_interventions,missed_acceptance,diff_verdict,wall_time_sec,notes";

    private ProductionRunScorecard() {
    }

    public static Metrics collect(Path workspace, String storyId) throws IOException {
        return collect(
                workspace,
                storyId,
                new ProcessInvoker.RealProcessInvoker(),
                ExperimentHints.empty());
    }

    public static Metrics collect(
            Path workspace,
            String storyId,
            ProcessInvoker invoker,
            ExperimentHints hints) throws IOException {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace required");
        }
        if (invoker == null) {
            throw new IllegalArgumentException("ProcessInvoker required for independent git audits");
        }
        ExperimentHints h = hints == null ? ExperimentHints.empty() : hints;
        String id = requireSafeStoryId(storyId);
        Path ws = workspace.toAbsolutePath().normalize();
        Path storyRoot = requireStoryRootInsideWorkspace(ws, id);

        RunLedger ledger = RunLedger.openExisting(ws, id);
        RunLedger.RunStateSnapshot snap = ledger.readState();
        String terminal = snap.terminalOrNull == null ? "" : snap.terminalOrNull.trim();
        int exit = exitCodeFor(terminal);
        String runSettled = isSettledTerminal(terminal) ? "1" : "0";
        boolean awaiting = ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE.name().equals(terminal);
        String writeScope = snap.writeScopeOrNull == null ? "" : snap.writeScopeOrNull.trim();
        List<String> scopeList = splitCsv(writeScope);

        String commit = "";
        try {
            String sha = DeliveryRecords.readCommitShaOrNull(ws, id);
            commit = sha == null ? "" : sha;
        } catch (Exception ignored) {
            commit = "";
        }

        boolean commitExists = false;
        String commitScopeOk = NA;
        String baseline = na(h.baselineCommit);
        if (!Strings.isBlank(commit)) {
            commitExists = WorkspaceGit.commitExists(ws, invoker, commit);
            if (!commitExists) {
                commitScopeOk = "0";
            } else if (scopeList.isEmpty()) {
                commitScopeOk = NA;
            } else if (NA.equals(baseline)) {
                // Without a comparable baseline we cannot claim range scope ok.
                commitScopeOk = NA;
            } else if (!WorkspaceGit.commitExists(ws, invoker, baseline)) {
                commitScopeOk = "0";
            } else if (!WorkspaceGit.isAncestor(ws, invoker, baseline, commit)) {
                commitScopeOk = "0";
            } else {
                List<String> paths = WorkspaceGit.rangePaths(ws, invoker, baseline, commit);
                List<String> business = filterBusiness(paths);
                List<String> bad = DiffScopeGuard.findViolations(business, scopeList);
                commitScopeOk = bad.isEmpty() ? "1" : "0";
            }
        } else {
            commitScopeOk = awaiting ? "0" : NA;
        }

        String verifyPassBeforeReview = auditVerifyPassBeforeReview(ledger, storyRoot, snap, awaiting);

        Path packages = storyRoot.resolve("packages");
        long packageBytes = sumBytes(packages);
        long p1Bytes = sumP1Bytes(packages);
        long p2Bytes = Math.max(0L, packageBytes - p1Bytes);
        int devPackages = countDirs(packages.resolve("development"), "round-");
        int verifyReports = countFiles(storyRoot.resolve("verification"), "report-round-", ".md");
        int adapterAudits = countFiles(storyRoot.resolve("execution"), "adapter-", ".md");
        int writeScopeHits = countEventHints(ledger, "write scope", "Diff exceeds Allowed", "outside:");

        return new Metrics(
                na(h.pairId),
                id,
                na(h.arm),
                baseline,
                na(h.modelId),
                writeScope.isEmpty() ? NA : writeScope,
                terminal,
                exit,
                runSettled,
                awaiting,
                snap.roundsUsed,
                snap.maxDevRoundsOrMinusOne,
                snap.lastRoundOutcomeOrNull == null ? "" : snap.lastRoundOutcomeOrNull,
                verifyReports,
                devPackages,
                adapterAudits,
                packageBytes,
                p1Bytes,
                p2Bytes,
                na(h.inputTokens),
                na(h.outputTokens),
                na(h.toolCalls),
                commit,
                commitExists ? "1" : "0",
                commitScopeOk,
                verifyPassBeforeReview,
                writeScopeHits,
                na(h.humanInterventions),
                na(h.missedAcceptance),
                na(h.diffVerdict),
                na(h.wallTimeSec),
                na(h.notes));
    }

    public static String toCsvLine(Metrics m) {
        return csv(
                m.pairId,
                m.storyId,
                m.arm,
                m.baselineCommit,
                m.modelId,
                m.writeScope,
                m.terminal,
                Integer.toString(m.exitCode),
                m.runSettled,
                m.awaitingAcceptance ? "1" : "0",
                Integer.toString(m.roundsUsed),
                Integer.toString(m.maxDevRoundsOrMinusOne),
                m.lastRoundOutcomeOrNull,
                Integer.toString(m.verifyReportRounds),
                Integer.toString(m.devPackages),
                Integer.toString(m.adapterAudits),
                Long.toString(m.packageBytes),
                Long.toString(m.p1Bytes),
                Long.toString(m.p2Bytes),
                m.inputTokens,
                m.outputTokens,
                m.toolCalls,
                m.commitShaOrEmpty,
                m.commitExists,
                m.commitScopeOk,
                m.verifyPassBeforeReview,
                Integer.toString(m.writeScopeViolationEvents),
                m.humanInterventions,
                m.missedAcceptance,
                m.diffVerdict,
                m.wallTimeSec,
                m.notes);
    }

    /** @deprecated use {@link #toCsvLine(Metrics)} — arm/human fields live on Metrics via hints */
    @Deprecated
    public static String toCsvLine(
            Metrics m,
            String arm,
            String humanInterventions,
            String missedAcceptance,
            String diffVerdict,
            String wallTimeSec,
            String notes) {
        ExperimentHints h = ExperimentHints.empty();
        h.arm = arm;
        h.humanInterventions = humanInterventions;
        h.missedAcceptance = missedAcceptance;
        h.diffVerdict = diffVerdict;
        h.wallTimeSec = wallTimeSec;
        h.notes = notes;
        Metrics merged = m.withHints(h);
        return toCsvLine(merged);
    }

    static String requireSafeStoryId(String raw) {
        if (Strings.isBlank(raw)) {
            throw new IllegalArgumentException("storyId required");
        }
        String id = raw.trim();
        if (id.contains("/") || id.contains("\\") || id.contains("..")) {
            throw new IllegalArgumentException("storyId must be a single path segment: " + raw);
        }
        if (!id.matches("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")) {
            throw new IllegalArgumentException("storyId contains illegal characters: " + raw);
        }
        return id;
    }

    static Path requireStoryRootInsideWorkspace(Path workspace, String storyId) throws IOException {
        Path base = workspace.resolve(".story").normalize();
        Path root = base.resolve(storyId).normalize();
        if (!root.startsWith(base) || root.equals(base)) {
            throw new IllegalArgumentException(
                    "story root escapes workspace/.story: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new StageGateException("Missing story directory (read-only): " + root);
        }
        return root;
    }

    private static List<String> filterBusiness(List<String> paths) {
        List<String> out = new ArrayList<String>();
        for (String p : paths) {
            if (!WorkspaceGit.isIgnorableForMutation(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static List<String> splitCsv(String csv) {
        if (Strings.isBlank(csv)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean hasStrictPassingVerificationReport(Path storyRoot) throws IOException {
        Path dir = storyRoot.resolve("verification");
        if (!Files.isDirectory(dir)) {
            return false;
        }
        Path latest = null;
        int maxRound = -1;
        for (Path p : Files.newDirectoryStream(dir, "report-round-*.md")) {
            String name = p.getFileName().toString();
            try {
                int n = Integer.parseInt(
                        name.substring("report-round-".length(), name.length() - ".md".length()));
                if (n > maxRound) {
                    maxRound = n;
                    latest = p;
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (latest == null || !Files.isRegularFile(latest)) {
            return false;
        }
        String text = new String(Files.readAllBytes(latest), StandardCharsets.UTF_8);
        return hasStrictPassOutcome(text);
    }

    /** Require a dedicated outcome line {@code outcome: PASS} (not substring / misspellings). */
    static boolean hasStrictPassOutcome(String reportText) {
        if (reportText == null) {
            return false;
        }
        for (String line : reportText.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("-")) {
                t = t.substring(1).trim();
            }
            if (t.equalsIgnoreCase("outcome: PASS") || t.equalsIgnoreCase("outcome:PASS")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Independent ordering audit: honest chain requires
     * {@code VERIFY_PASS seq < VERIFICATION stage_completed seq < Review/Delivery/awaiting seq},
     * plus a strict PASS report and matching ledger outcome.
     */
    static String auditVerifyPassBeforeReview(
            RunLedger ledger,
            Path storyRoot,
            RunLedger.RunStateSnapshot snap,
            boolean awaiting) throws IOException {
        boolean enteredGate = awaiting
                || ledger.hasCompleted(WorkflowStage.REVIEW)
                || ledger.hasCompleted(WorkflowStage.DELIVERY);
        Long passSeq = earliestRoundVerifyPassSeq(ledger);
        Long verificationCompletedSeq = earliestVerificationStageCompletedSeq(ledger);
        Long gateSeq = earliestReviewOrCommitGateSeq(ledger);
        boolean strictReport = hasStrictPassingVerificationReport(storyRoot);
        boolean ledgerPass = RoundOutcome.VERIFY_PASS.name().equals(
                snap.lastRoundOutcomeOrNull == null
                        ? ""
                        : snap.lastRoundOutcomeOrNull.trim().toUpperCase(Locale.ROOT));

        if (!enteredGate && gateSeq == null) {
            if (passSeq != null
                    && verificationCompletedSeq != null
                    && passSeq.longValue() < verificationCompletedSeq.longValue()
                    && strictReport
                    && ledgerPass) {
                return "1";
            }
            if (passSeq == null && verificationCompletedSeq == null && !strictReport) {
                return NA;
            }
            return "0";
        }

        if (passSeq == null || verificationCompletedSeq == null || gateSeq == null) {
            return "0";
        }
        // PASS must precede Verification completion, which must precede Review/Delivery/awaiting.
        if (!(passSeq.longValue() < verificationCompletedSeq.longValue()
                && verificationCompletedSeq.longValue() < gateSeq.longValue())) {
            return "0";
        }
        if (!strictReport || !ledgerPass) {
            return "0";
        }
        return "1";
    }

    /** PASS clock is only {@code round_completed} with {@code outcome=VERIFY_PASS}. */
    static Long earliestRoundVerifyPassSeq(RunLedger ledger) throws IOException {
        Long best = null;
        for (String line : ledger.readEventLines()) {
            long seq = parseJsonLongField(line, "seq", -1L);
            if (seq < 1) {
                continue;
            }
            if (!"round_completed".equals(jsonStringField(line, "type"))) {
                continue;
            }
            String detail = jsonStringField(line, "detail");
            if (detail != null && detail.contains("outcome=" + RoundOutcome.VERIFY_PASS.name())) {
                best = minSeq(best, seq);
            }
        }
        return best;
    }

    static Long earliestVerificationStageCompletedSeq(RunLedger ledger) throws IOException {
        Long best = null;
        for (String line : ledger.readEventLines()) {
            long seq = parseJsonLongField(line, "seq", -1L);
            if (seq < 1) {
                continue;
            }
            if (!"stage_completed".equals(jsonStringField(line, "type"))) {
                continue;
            }
            if (WorkflowStage.VERIFICATION.name().equals(jsonStringField(line, "stage"))) {
                best = minSeq(best, seq);
            }
        }
        return best;
    }

    static Long earliestReviewOrCommitGateSeq(RunLedger ledger) throws IOException {
        Long best = null;
        for (String line : ledger.readEventLines()) {
            long seq = parseJsonLongField(line, "seq", -1L);
            if (seq < 1) {
                continue;
            }
            String type = jsonStringField(line, "type");
            String stage = jsonStringField(line, "stage");
            if (("stage_started".equals(type) || "stage_completed".equals(type))
                    && (WorkflowStage.REVIEW.name().equals(stage)
                            || WorkflowStage.DELIVERY.name().equals(stage))) {
                best = minSeq(best, seq);
                continue;
            }
            if ("run_stopped".equals(type)) {
                String terminal = jsonStringField(line, "terminal");
                if (ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE.name().equals(terminal)) {
                    best = minSeq(best, seq);
                }
            }
        }
        return best;
    }

    private static Long minSeq(Long cur, long seq) {
        if (cur == null || seq < cur.longValue()) {
            return Long.valueOf(seq);
        }
        return cur;
    }

    private static String jsonStringField(String jsonLine, String field) {
        String key = "\"" + field + "\":\"";
        int i = jsonLine.indexOf(key);
        if (i < 0) {
            return null;
        }
        int start = i + key.length();
        StringBuilder sb = new StringBuilder();
        for (int j = start; j < jsonLine.length(); j++) {
            char c = jsonLine.charAt(j);
            if (c == '\\' && j + 1 < jsonLine.length()) {
                sb.append(jsonLine.charAt(++j));
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static long parseJsonLongField(String jsonLine, String field, long dflt) {
        String key = "\"" + field + "\":";
        int i = jsonLine.indexOf(key);
        if (i < 0) {
            return dflt;
        }
        int start = i + key.length();
        int end = start;
        while (end < jsonLine.length()) {
            char c = jsonLine.charAt(end);
            if (c == '-' || (c >= '0' && c <= '9')) {
                end++;
                continue;
            }
            break;
        }
        if (end == start) {
            return dflt;
        }
        try {
            return Long.parseLong(jsonLine.substring(start, end));
        } catch (NumberFormatException e) {
            return dflt;
        }
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

    /** True when ledger terminal is a known {@link ProductionTerminal} (not blank / in-flight). */
    static boolean isSettledTerminal(String terminal) {
        if (Strings.isBlank(terminal)) {
            return false;
        }
        try {
            ProductionTerminal.valueOf(terminal.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
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

    /** Heuristic P1 slices: acceptance / allowed / defect refs under packages. */
    private static long sumP1Bytes(Path packagesRoot) throws IOException {
        if (packagesRoot == null || !Files.isDirectory(packagesRoot)) {
            return 0L;
        }
        final long[] total = new long[] {0L};
        Files.walk(packagesRoot).forEach(p -> {
            if (!Files.isRegularFile(p)) {
                return;
            }
            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.contains("acceptance")
                    || name.contains("allowed")
                    || name.contains("defect")) {
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

    private static String na(String raw) {
        return Strings.isBlank(raw) ? NA : raw.trim();
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

    /** Operator-supplied A/B experiment columns; blank → {@link #NA}. */
    public static final class ExperimentHints {
        public String pairId = NA;
        public String arm = NA;
        public String baselineCommit = NA;
        public String modelId = NA;
        public String inputTokens = NA;
        public String outputTokens = NA;
        public String toolCalls = NA;
        public String humanInterventions = NA;
        public String missedAcceptance = NA;
        public String diffVerdict = NA;
        public String wallTimeSec = NA;
        public String notes = NA;

        public static ExperimentHints empty() {
            return new ExperimentHints();
        }
    }

    public static final class Metrics {
        public final String pairId;
        public final String storyId;
        public final String arm;
        public final String baselineCommit;
        public final String modelId;
        public final String writeScope;
        public final String terminal;
        public final int exitCode;
        public final String runSettled;
        public final boolean awaitingAcceptance;
        public final int roundsUsed;
        public final int maxDevRoundsOrMinusOne;
        public final String lastRoundOutcomeOrNull;
        public final int verifyReportRounds;
        public final int devPackages;
        public final int adapterAudits;
        public final long packageBytes;
        public final long p1Bytes;
        public final long p2Bytes;
        public final String inputTokens;
        public final String outputTokens;
        public final String toolCalls;
        public final String commitShaOrEmpty;
        public final String commitExists;
        public final String commitScopeOk;
        public final String verifyPassBeforeReview;
        public final int writeScopeViolationEvents;
        public final String humanInterventions;
        public final String missedAcceptance;
        public final String diffVerdict;
        public final String wallTimeSec;
        public final String notes;

        public Metrics(
                String pairId,
                String storyId,
                String arm,
                String baselineCommit,
                String modelId,
                String writeScope,
                String terminal,
                int exitCode,
                String runSettled,
                boolean awaitingAcceptance,
                int roundsUsed,
                int maxDevRoundsOrMinusOne,
                String lastRoundOutcomeOrNull,
                int verifyReportRounds,
                int devPackages,
                int adapterAudits,
                long packageBytes,
                long p1Bytes,
                long p2Bytes,
                String inputTokens,
                String outputTokens,
                String toolCalls,
                String commitShaOrEmpty,
                String commitExists,
                String commitScopeOk,
                String verifyPassBeforeReview,
                int writeScopeViolationEvents,
                String humanInterventions,
                String missedAcceptance,
                String diffVerdict,
                String wallTimeSec,
                String notes) {
            this.pairId = pairId;
            this.storyId = storyId;
            this.arm = arm;
            this.baselineCommit = baselineCommit;
            this.modelId = modelId;
            this.writeScope = writeScope;
            this.terminal = terminal;
            this.exitCode = exitCode;
            this.runSettled = runSettled;
            this.awaitingAcceptance = awaitingAcceptance;
            this.roundsUsed = roundsUsed;
            this.maxDevRoundsOrMinusOne = maxDevRoundsOrMinusOne;
            this.lastRoundOutcomeOrNull = lastRoundOutcomeOrNull;
            this.verifyReportRounds = verifyReportRounds;
            this.devPackages = devPackages;
            this.adapterAudits = adapterAudits;
            this.packageBytes = packageBytes;
            this.p1Bytes = p1Bytes;
            this.p2Bytes = p2Bytes;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.toolCalls = toolCalls;
            this.commitShaOrEmpty = commitShaOrEmpty == null ? "" : commitShaOrEmpty;
            this.commitExists = commitExists;
            this.commitScopeOk = commitScopeOk;
            this.verifyPassBeforeReview = verifyPassBeforeReview;
            this.writeScopeViolationEvents = writeScopeViolationEvents;
            this.humanInterventions = humanInterventions;
            this.missedAcceptance = missedAcceptance;
            this.diffVerdict = diffVerdict;
            this.wallTimeSec = wallTimeSec;
            this.notes = notes;
        }

        Metrics withHints(ExperimentHints h) {
            return new Metrics(
                    pairId,
                    storyId,
                    na(h.arm),
                    baselineCommit,
                    modelId,
                    writeScope,
                    terminal,
                    exitCode,
                    runSettled,
                    awaitingAcceptance,
                    roundsUsed,
                    maxDevRoundsOrMinusOne,
                    lastRoundOutcomeOrNull,
                    verifyReportRounds,
                    devPackages,
                    adapterAudits,
                    packageBytes,
                    p1Bytes,
                    p2Bytes,
                    inputTokens,
                    outputTokens,
                    toolCalls,
                    commitShaOrEmpty,
                    commitExists,
                    commitScopeOk,
                    verifyPassBeforeReview,
                    writeScopeViolationEvents,
                    na(h.humanInterventions),
                    na(h.missedAcceptance),
                    na(h.diffVerdict),
                    na(h.wallTimeSec),
                    na(h.notes));
        }

        public String toHumanSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("pair_id=").append(pairId).append(" arm=").append(arm).append('\n');
            sb.append("story=").append(storyId).append('\n');
            sb.append("baseline_commit=").append(baselineCommit)
                    .append(" model_id=").append(modelId).append('\n');
            sb.append("write_scope=").append(writeScope).append('\n');
            sb.append("terminal=").append(terminal).append(" exit=").append(exitCode)
                    .append(" run_settled=").append(runSettled).append('\n');
            sb.append("awaiting_acceptance=").append(awaitingAcceptance).append('\n');
            sb.append("rounds_used=").append(roundsUsed)
                    .append(" max_dev_rounds=").append(maxDevRoundsOrMinusOne).append('\n');
            sb.append("last_round_outcome=").append(lastRoundOutcomeOrNull).append('\n');
            sb.append("verify_reports=").append(verifyReportRounds)
                    .append(" dev_packages=").append(devPackages)
                    .append(" adapter_audits=").append(adapterAudits).append('\n');
            sb.append("package_bytes=").append(packageBytes)
                    .append(" p1_bytes=").append(p1Bytes)
                    .append(" p2_bytes=").append(p2Bytes).append('\n');
            sb.append("tokens_in=").append(inputTokens)
                    .append(" tokens_out=").append(outputTokens)
                    .append(" tool_calls=").append(toolCalls).append('\n');
            sb.append("commit=").append(Strings.isBlank(commitShaOrEmpty) ? "-" : commitShaOrEmpty)
                    .append(" exists=").append(commitExists)
                    .append(" scope_ok=").append(commitScopeOk).append('\n');
            sb.append("verify_pass_before_review=").append(verifyPassBeforeReview).append('\n');
            sb.append("write_scope_violation_events=").append(writeScopeViolationEvents)
                    .append(" (event-text hint only; prefer commit_scope_ok)").append('\n');
            return sb.toString();
        }
    }
}
