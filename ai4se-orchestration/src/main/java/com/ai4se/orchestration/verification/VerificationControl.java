package com.ai4se.orchestration.verification;

import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.support.CommandArgv;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Verification Control: build Verify Package first, then run entry command(s).
 * Verdict basis = customer entry exit codes (conjunction when multiple), not compile-only,
 * not per-item LLM scoring.
 */
public final class VerificationControl {

    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(30);

    /** Honest label: customer test entries and explicit quality gates are conjunctive. */
    public static final String VERDICT_BASIS =
            "customer_test_entries_and_quality_gates_all_exit_codes";

    private VerificationControl() {
    }

    public static Path reportsDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("verification");
    }

    /** Single-command overload — delegates to conjunction runner. */
    public static VerificationRecord run(
            Path workspace,
            String storyId,
            String command,
            ProcessInvoker invoker) throws IOException {
        if (Strings.isBlank(command)) {
            throw new StageGateException("Verification command required");
        }
        return run(workspace, storyId, Collections.singletonList(command.trim()), invoker);
    }

    /**
     * Run all listed commands; PASS only if every command exits 0.
     * Problem class: incomplete entry consumption — callers should pass Onboarding's full
     * usable test list unless explicitly overriding with disclosure.
     */
    public static VerificationRecord run(
            Path workspace,
            String storyId,
            List<String> commands,
            ProcessInvoker invoker) throws IOException {
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required — Verification must run commands");
        }
        if (commands == null || commands.isEmpty()) {
            throw new StageGateException("Verification requires at least one test command");
        }
        StoryWorkflowState state = StoryWorkflowMachine.load(workspace, storyId);
        if (state.stage() != WorkflowStage.VERIFICATION || !state.isRunnable()) {
            throw new StageGateException("Verification only when stage=VERIFICATION RUNNING");
        }

        List<String> normalized = new ArrayList<String>();
        for (String command : commands) {
            if (Strings.isBlank(command)) {
                continue;
            }
            String c = command.trim();
            VerificationEntries.requireAllowedCommand(workspace, c);
            if (VerificationEntries.isCompileOnly(c)) {
                throw new StageGateException("Compile-only command cannot be Acceptance PASS: " + c);
            }
            normalized.add(c);
        }
        if (normalized.isEmpty()) {
            throw new StageGateException("Verification requires at least one usable test command");
        }
        List<String> qualityGates = VerificationEntries.readUsableQualityGateCommands(workspace);
        normalized.addAll(qualityGates);

        StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
        List<String> acceptance = requirement.acceptance();
        if (acceptance.isEmpty()) {
            throw new StageGateException("P1 Acceptance missing — cannot Verify");
        }
        DevelopmentRecords.requireReadyForVerification(workspace, storyId);

        AcceptanceProbeSet probes = AcceptanceProbeSet.load(workspace, storyId, acceptance.size());
        int round = VerifyPackageBuilder.nextRound(workspace, storyId);
        Path priorDefect = DefectPackageWriter.latest(workspace, storyId);
        Path pkg = VerifyPackageBuilder.build(workspace, storyId, round, normalized, priorDefect);
        requireEmbeddedP1(pkg);

        List<String> beforeChanged = WorkspaceGit.changedPaths(workspace, invoker);
        probes.requireUnmodified(beforeChanged);
        List<String> beforeBusiness = businessPaths(beforeChanged);

        List<CommandResult> results = new ArrayList<CommandResult>();
        boolean allOk = true;
        String failingCommand = null;
        int failingExit = 0;
        ProcessInvoker.ProcessOutcome lastOutcome = null;
        for (String command : normalized) {
            ProcessInvoker.ProcessOutcome outcome;
            try {
                outcome = invoker.run(
                        CommandArgv.shellCommand(command), workspace, null, VERIFY_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StageGateException("Verification interrupted");
            } catch (IOException e) {
                throw new StageGateException(
                        "Verification ENV_FAIL: cannot launch test command '" + command + "': "
                                + e.getMessage());
            }
            lastOutcome = outcome;
            if (looksLikeEnvFailure(outcome, command)) {
                throw new StageGateException(
                        "Verification ENV_FAIL: shell/env failure for '" + command
                                + "' exit=" + outcome.exitCode
                                + " — not a customer test FAIL; will not enter Defect Loop. stderr="
                                + truncate(outcome.stderr));
            }
            int exitCode = outcome.timedOut ? -1 : outcome.exitCode;
            boolean commandOk = !outcome.timedOut && exitCode == 0;
            results.add(new CommandResult(command, exitCode, commandOk, outcome.timedOut));
            if (!commandOk) {
                allOk = false;
                failingCommand = command;
                failingExit = exitCode;
                break;
            }
        }

        boolean entryCommandsPassed = allOk;
        List<AcceptanceEvidence> acceptanceEvidence = new ArrayList<AcceptanceEvidence>();
        boolean probeFailed = false;
        if (entryCommandsPassed && probes.configured()) {
            for (AcceptanceProbeSet.Probe probe : probes.probes()) {
                ProcessInvoker.ProcessOutcome outcome;
                try {
                    outcome = invoker.run(
                            CommandArgv.shellCommand(probe.command), workspace, null, VERIFY_TIMEOUT);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StageGateException("Acceptance probe interrupted for AC" + probe.index);
                } catch (IOException e) {
                    throw new StageGateException(
                            "Acceptance probe ENV_FAIL for AC" + probe.index + ": " + e.getMessage());
                }
                if (looksLikeEnvFailure(outcome, probe.command)) {
                    throw new StageGateException(
                            "Acceptance probe ENV_FAIL for AC" + probe.index + " exit="
                                    + outcome.exitCode);
                }
                int exitCode = outcome.timedOut ? -1 : outcome.exitCode;
                boolean proven = !outcome.timedOut && exitCode == 0;
                acceptanceEvidence.add(new AcceptanceEvidence(
                        probe.index,
                        proven ? "PROVEN" : "FAILED",
                        probe.command,
                        exitCode,
                        outcome.timedOut,
                        probe.relativePath,
                        probe.sha256));
                if (!proven) {
                    probeFailed = true;
                }
                if (!probe.sha256.equals(AcceptanceProbeSet.sha256(probe.path))) {
                    throw new StageGateException(
                            "Frozen acceptance probe SHA changed during Verification for AC"
                                    + probe.index);
                }
            }
        } else {
            for (int i = 1; i <= acceptance.size(); i++) {
                acceptanceEvidence.add(new AcceptanceEvidence(
                        i, "UNPROVEN", "(no frozen probe executed)", -1, false, "-", "-"));
            }
        }

        List<String> afterChanged = WorkspaceGit.changedPaths(workspace, invoker);
        probes.requireUnmodified(afterChanged);
        List<String> afterBusiness = businessPaths(afterChanged);
        List<String> mutated = newlyChanged(beforeBusiness, afterBusiness);
        if (!mutated.isEmpty()) {
            throw new StageGateException(
                    "Verification must not modify business source code: " + mutated);
        }

        VerificationOutcome result = entryCommandsPassed && !probeFailed
                ? VerificationOutcome.PASS : VerificationOutcome.FAIL;
        List<String> changedForCoverage;
        try {
            changedForCoverage = DevelopmentRecords.hasValidRecord(workspace, storyId)
                    ? DevelopmentRecords.readChangedFiles(workspace, storyId)
                    : Collections.<String>emptyList();
        } catch (Exception e) {
            changedForCoverage = Collections.emptyList();
        }
        VerifyCoverageGap.Assessment coverage =
                VerifyCoverageGap.assess(changedForCoverage, normalized);
        Path report = writeReport(
                workspace, storyId, round, normalized, qualityGates, results, result, pkg,
                entryCommandsPassed, acceptanceEvidence, lastOutcome, coverage,
                !afterBusiness.isEmpty(), afterBusiness);

        if (result == VerificationOutcome.FAIL) {
            List<String> allowed;
            try {
                allowed = PlanRecords.readAllowedFiles(workspace, storyId);
            } catch (Exception e) {
                allowed = Collections.emptyList();
            }
            StringBuilder perCmd = new StringBuilder();
            for (CommandResult r : results) {
                if (perCmd.length() > 0) {
                    perCmd.append("; ");
                }
                perCmd.append(r.command)
                        .append(" exit=")
                        .append(r.exitCode)
                        .append(r.timedOut ? " timed_out" : "")
                        .append(r.ok ? " ok" : " FAIL");
            }
            String stderrExcerpt = truncate(lastOutcome == null ? null : lastOutcome.stderr);
            String stdoutExcerpt = truncate(lastOutcome == null ? null : lastOutcome.stdout);
            // Prefer stderr for fingerprint logs; many runners (mvn/npm) put failures on stdout only.
            String logField;
            String logExcerpt;
            if (!Strings.isBlank(stderrExcerpt)) {
                logField = "stderr_excerpt";
                logExcerpt = stderrExcerpt;
            } else if (!Strings.isBlank(stdoutExcerpt)) {
                logField = "stdout_excerpt";
                logExcerpt = stdoutExcerpt;
            } else {
                logField = null;
                logExcerpt = null;
            }
            String whyFailed = "VERIFY_FAIL exit=" + failingExit
                    + " failing_command=" + failingCommand
                    + " per_command=[" + perCmd + "]"
                    + " verdict_basis=" + VERDICT_BASIS
                    + " acceptance_evidence=" + acceptanceSummary(acceptanceEvidence)
                    + (logField == null ? "" : " " + logField + "=" + logExcerpt);
            String suggested = allowed.isEmpty()
                    ? "keep Allowed unless Approval expands"
                    : "keep within Allowed: " + joinPaths(allowed);
            String reproduce = "command: " + failingCommand
                    + " ; report: " + report.getFileName()
                    + (Strings.isBlank(stderrExcerpt) ? "" : " ; stderr_excerpt: " + stderrExcerpt)
                    + (Strings.isBlank(stdoutExcerpt) ? "" : " ; stdout_excerpt: " + stdoutExcerpt);
            Path defect = DefectPackageWriter.write(
                    workspace,
                    storyId,
                    round,
                    whyFailed,
                    acceptance,
                    DevelopmentRecords.hasValidRecord(workspace, storyId)
                            ? DevelopmentRecords.readChangedFiles(workspace, storyId)
                            : Collections.<String>emptyList(),
                    suggested,
                    "do not expand beyond Allowed without gate",
                    reproduce);
            StoryWorkflowMachine.returnToDevelopment(workspace, storyId, "Verify FAIL round-" + round);
            // Next Dev Package is built at the start of the next Development round (not here),
            // so Dev/Verify package rounds stay 1:1 with developmentRoundsUsed.
            return new VerificationRecord(result, round, report, pkg, defect);
        }
        return new VerificationRecord(result, round, report, pkg, null);
    }

    public static boolean hasPassReport(Path workspace, String storyId) throws IOException {
        Path dir = reportsDir(workspace, storyId);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        for (Path p : Files.newDirectoryStream(dir, "report-round-*.md")) {
            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            if (text.contains("outcome: PASS")) {
                return true;
            }
        }
        return false;
    }

    public static void requirePassBeforeReview(Path workspace, String storyId) throws IOException {
        if (!hasPassReport(workspace, storyId)) {
            throw new StageGateException("No Verification PASS report — cannot enter Review");
        }
    }

    /** True only when the latest Verification PASS includes a frozen, successful probe per AC. */
    public static boolean allAcceptanceProven(Path workspace, String storyId) throws IOException {
        Path dir = reportsDir(workspace, storyId);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        Path latest = null;
        int max = -1;
        for (Path p : Files.newDirectoryStream(dir, "report-round-*.md")) {
            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            if (!text.contains("outcome: PASS")) {
                continue;
            }
            String name = p.getFileName().toString();
            try {
                int round = Integer.parseInt(
                        name.substring("report-round-".length(), name.length() - ".md".length()));
                if (round > max) {
                    max = round;
                    latest = p;
                }
            } catch (NumberFormatException ignored) {
                if (latest == null) {
                    latest = p;
                }
            }
        }
        if (latest == null) {
            return false;
        }
        String text = new String(Files.readAllBytes(latest), StandardCharsets.UTF_8);
        return text.contains("acceptance_all_proven: true");
    }

    static void requireEmbeddedP1(Path pkg) throws IOException {
        Path acceptance = pkg.resolve("slices/acceptance.md");
        Path diff = pkg.resolve("slices/diff.md");
        Path entry = pkg.resolve("slices/entry.md");
        if (!Files.isRegularFile(acceptance) || !Files.isRegularFile(diff) || !Files.isRegularFile(entry)) {
            throw new StageGateException("Verify Package missing embedded P1 slices");
        }
        String ac = new String(Files.readAllBytes(acceptance), StandardCharsets.UTF_8);
        if (!ac.contains("- ") || ac.contains("See .story/")) {
            throw new StageGateException("Verify Package acceptance must be embedded, not pointer-only");
        }
        String d = new String(Files.readAllBytes(diff), StandardCharsets.UTF_8);
        if (d.contains("See .story/") && !d.contains("- ")) {
            throw new StageGateException("Verify Package diff must be embedded, not pointer-only");
        }
    }

    private static List<String> newlyChanged(List<String> before, List<String> after) {
        Set<String> prior = new LinkedHashSet<String>(before);
        List<String> neu = new ArrayList<String>();
        for (String p : after) {
            if (!prior.contains(p)) {
                neu.add(p);
            }
        }
        return neu;
    }

    private static List<String> businessPaths(List<String> changed) {
        List<String> business = new ArrayList<String>();
        if (changed == null) {
            return business;
        }
        for (String path : changed) {
            if (!WorkspaceGit.isIgnorableForMutation(path)) {
                business.add(path);
            }
        }
        return business;
    }

    private static Path writeReport(
            Path workspace,
            String storyId,
            int round,
            List<String> commands,
            List<String> qualityGates,
            List<CommandResult> results,
            VerificationOutcome outcome,
            Path pkg,
            boolean entryCommandsPassed,
            List<AcceptanceEvidence> acceptanceEvidence,
            ProcessInvoker.ProcessOutcome lastProcess,
            VerifyCoverageGap.Assessment coverage,
            boolean businessCodeMutated,
            List<String> businessChangedPaths) throws IOException {
        Path dir = reportsDir(workspace, storyId);
        Files.createDirectories(dir);
        Path path = dir.resolve("report-round-" + round + ".md");
        StringBuilder ac = new StringBuilder();
        boolean allProven = !acceptanceEvidence.isEmpty();
        boolean anyFailed = false;
        for (AcceptanceEvidence evidence : acceptanceEvidence) {
            allProven = allProven && "PROVEN".equals(evidence.verdict);
            anyFailed = anyFailed || "FAILED".equals(evidence.verdict);
            ac.append("  - ac: AC").append(evidence.index)
                    .append(" | verdict: ").append(evidence.verdict)
                    .append(" | command: ").append(evidence.command)
                    .append(" | exit_code: ").append(evidence.exitCode)
                    .append(" | timed_out: ").append(evidence.timedOut)
                    .append(" | probe_path: ").append(evidence.probePath)
                    .append(" | probe_sha256: ").append(evidence.probeSha256)
                    .append('\n');
        }
        String acceptanceMetValue = allProven
                ? "proven_all" : (anyFailed ? "failed" : "unproven");
        StringBuilder cmdLines = new StringBuilder();
        for (String c : commands) {
            cmdLines.append("  - ").append(c).append('\n');
        }
        StringBuilder resultLines = new StringBuilder();
        for (CommandResult r : results) {
            resultLines.append("  - command: ").append(r.command)
                    .append(" | exit_code: ").append(r.exitCode)
                    .append(" | ok: ").append(r.ok)
                    .append(" | timed_out: ").append(r.timedOut)
                    .append('\n');
        }
        VerifyCoverageGap.Assessment cov = coverage == null
                ? VerifyCoverageGap.assess(Collections.<String>emptyList(), commands)
                : coverage;
        String changeEvidence = businessChangeEvidence(workspace, businessChangedPaths);
        int lastExit = results.isEmpty() ? -1 : results.get(results.size() - 1).exitCode;
        boolean timedOut = lastProcess != null && lastProcess.timedOut;
        String body = ""
                + "# Verification Report\n\n"
                + "- round: " + round + "\n"
                + "- outcome: " + outcome.name() + "\n"
                + "- commands:\n" + cmdLines
                + "- exit_code: " + lastExit + "\n"
                + "- timed_out: " + timedOut + "\n"
                + "- entry_commands_passed: " + entryCommandsPassed + "\n"
                + "- quality_gate_count: " + (qualityGates == null ? 0 : qualityGates.size()) + "\n"
                + "- quality_gates_passed: " + entryCommandsPassed + "\n"
                + "- command_ok: " + entryCommandsPassed + "\n"
                + "- acceptance_met: " + acceptanceMetValue + "\n"
                + "- acceptance_all_proven: " + allProven + "\n"
                + "- verdict_basis: " + VERDICT_BASIS + "\n"
                + "- acceptance_item_scoring: frozen_probe\n"
                + "- coverage_gap: " + cov.gapLabel() + "\n"
                + "- coverage_gap_detail: " + cov.detailLine() + "\n"
                + "- observed: true\n"
                + "- package: " + pkg.toString() + "\n"
                + "- package_built_before_run: true\n"
                + "- business_code_mutated: " + businessCodeMutated + "\n"
                + "- business_changed_paths: " + joinPaths(businessChangedPaths) + "\n\n"
                + "## Per-command results\n\n"
                + resultLines
                + "\n## Coverage disclosure\n\n"
                + "- Diff×entry integrity: disclosure_only (not a hard gate)\n"
                + "- coverage_gap: " + cov.gapLabel() + "\n"
                + "- " + cov.detailLine() + "\n"
                + "\n## Acceptance evidence\n\n"
                + ac
                + "\n## Business change evidence (bounded current-file snapshot)\n\n"
                + changeEvidence
                + "\n## Process pointer\n\n"
                + "- stdout_bytes: "
                + (lastProcess == null || lastProcess.stdout == null ? 0 : lastProcess.stdout.length())
                + "\n"
                + "- stderr_bytes: "
                + (lastProcess == null || lastProcess.stderr == null ? 0 : lastProcess.stderr.length())
                + "\n"
                + "- stderr_excerpt: "
                + truncate(lastProcess == null ? null : lastProcess.stderr)
                + "\n"
                + "- stdout_excerpt: "
                + truncate(lastProcess == null ? null : lastProcess.stdout)
                + "\n";
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static String acceptanceSummary(List<AcceptanceEvidence> evidence) {
        StringBuilder out = new StringBuilder();
        for (AcceptanceEvidence item : evidence) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append("AC").append(item.index).append('=').append(item.verdict);
        }
        return out.toString();
    }

    private static String businessChangeEvidence(Path workspace, List<String> paths) throws IOException {
        if (paths == null || paths.isEmpty()) {
            return "- (no business working-tree paths observed)\n";
        }
        StringBuilder out = new StringBuilder();
        int remaining = 16000;
        for (String relative : paths) {
            Path file = workspace.resolve(relative).normalize();
            out.append("### ").append(relative).append("\n\n");
            if (!file.startsWith(workspace) || !Files.isRegularFile(file)) {
                out.append("(deleted, non-regular, or unavailable)\n\n");
                continue;
            }
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            int take = Math.min(text.length(), Math.max(0, remaining));
            out.append("```text\n").append(text.substring(0, take)).append("\n```\n\n");
            remaining -= take;
            if (remaining <= 0) {
                out.append("(snapshot truncated)\n");
                break;
            }
        }
        return out.toString();
    }

    private static final class AcceptanceEvidence {
        final int index;
        final String verdict;
        final String command;
        final int exitCode;
        final boolean timedOut;
        final String probePath;
        final String probeSha256;

        AcceptanceEvidence(
                int index,
                String verdict,
                String command,
                int exitCode,
                boolean timedOut,
                String probePath,
                String probeSha256) {
            this.index = index;
            this.verdict = verdict;
            this.command = command;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.probePath = probePath;
            this.probeSha256 = probeSha256;
        }
    }

    /**
     * Environment / shell failures are Orchestration ENV_FAIL — must not become Defect Loop fuel.
     * Customer assertion red (non-zero exit without env signatures) remains VERIFY_FAIL.
     */
    static boolean looksLikeEnvFailure(ProcessInvoker.ProcessOutcome outcome, String command) {
        if (outcome == null) {
            return false;
        }
        int code = outcome.exitCode;
        if (code == 127) {
            return true;
        }
        String err = ((outcome.stderr == null ? "" : outcome.stderr)
                + "\n"
                + (outcome.stdout == null ? "" : outcome.stdout)).toLowerCase(Locale.ROOT);
        if (err.contains("windows subsystem for linux")
                || err.contains("wsl.exe")
                || (err.contains("wsl") && err.contains("install"))) {
            return true;
        }
        if (err.contains("is not recognized as an internal or external command")) {
            return true;
        }
        if (err.contains("createprocess error") || err.contains("error=2") || err.contains("error=193")) {
            return true;
        }
        if (err.contains("no such file or directory")
                && command != null
                && (command.contains("bash") || err.contains("/bin/bash") || err.contains("bash:"))) {
            return true;
        }
        if (err.contains("command not found")) {
            return true;
        }
        return false;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim().replace('\n', ' ');
        return t.length() <= 240 ? t : t.substring(0, 240) + "...";
    }

    private static String joinPaths(List<String> paths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(paths.get(i));
        }
        return sb.toString();
    }

    private static final class CommandResult {
        final String command;
        final int exitCode;
        final boolean ok;
        final boolean timedOut;

        CommandResult(String command, int exitCode, boolean ok, boolean timedOut) {
            this.command = command;
            this.exitCode = exitCode;
            this.ok = ok;
            this.timedOut = timedOut;
        }
    }

    public static final class VerificationRecord {
        public final VerificationOutcome outcome;
        public final int round;
        public final Path report;
        public final Path verifyPackage;
        public final Path defectOrNull;

        public VerificationRecord(
                VerificationOutcome outcome,
                int round,
                Path report,
                Path verifyPackage,
                Path defectOrNull) {
            this.outcome = outcome;
            this.round = round;
            this.report = report;
            this.verifyPackage = verifyPackage;
            this.defectOrNull = defectOrNull;
        }
    }
}
