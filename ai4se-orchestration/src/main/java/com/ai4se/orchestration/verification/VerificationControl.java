package com.ai4se.orchestration.verification;

import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.support.CommandArgv;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * W7 Verification Control: build Verify Package first, then run entry command.
 * Verdict basis = customer entry exit (AC oracle is the customer suite), not compile-only,
 * not per-item LLM scoring. Report states that honestly.
 * FAIL→Defect→Dev (with Dev Package P1); re-Verify uses a new Verify Package.
 * Verify never mutates business sources (observed via git).
 */
public final class VerificationControl {

    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(30);

    /** Honest label: PASS/FAIL follows customer test entry exit, not silent per-AC scoring. */
    public static final String VERDICT_BASIS = "customer_entry_exit_code";

    private VerificationControl() {
    }

    public static Path reportsDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("verification");
    }

    /**
     * Package-then-run verification. Exit code is the AC oracle (customer suite);
     * report must not pretend item-level scoring was performed.
     */
    public static VerificationRecord run(
            Path workspace,
            String storyId,
            String command,
            ProcessInvoker invoker) throws IOException {
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required — Verification must run commands");
        }
        StoryWorkflowState state = StoryWorkflowMachine.load(workspace, storyId);
        if (state.stage() != WorkflowStage.VERIFICATION || !state.isRunnable()) {
            throw new StageGateException("Verification only when stage=VERIFICATION RUNNING");
        }
        VerificationEntries.requireAllowedCommand(workspace, command);
        if (VerificationEntries.isCompileOnly(command)) {
            throw new StageGateException("Compile-only command cannot be Acceptance PASS: " + command);
        }

        StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
        List<String> acceptance = requirement.acceptance();
        if (acceptance.isEmpty()) {
            throw new StageGateException("P1 Acceptance missing — cannot Verify");
        }
        DevelopmentRecords.requireReadyForVerification(workspace, storyId);

        int round = VerifyPackageBuilder.nextRound(workspace, storyId);
        Path priorDefect = DefectPackageWriter.latest(workspace, storyId);
        // S3 before execute: package must exist before the entry command runs
        Path pkg = VerifyPackageBuilder.build(workspace, storyId, round, command, priorDefect);
        requireEmbeddedP1(pkg);

        List<String> beforeBusiness = WorkspaceGit.businessChangedPaths(workspace, invoker);

        ProcessInvoker.ProcessOutcome outcome;
        try {
            outcome = invoker.run(
                    CommandArgv.shellCommand(command), workspace, null, VERIFY_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StageGateException("Verification interrupted");
        }

        List<String> afterBusiness = WorkspaceGit.businessChangedPaths(workspace, invoker);
        List<String> mutated = newlyChanged(beforeBusiness, afterBusiness);
        if (!mutated.isEmpty()) {
            throw new StageGateException(
                    "Verification must not modify business source code: " + mutated);
        }

        int exitCode = outcome.timedOut ? -1 : outcome.exitCode;
        boolean commandOk = !outcome.timedOut && exitCode == 0;
        // Product 05: customer entry is the Acceptance oracle. Do not claim per-item scoring.
        boolean acceptanceMet = commandOk;

        VerificationOutcome result = acceptanceMet ? VerificationOutcome.PASS : VerificationOutcome.FAIL;
        Path report = writeReport(
                workspace, storyId, round, command, exitCode, result, pkg,
                commandOk, acceptanceMet, acceptance, outcome);

        if (result == VerificationOutcome.FAIL) {
            Path defect = DefectPackageWriter.write(
                    workspace,
                    storyId,
                    round,
                    "command exit=" + exitCode
                            + (outcome.timedOut ? " timedOut" : "")
                            + " command_ok=false acceptance_met=false"
                            + " verdict_basis=" + VERDICT_BASIS,
                    acceptance,
                    DevelopmentRecords.hasValidRecord(workspace, storyId)
                            ? DevelopmentRecords.readChangedFiles(workspace, storyId)
                            : Collections.<String>emptyList(),
                    "keep Allowed unless Approval expands",
                    "do not expand beyond Allowed without gate",
                    "command: " + command + " ; report: " + report.getFileName());
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
            String command,
            int exitCode,
            VerificationOutcome outcome,
            Path pkg,
            boolean commandOk,
            boolean acceptanceMet,
            List<String> acceptance,
            ProcessInvoker.ProcessOutcome process) throws IOException {
        Path dir = reportsDir(workspace, storyId);
        Files.createDirectories(dir);
        Path path = dir.resolve("report-round-" + round + ".md");
        StringBuilder ac = new StringBuilder();
        String itemMark = acceptanceMet ? "asserted_via_entry_command" : "not_met_entry_failed";
        for (String item : acceptance) {
            ac.append("  - [").append(itemMark).append("] ").append(item).append('\n');
        }
        String body = ""
                + "# Verification Report\n\n"
                + "- round: " + round + "\n"
                + "- outcome: " + outcome.name() + "\n"
                + "- command: " + command + "\n"
                + "- exit_code: " + exitCode + "\n"
                + "- timed_out: " + process.timedOut + "\n"
                + "- command_ok: " + commandOk + "\n"
                + "- acceptance_met: " + acceptanceMet + "\n"
                + "- verdict_basis: " + VERDICT_BASIS + "\n"
                + "- acceptance_item_scoring: not_performed\n"
                + "- observed: true\n"
                + "- package: " + pkg.toString() + "\n"
                + "- package_built_before_run: true\n"
                + "- business_code_mutated: false\n\n"
                + "## Acceptance covered / impacted\n\n"
                + ac
                + "\n## Process pointer\n\n"
                + "- stdout_bytes: " + (process.stdout == null ? 0 : process.stdout.length()) + "\n"
                + "- stderr_bytes: " + (process.stderr == null ? 0 : process.stderr.length()) + "\n";
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
        return path;
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
