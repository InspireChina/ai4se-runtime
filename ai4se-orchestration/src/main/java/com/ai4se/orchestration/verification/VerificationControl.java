package com.ai4se.orchestration.verification;

import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.development.DevPackageBuilder;
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

    /** Honest label: multi-entry conjunction of customer test exits. */
    public static final String VERDICT_BASIS = "customer_entries_all_exit_codes";

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

        StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
        List<String> acceptance = requirement.acceptance();
        if (acceptance.isEmpty()) {
            throw new StageGateException("P1 Acceptance missing — cannot Verify");
        }
        DevelopmentRecords.requireReadyForVerification(workspace, storyId);

        int round = VerifyPackageBuilder.nextRound(workspace, storyId);
        Path priorDefect = DefectPackageWriter.latest(workspace, storyId);
        Path pkg = VerifyPackageBuilder.build(workspace, storyId, round, normalized, priorDefect);
        requireEmbeddedP1(pkg);

        List<String> beforeBusiness = WorkspaceGit.businessChangedPaths(workspace, invoker);

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

        List<String> afterBusiness = WorkspaceGit.businessChangedPaths(workspace, invoker);
        List<String> mutated = newlyChanged(beforeBusiness, afterBusiness);
        if (!mutated.isEmpty()) {
            throw new StageGateException(
                    "Verification must not modify business source code: " + mutated);
        }

        boolean acceptanceMet = allOk;
        VerificationOutcome result = acceptanceMet ? VerificationOutcome.PASS : VerificationOutcome.FAIL;
        Path report = writeReport(
                workspace, storyId, round, normalized, results, result, pkg,
                acceptanceMet, acceptance, lastOutcome);

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
            String whyFailed = "VERIFY_FAIL exit=" + failingExit
                    + " failing_command=" + failingCommand
                    + " per_command=[" + perCmd + "]"
                    + " verdict_basis=" + VERDICT_BASIS
                    + " acceptance_scoring=not_performed_all_impacted_via_entry_fail"
                    + (Strings.isBlank(stderrExcerpt) ? "" : " stderr_excerpt=" + stderrExcerpt);
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
            DevPackageBuilder.build(workspace, storyId);
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

    private static Path writeReport(
            Path workspace,
            String storyId,
            int round,
            List<String> commands,
            List<CommandResult> results,
            VerificationOutcome outcome,
            Path pkg,
            boolean acceptanceMet,
            List<String> acceptance,
            ProcessInvoker.ProcessOutcome lastProcess) throws IOException {
        Path dir = reportsDir(workspace, storyId);
        Files.createDirectories(dir);
        Path path = dir.resolve("report-round-" + round + ".md");
        StringBuilder ac = new StringBuilder();
        String itemMark = acceptanceMet ? "asserted_via_entry_command" : "not_met_entry_failed";
        for (String item : acceptance) {
            ac.append("  - [").append(itemMark).append("] ").append(item).append('\n');
        }
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
        int lastExit = results.isEmpty() ? -1 : results.get(results.size() - 1).exitCode;
        boolean timedOut = lastProcess != null && lastProcess.timedOut;
        String body = ""
                + "# Verification Report\n\n"
                + "- round: " + round + "\n"
                + "- outcome: " + outcome.name() + "\n"
                + "- commands:\n" + cmdLines
                + "- exit_code: " + lastExit + "\n"
                + "- timed_out: " + timedOut + "\n"
                + "- command_ok: " + acceptanceMet + "\n"
                + "- acceptance_met: " + acceptanceMet + "\n"
                + "- verdict_basis: " + VERDICT_BASIS + "\n"
                + "- acceptance_item_scoring: not_performed\n"
                + "- observed: true\n"
                + "- package: " + pkg.toString() + "\n"
                + "- package_built_before_run: true\n"
                + "- business_code_mutated: false\n\n"
                + "## Per-command results\n\n"
                + resultLines
                + "\n## Acceptance covered / impacted\n\n"
                + ac
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
