package com.ai4se.orchestration.run;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.control.FailureFingerprint;
import com.ai4se.orchestration.control.RoundOutcome;
import com.ai4se.orchestration.control.RoundProgressSink;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.runtime.common.util.Strings;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Durable production run ledger under {@code .story/<id>/run/} (M1 PR3).
 *
 * <p>{@code events.jsonl} is append-only. {@code stage_completed} is written only after the
 * caller has validated stage artifacts.
 */
public final class RunLedger implements RoundProgressSink {

    public static final String RUN_DIR = "run";
    public static final String STATE_FILE = "state.properties";
    public static final String EVENTS_FILE = "events.jsonl";
    public static final String FINGERPRINT_FILE = "failure-fingerprint";

    private final Path workspace;
    private final String storyId;
    private final Path runDir;
    private long sequence;

    private RunLedger(Path workspace, String storyId, Path runDir, long sequence) {
        this.workspace = workspace;
        this.storyId = storyId;
        this.runDir = runDir;
        this.sequence = sequence;
    }

    public static Path runDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(RUN_DIR);
    }

    public static RunLedger open(Path workspace, String storyId) throws IOException {
        Path dir = runDir(workspace, storyId);
        Files.createDirectories(dir);
        long seq = 0L;
        Path state = dir.resolve(STATE_FILE);
        if (Files.isRegularFile(state)) {
            Properties p = loadProperties(state);
            seq = parseLong(p.getProperty("last_event_sequence"), 0L);
        }
        return new RunLedger(workspace, storyId, dir, seq);
    }

    /**
     * Open an existing run ledger without creating directories. Requires {@code state.properties}
     * and {@code events.jsonl}. Used by read-only scorecard / auditors.
     */
    public static RunLedger openExisting(Path workspace, String storyId) throws IOException {
        Path dir = runDir(workspace, storyId);
        if (!Files.isDirectory(dir)) {
            throw new StageGateException(
                    "Missing run directory (read-only open refuses to create): " + dir);
        }
        Path state = dir.resolve(STATE_FILE);
        Path events = dir.resolve(EVENTS_FILE);
        if (!Files.isRegularFile(state)) {
            throw new StageGateException("Missing run state.properties (read-only)");
        }
        if (!Files.isRegularFile(events)) {
            throw new StageGateException("Missing run events.jsonl (read-only)");
        }
        Properties p = loadProperties(state);
        long seq = parseLong(p.getProperty("last_event_sequence"), 0L);
        return new RunLedger(workspace, storyId, dir, seq);
    }

    public Path directory() {
        return runDir;
    }

    public Path eventsPath() {
        return runDir.resolve(EVENTS_FILE);
    }

    public Path statePath() {
        return runDir.resolve(STATE_FILE);
    }

    public Path fingerprintPath() {
        return runDir.resolve(FINGERPRINT_FILE);
    }

    public synchronized void beginRun(String writeScopeCsv, int maxDevelopmentRounds) throws IOException {
        beginRun(writeScopeCsv, maxDevelopmentRounds, null, null);
    }

    /** Begin a run while recording the controlled Adapter selection in durable evidence. */
    public synchronized void beginRun(
            String writeScopeCsv,
            int maxDevelopmentRounds,
            String adapterName,
            String model) throws IOException {
        beginRun(writeScopeCsv, maxDevelopmentRounds, adapterName, model, null);
    }

    /** Begin a run with an explicit model-selection provenance value. */
    public synchronized void beginRun(
            String writeScopeCsv,
            int maxDevelopmentRounds,
            String adapterName,
            String model,
            String modelSelection) throws IOException {
        Properties p = readStateProperties();
        p.setProperty("write_scope", writeScopeCsv == null ? "" : writeScopeCsv);
        p.setProperty("max_dev_rounds", Integer.toString(maxDevelopmentRounds));
        p.setProperty("rounds_used", "0");
        p.setProperty("current_round", "0");
        p.setProperty("last_round_outcome", RoundOutcome.IN_FLIGHT.name());
        if (!Strings.isBlank(adapterName)) {
            p.setProperty("adapter", adapterName.trim());
            p.setProperty("adapter_provenance", "pinned");
        }
        if (!Strings.isBlank(model)) {
            p.setProperty("model", model.trim());
        }
        if (!Strings.isBlank(modelSelection)) {
            p.setProperty("model_selection", modelSelection.trim());
        }
        p.remove("failure_fingerprint");
        p.remove("failure_diff_hash");
        storeProperties(p);
        appendEvent("run_started", null, null,
                "write_scope=" + (writeScopeCsv == null ? "" : writeScopeCsv)
                        + " max_dev_rounds=" + maxDevelopmentRounds
                        + (Strings.isBlank(adapterName) ? "" : " adapter=" + adapterName.trim())
                        + (Strings.isBlank(model) ? "" : " model=" + model.trim())
                        + (Strings.isBlank(modelSelection)
                                ? "" : " model_selection=" + modelSelection.trim()));
        rewriteState(WorkflowStage.ANALYSIS.name(), "RUNNING", null, null);
    }

    /** Persist absolute Development rounds consumed (continues across resume). */
    public synchronized void recordRoundsUsed(int roundsUsed) throws IOException {
        if (roundsUsed < 0) {
            throw new StageGateException("rounds_used must be >= 0");
        }
        Properties p = readStateProperties();
        int prev = parseInt(p.getProperty("rounds_used"), 0);
        int next = Math.max(prev, roundsUsed);
        p.setProperty("rounds_used", Integer.toString(next));
        int current = parseInt(p.getProperty("current_round"), 0);
        // Controlled exits may bump rounds_used without onRoundCompleted — clear stale in-flight.
        if (current > 0 && next >= current) {
            p.setProperty("current_round", "0");
        }
        storeProperties(p);
        appendEvent("rounds_progress", WorkflowStage.DEVELOPMENT.name(), null,
                "rounds_used=" + next);
    }

    /**
     * Mark a Development round as in-flight before Adapter work. Resume re-runs this round
     * until {@link #onRoundCompleted} clears it.
     */
    @Override
    public synchronized void onRoundStarted(int round) throws IOException {
        if (round < 1) {
            throw new StageGateException("current_round must be >= 1");
        }
        Properties p = readStateProperties();
        p.setProperty("current_round", Integer.toString(round));
        p.setProperty("last_round_outcome", RoundOutcome.IN_FLIGHT.name());
        storeProperties(p);
        appendEvent("round_started", WorkflowStage.DEVELOPMENT.name(), null,
                "round=" + round + " outcome=" + RoundOutcome.IN_FLIGHT.name());
    }

    /**
     * Mark a Development↔Verify attempt finished with an explicit {@link RoundOutcome}.
     * Updates {@code rounds_used} immediately; persists no-progress context on VERIFY_FAIL only.
     */
    @Override
    public synchronized void onRoundCompleted(
            int round,
            RoundOutcome outcome,
            FailureFingerprint fingerprintOrNull,
            String businessDiffHashOrNull)
            throws IOException {
        if (round < 1) {
            throw new StageGateException("round must be >= 1");
        }
        if (outcome == null || outcome == RoundOutcome.IN_FLIGHT) {
            throw new StageGateException("onRoundCompleted requires a settled RoundOutcome");
        }
        Properties p = readStateProperties();
        int prevUsed = parseInt(p.getProperty("rounds_used"), 0);
        int nextUsed = Math.max(prevUsed, round);
        p.setProperty("rounds_used", Integer.toString(nextUsed));
        p.setProperty("current_round", "0");
        p.setProperty("last_round_outcome", outcome.name());
        if (outcome == RoundOutcome.VERIFY_FAIL && fingerprintOrNull != null) {
            String fp = fingerprintOrNull.toString();
            Files.write(fingerprintPath(), (fp + "\n").getBytes(StandardCharsets.UTF_8));
            p.setProperty("failure_fingerprint", fp);
            if (!Strings.isBlank(businessDiffHashOrNull)) {
                p.setProperty("failure_diff_hash", businessDiffHashOrNull.trim());
            } else {
                p.remove("failure_diff_hash");
            }
        } else if (outcome == RoundOutcome.VERIFY_PASS) {
            p.remove("failure_fingerprint");
            p.remove("failure_diff_hash");
        }
        // Controlled failures keep prior no-progress context unless overwritten later.
        storeProperties(p);
        appendEvent(
                "round_completed",
                WorkflowStage.DEVELOPMENT.name(),
                null,
                "round=" + round + " rounds_used=" + nextUsed + " outcome=" + outcome.name());
    }

    /**
     * Clear a prior stop terminal so resume can continue from the last stage boundary.
     * Preserves {@code rounds_used}, {@code max_dev_rounds}, and completed stage events.
     */
    public synchronized void prepareResume() throws IOException {
        requireConsistentForResume();
        Properties p = readStateProperties();
        p.remove("terminal");
        p.remove("detail");
        p.setProperty("status", "RUNNING");
        storeProperties(p);
        appendEvent("run_resumed", p.getProperty("stage"), null, "resume from stage boundary");
    }

    /** Record that this ledger predates adapter pinning and was resumed compatibly. */
    public synchronized void markLegacyAdapterProvenance() throws IOException {
        Properties p = readStateProperties();
        if (!"legacy_unpinned".equals(p.getProperty("adapter_provenance"))) {
            p.setProperty("adapter_provenance", "legacy_unpinned");
            storeProperties(p);
            appendEvent(
                    "adapter_provenance",
                    null,
                    null,
                    "adapter_provenance=legacy_unpinned");
        }
    }

    public synchronized void stageStarted(WorkflowStage stage) throws IOException {
        if (stage == null) {
            return;
        }
        appendEvent("stage_started", stage.name(), null, null);
        rewriteState(stage.name(), "RUNNING", null, null);
    }

    /**
     * Record a completed stage boundary. Caller must have validated artifacts first.
     */
    public synchronized void stageCompleted(WorkflowStage stage) throws IOException {
        if (stage == null) {
            throw new StageGateException("stage_completed requires stage");
        }
        appendEvent("stage_completed", stage.name(), null, null);
        rewriteState(stage.name(), "STAGE_COMPLETED", null, null);
    }

    public synchronized void markTerminal(ProductionTerminal terminal, String detailOrNull)
            throws IOException {
        if (terminal == null) {
            throw new StageGateException("terminal required");
        }
        appendEvent("run_stopped", null, terminal.name(), detailOrNull);
        rewriteState(
                readState().stageOrNull,
                terminal.name(),
                terminal.name(),
                detailOrNull);
    }

    public synchronized void writeFailureFingerprint(String fingerprint) throws IOException {
        String text = fingerprint == null ? "" : fingerprint.trim();
        Files.write(fingerprintPath(), (text + "\n").getBytes(StandardCharsets.UTF_8));
        Properties p = readStateProperties();
        p.setProperty("failure_fingerprint", text);
        storeProperties(p);
    }

    public RunStateSnapshot readState() throws IOException {
        Properties p = readStateProperties();
        return new RunStateSnapshot(
                p.getProperty("story_id", storyId),
                p.getProperty("stage"),
                p.getProperty("status"),
                p.getProperty("terminal"),
                parseLong(p.getProperty("last_event_sequence"), sequence),
                p.getProperty("failure_fingerprint"),
                p.getProperty("failure_diff_hash"),
                p.getProperty("write_scope"),
                parseInt(p.getProperty("max_dev_rounds"), -1),
                parseInt(p.getProperty("rounds_used"), 0),
                parseInt(p.getProperty("current_round"), 0),
                p.getProperty("last_round_outcome"),
                p.getProperty("adapter"),
                p.getProperty("model"),
                p.getProperty("adapter_provenance"),
                p.getProperty("model_selection"));
    }

    public RoundOutcome lastRoundOutcomeOrNull() throws IOException {
        return parseRoundOutcomeOrNull(readState().lastRoundOutcomeOrNull);
    }

    public static RoundOutcome parseRoundOutcomeOrNull(String raw) {
        if (Strings.isBlank(raw)) {
            return null;
        }
        try {
            return RoundOutcome.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Completed rounds already consumed for budget accounting.
     *
     * <ul>
     *   <li>Process kill mid-round: {@code rounds_used < current_round} → re-run {@code current_round}
     *       (return {@code current_round - 1}).
     *   <li>Controlled failure already recorded: {@code rounds_used >= current_round} → use
     *       {@code rounds_used} (do not grant a free retry of the failed round).
     * </ul>
     */
    public int completedRoundsForResume() throws IOException {
        RunStateSnapshot snap = readState();
        if (snap.currentRound > 0 && snap.roundsUsed < snap.currentRound) {
            return Math.max(0, snap.currentRound - 1);
        }
        return snap.roundsUsed;
    }

    public Set<WorkflowStage> completedStages() throws IOException {
        LinkedHashSet<WorkflowStage> out = new LinkedHashSet<WorkflowStage>();
        for (String line : readEventLines()) {
            if (!line.contains("\"type\":\"stage_completed\"")) {
                continue;
            }
            String stage = jsonStringField(line, "stage");
            if (Strings.isBlank(stage)) {
                continue;
            }
            try {
                out.add(WorkflowStage.valueOf(stage.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // skip unknown
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public boolean hasCompleted(WorkflowStage stage) throws IOException {
        return stage != null && completedStages().contains(stage);
    }

    public List<String> readEventLines() throws IOException {
        Path events = eventsPath();
        if (!Files.isRegularFile(events)) {
            return Collections.emptyList();
        }
        List<String> lines = Files.readAllLines(events, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<String>();
        for (String line : lines) {
            if (!Strings.isBlank(line)) {
                out.add(line.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Refuse resume when state/events are missing, truncated, or sequence mismatches.
     * Events must be strictly sequenced {@code seq=1..N} matching {@code last_event_sequence}.
     */
    public void requireConsistentForResume() throws IOException {
        if (!Files.isRegularFile(statePath())) {
            throw new StageGateException("Corrupt run state — missing state.properties");
        }
        if (!Files.isRegularFile(eventsPath())) {
            throw new StageGateException("Corrupt run state — missing events.jsonl");
        }
        Properties p = loadProperties(statePath());
        long stated = parseLong(p.getProperty("last_event_sequence"), -1L);
        List<String> lines = readEventLines();
        if (stated < 0 || stated != lines.size()) {
            throw new StageGateException(
                    "Corrupt run state — last_event_sequence=" + stated
                            + " but events.jsonl has " + lines.size() + " line(s)");
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith("{") || !line.contains("\"seq\":") || !line.contains("\"type\":")) {
                throw new StageGateException(
                        "Corrupt run state — malformed events.jsonl line " + (i + 1));
            }
            long seq = parseJsonLongField(line, "seq", -1L);
            if (seq != (i + 1L)) {
                throw new StageGateException(
                        "Corrupt run state — events.jsonl seq must be 1..N contiguous; line "
                                + (i + 1) + " has seq=" + seq);
            }
            String type = jsonStringField(line, "type");
            if (Strings.isBlank(type)) {
                throw new StageGateException(
                        "Corrupt run state — events.jsonl line " + (i + 1) + " missing type");
            }
        }
        String terminal = p.getProperty("terminal");
        if (!Strings.isBlank(terminal)
                && ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE.name().equals(terminal)) {
            // already finished successfully — resume may still no-op delivery
            return;
        }
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

    private void appendEvent(String type, String stage, String terminal, String detail)
            throws IOException {
        sequence++;
        StringBuilder json = new StringBuilder(128);
        json.append('{');
        json.append("\"seq\":").append(sequence).append(',');
        json.append("\"type\":\"").append(escape(type)).append('"');
        if (!Strings.isBlank(stage)) {
            json.append(",\"stage\":\"").append(escape(stage)).append('"');
        }
        if (!Strings.isBlank(terminal)) {
            json.append(",\"terminal\":\"").append(escape(terminal)).append('"');
        }
        if (!Strings.isBlank(detail)) {
            json.append(",\"detail\":\"").append(escape(detail)).append('"');
        }
        json.append('}');
        Files.write(
                eventsPath(),
                (json.toString() + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        Properties p = readStateProperties();
        p.setProperty("last_event_sequence", Long.toString(sequence));
        if (!Strings.isBlank(stage)) {
            p.setProperty("stage", stage);
        }
        storeProperties(p);
    }

    private void rewriteState(
            String stage, String status, String terminal, String detail) throws IOException {
        Properties p = readStateProperties();
        p.setProperty("story_id", storyId);
        if (!Strings.isBlank(stage)) {
            p.setProperty("stage", stage);
        }
        if (!Strings.isBlank(status)) {
            p.setProperty("status", status);
        }
        if (!Strings.isBlank(terminal)) {
            p.setProperty("terminal", terminal);
        }
        if (!Strings.isBlank(detail)) {
            p.setProperty("detail", detail);
        }
        p.setProperty("last_event_sequence", Long.toString(sequence));
        storeProperties(p);
    }

    private Properties readStateProperties() throws IOException {
        if (!Files.isRegularFile(statePath())) {
            Properties p = new Properties();
            p.setProperty("story_id", storyId);
            p.setProperty("last_event_sequence", Long.toString(sequence));
            return p;
        }
        return loadProperties(statePath());
    }

    private void storeProperties(Properties p) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(statePath()), StandardCharsets.UTF_8))) {
            p.store(w, "ai4se production run state");
        }
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties p = new Properties();
        p.load(Files.newInputStream(path));
        return p;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
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

    private static long parseLong(String s, long dflt) {
        if (Strings.isBlank(s)) {
            return dflt;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static int parseInt(String s, int dflt) {
        if (Strings.isBlank(s)) {
            return dflt;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    public static final class RunStateSnapshot {
        public final String storyId;
        public final String stageOrNull;
        public final String statusOrNull;
        public final String terminalOrNull;
        public final long lastEventSequence;
        public final String failureFingerprintOrNull;
        public final String failureDiffHashOrNull;
        public final String writeScopeOrNull;
        public final int maxDevRoundsOrMinusOne;
        public final int roundsUsed;
        /** In-flight Development round (&gt;0), or 0 when between rounds. */
        public final int currentRound;
        /** Last settled {@link RoundOutcome} name, or null. */
        public final String lastRoundOutcomeOrNull;
        public final String adapterOrNull;
        public final String modelOrNull;
        public final String adapterProvenanceOrNull;
        public final String modelSelectionOrNull;

        public RunStateSnapshot(
                String storyId,
                String stageOrNull,
                String statusOrNull,
                String terminalOrNull,
                long lastEventSequence,
                String failureFingerprintOrNull,
                String failureDiffHashOrNull,
                String writeScopeOrNull,
                int maxDevRoundsOrMinusOne,
                int roundsUsed,
                int currentRound,
                String lastRoundOutcomeOrNull,
                String adapterOrNull,
                String modelOrNull,
                String adapterProvenanceOrNull,
                String modelSelectionOrNull) {
            this.storyId = storyId;
            this.stageOrNull = stageOrNull;
            this.statusOrNull = statusOrNull;
            this.terminalOrNull = terminalOrNull;
            this.lastEventSequence = lastEventSequence;
            this.failureFingerprintOrNull = failureFingerprintOrNull;
            this.failureDiffHashOrNull = failureDiffHashOrNull;
            this.writeScopeOrNull = writeScopeOrNull;
            this.maxDevRoundsOrMinusOne = maxDevRoundsOrMinusOne;
            this.roundsUsed = roundsUsed < 0 ? 0 : roundsUsed;
            this.currentRound = currentRound < 0 ? 0 : currentRound;
            this.lastRoundOutcomeOrNull = lastRoundOutcomeOrNull;
            this.adapterOrNull = adapterOrNull;
            this.modelOrNull = modelOrNull;
            this.adapterProvenanceOrNull = adapterProvenanceOrNull;
            this.modelSelectionOrNull = modelSelectionOrNull;
        }
    }
}
