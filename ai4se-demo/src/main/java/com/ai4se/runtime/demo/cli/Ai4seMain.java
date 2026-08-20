package com.ai4se.runtime.demo.cli;

import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.story.StoryIntake;
import com.ai4se.orchestration.specification.SpecificationAdapterExecution;
import com.ai4se.orchestration.specification.SpecificationRecords;
import com.ai4se.orchestration.discovery.DiscoveryAdapterExecution;
import com.ai4se.orchestration.lifecycle.KnowledgeLifecycleControl;
import com.ai4se.orchestration.queue.SerialStoryQueue;
import com.ai4se.orchestration.verification.AcceptanceProbeCandidates;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.ClarificationRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.acceptance.HumanAcceptanceRecords;
import com.ai4se.orchestration.evaluation.ProductionRunScorecard;
import com.ai4se.orchestration.production.ProductionPathway;
import com.ai4se.orchestration.production.ProductionAdapterRegistry;
import com.ai4se.orchestration.production.ProductionRunRequest;
import com.ai4se.orchestration.production.ProductionRunResult;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.runtime.common.util.Strings;
import com.ai4se.runtime.demo.input.ProductionRuntimeMain;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Formal product CLI for M1 Story production runs.
 *
 * <pre>
 * java -jar ai4se-runtime.jar run \
 *   --workspace /repo \
 *   --story story-123 \
 *   --requirement /tmp/story-123.md \
 *   --write-scope src/main/java \
 *   --write-scope src/test/java \
 *   --max-dev-rounds 3
 *
 * java -jar ai4se-runtime.jar status --workspace /repo --story story-123
 * java -jar ai4se-runtime.jar resume --workspace /repo --story story-123
 * java -jar ai4se-runtime.jar scorecard --workspace /repo --story story-123 --arm B
 *
 * java -jar ai4se-runtime.jar legacy-fixture --workspace ... --input ...
 * </pre>
 */
public final class Ai4seMain {

    private Ai4seMain() {
    }

    public static void main(String[] args) throws Exception {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    /** Testable entry — returns process exit code without {@link System#exit}. */
    public static int run(String[] args) throws Exception {
        if (args == null || args.length == 0 || isHelp(args[0])) {
            printHelp();
            return args == null || args.length == 0 ? 2 : 0;
        }
        String cmd = args[0].trim().toLowerCase(Locale.ROOT);
        if ("legacy-fixture".equals(cmd)) {
            String[] rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            ProductionRuntimeMain.main(rest);
            return 0;
        }
        if ("status".equals(cmd)) {
            return runStatus(slice(args, 1));
        }
        if ("onboard".equals(cmd)) {
            return runOnboard(slice(args, 1));
        }
        if ("discover".equals(cmd)) {
            return runDiscover(slice(args, 1));
        }
        if ("approve-knowledge".equals(cmd)) {
            return runApproveKnowledge(slice(args, 1));
        }
        if ("knowledge".equals(cmd)) {
            return runKnowledge(slice(args, 1));
        }
        if ("intake".equals(cmd)) {
            return runIntake(slice(args, 1));
        }
        if ("specify".equals(cmd)) {
            return runSpecify(slice(args, 1));
        }
        if ("answer-spec".equals(cmd)) {
            return runAnswerSpecification(slice(args, 1));
        }
        if ("freeze-spec".equals(cmd)) {
            return runFreezeSpecification(slice(args, 1));
        }
        if ("queue".equals(cmd)) {
            return runQueue(slice(args, 1));
        }
        if ("freeze-probes".equals(cmd)) {
            return runFreezeProbes(slice(args, 1));
        }
        if ("resume".equals(cmd)) {
            return runResume(slice(args, 1));
        }
        if ("scorecard".equals(cmd)) {
            return runScorecard(slice(args, 1));
        }
        if ("answer".equals(cmd)) {
            return runAnswer(slice(args, 1));
        }
        if ("approve-plan".equals(cmd)) {
            return runApprovePlan(slice(args, 1));
        }
        if ("accept".equals(cmd)) {
            return runAccept(slice(args, 1));
        }
        if ("reject".equals(cmd)) {
            return runReject(slice(args, 1));
        }
        if (!"run".equals(cmd)) {
            System.err.println("Unknown command: " + args[0]);
            printHelp();
            return 2;
        }
        try {
            RunArgs parsed = RunArgs.parse(slice(args, 1), true);
            ProductionRunRequest request = toRequest(parsed);
            ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
            com.ai4se.execution.api.ModelCliAdapter adapter = adapterFor(parsed, invoker);
            System.out.println("AI4SE production run");
            System.out.println("workspace=" + request.workspace.toAbsolutePath().normalize());
            System.out.println("story=" + request.storyId);
            System.out.println("writeScope=" + request.writeScope);
            System.out.println("maxDevelopmentRounds=" + request.maxDevelopmentRounds);
            System.out.println("adapter=" + adapter.name());
            ProductionRunResult result = ProductionPathway.run(request, invoker, adapter);
            if (parsed.interactive) {
                result = resolveAnalysisClarificationsInteractively(request, invoker, adapter, result);
            }
            printResult(result);
            return result.exitCode;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runStatus(String[] args) throws Exception {
        try {
            StatusArgs a = StatusArgs.parse(args);
            System.out.print(ProductionPathway.formatStatus(a.workspace, a.storyId));
            return 0;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runOnboard(String[] args) throws Exception {
        try {
            OnboardArgs a = OnboardArgs.parse(args);
            Path script = a.runtimeRoot.resolve("scripts/onboard-repo.sh").normalize();
            OnboardRepoScript.run(a.workspace, script);
            System.out.println("onboard=complete");
            System.out.println("next=verify .ai4se/repository/entries.yaml and fill confirmed knowledge/rules");
            return 0;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    /** Builds one source-grounded candidate knowledge set; it never starts a Story or edits source. */
    private static int runDiscover(String[] args) throws Exception {
        try {
            DiscoveryArgs a = DiscoveryArgs.parse(args);
            ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
            com.ai4se.execution.api.ModelCliAdapter adapter =
                    ProductionAdapterRegistry.create(a.adapter, invoker, a.model);
            String candidateId = a.candidateId == null
                    ? "discovery-" + a.scope.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase(Locale.ROOT)
                            .replaceAll("^-+|-+$", "") + "-" + Instant.now().toEpochMilli()
                    : a.candidateId;
            DiscoveryAdapterExecution.Outcome outcome = DiscoveryAdapterExecution.submit(
                    a.workspace, candidateId, a.scope, adapter, invoker, a.timeout);
            System.out.println("discovery=CANDIDATE_READY");
            System.out.println("candidate=" + outcome.candidateId());
            System.out.println("candidateRoot=" + outcome.candidateRoot());
            System.out.println("documents=" + outcome.documentCount());
            System.out.println("sourceCommit=" + outcome.sourceCommit());
            System.out.println("next=review candidate documents and run approve-knowledge explicitly");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runApproveKnowledge(String[] args) throws Exception {
        try {
            KnowledgeApprovalArgs a = KnowledgeApprovalArgs.parse(args);
            List<Path> promoted = KnowledgeLifecycleControl.approveDiscoveryCandidate(
                    a.workspace, a.candidateId, a.actor, new ProcessInvoker.RealProcessInvoker());
            System.out.println("knowledge=VERIFIED");
            System.out.println("promoted=" + promoted.size());
            for (Path path : promoted) {
                System.out.println("path=" + path);
            }
            System.out.println("next=review and commit .ai4se/knowledge + .ai4se/index before Story work");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runKnowledge(String[] args) throws Exception {
        try {
            KnowledgeStatusArgs a = KnowledgeStatusArgs.parse(args);
            System.out.print(KnowledgeLifecycleControl.formatKnowledgeStatus(a.workspace));
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    /** Captures raw text and optional media as immutable Story input; it never starts development. */
    private static int runIntake(String[] args) throws Exception {
        try {
            IntakeArgs a = IntakeArgs.parse(args);
            String raw = a.requestFile == null
                    ? a.text
                    : new String(java.nio.file.Files.readAllBytes(a.requestFile), StandardCharsets.UTF_8);
            StoryIntake.IntakeResult result = StoryIntake.capture(a.workspace, a.storyId, raw, a.attachments);
            System.out.println("intake=RAW_CAPTURED");
            System.out.println("storyDir=" + result.storyDir());
            System.out.println("rawRequest=" + result.rawRequest());
            System.out.println("attachments=" + result.attachmentCount());
            System.out.println("next=specify (produce candidate requirement or clarification questions; no business code has run)");
            return 0;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runSpecify(String[] args) throws Exception {
        try {
            SpecificationArgs a = SpecificationArgs.parse(args);
            ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
            com.ai4se.execution.api.ModelCliAdapter adapter =
                    ProductionAdapterRegistry.create(a.adapter, invoker, a.model);
            SpecificationRecords.Outcome outcome = SpecificationAdapterExecution.submit(
                    a.workspace, a.storyId, adapter, a.timeout);
            System.out.println("specification=" + outcome.name());
            if (outcome == SpecificationRecords.Outcome.CANDIDATE) {
                System.out.println("next=review candidate-requirement.md, then freeze-spec");
            } else {
                System.out.println("next=answer-spec, then specify (the answer becomes P1)");
            }
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runAnswerSpecification(String[] args) throws Exception {
        try {
            HumanDecisionArgs a = HumanDecisionArgs.parseAnswer(args);
            SpecificationRecords.writeClarificationAnswer(a.workspace, a.storyId, a.value, a.actor);
            System.out.println("specification_clarification=recorded");
            System.out.println("next=specify (the model must re-evaluate this answer)");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runFreezeSpecification(String[] args) throws Exception {
        try {
            StatusArgs a = StatusArgs.parse(args);
            Path requirement = SpecificationRecords.freezeCandidate(a.workspace, a.storyId);
            System.out.println("requirement=FROZEN");
            System.out.println("path=" + requirement);
            System.out.println("next=run (Production Analysis may still ask business clarification)");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runQueue(String[] args) throws Exception {
        try {
            QueueArgs a = QueueArgs.parse(args);
            if ("add".equals(a.action)) {
                SerialStoryQueue.add(a.workspace, a.storyId);
                System.out.println("queue=ADDED story=" + a.storyId);
            }
            System.out.print(SerialStoryQueue.format(a.workspace));
            return 0;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runFreezeProbes(String[] args) throws Exception {
        try {
            StatusArgs a = StatusArgs.parse(args);
            Path root = AcceptanceProbeCandidates.freeze(a.workspace, a.storyId);
            System.out.println("acceptance_probes=FROZEN");
            System.out.println("path=" + root);
            System.out.println("next=approve-plan, then resume for unattended Development");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runResume(String[] args) throws Exception {
        try {
            RunArgs parsed = RunArgs.parse(args, false);
            if (parsed.writeScopes.isEmpty()) {
                RunLedger ledger = RunLedger.open(parsed.workspace, parsed.storyId);
                ledger.requireConsistentForResume();
                RunLedger.RunStateSnapshot snap = ledger.readState();
                if (!Strings.isBlank(snap.writeScopeOrNull)) {
                    parsed = parsed.withWriteScopes(Arrays.asList(snap.writeScopeOrNull.split(",")));
                }
                if (snap.maxDevRoundsOrMinusOne > 0) {
                    parsed = parsed.withMaxDevRounds(snap.maxDevRoundsOrMinusOne);
                }
            }
            if (parsed.answersFile != null) {
                if (!java.nio.file.Files.isRegularFile(parsed.answersFile)) {
                    throw new IllegalArgumentException("--answers file not found: " + parsed.answersFile);
                }
                String answer = new String(
                        java.nio.file.Files.readAllBytes(parsed.answersFile), StandardCharsets.UTF_8);
                ClarificationRecords.writeResolvedAnswer(
                        parsed.workspace, parsed.storyId, answer, "operator-cli");
            }
            if (parsed.approvePlan) {
                ApprovalRecords.approvePlan(
                        parsed.workspace,
                        parsed.storyId,
                        "operator-cli",
                        parsed.approvalNote);
            }
            if (parsed.writeScopes.isEmpty()) {
                throw new IllegalArgumentException(
                        "resume requires --write-scope or write_scope in run/state.properties");
            }
            ProductionRunRequest request = toRequest(parsed);
            ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
            com.ai4se.execution.api.ModelCliAdapter adapter = adapterFor(parsed, invoker);
            System.out.println("AI4SE production resume");
            System.out.println("workspace=" + request.workspace.toAbsolutePath().normalize());
            System.out.println("story=" + request.storyId);
            System.out.println("adapter=" + adapter.name());
            ProductionRunResult result = ProductionPathway.resume(request, invoker, adapter);
            printResult(result);
            return result.exitCode;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static int runScorecard(String[] args) throws Exception {
        try {
            ScorecardArgs a = ScorecardArgs.parse(args);
            ProductionRunScorecard.ExperimentHints hints =
                    ProductionRunScorecard.ExperimentHints.empty();
            hints.pairId = a.pairId;
            hints.arm = a.arm;
            hints.baselineCommit = a.baselineCommit;
            hints.modelId = a.modelId;
            hints.inputTokens = a.inputTokens;
            hints.outputTokens = a.outputTokens;
            hints.toolCalls = a.toolCalls;
            hints.humanInterventions = a.humanInterventions;
            hints.missedAcceptance = a.missedAcceptance;
            hints.diffVerdict = a.diffVerdict;
            hints.wallTimeSec = a.wallTimeSec;
            hints.notes = a.notes;
            ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
            ProductionRunScorecard.Metrics m = ProductionRunScorecard.collect(
                    a.workspace, a.storyId, invoker, hints);
            System.out.print(m.toHumanSummary());
            System.out.println(ProductionRunScorecard.CSV_HEADER);
            System.out.println(ProductionRunScorecard.toCsvLine(m));
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    /** Captures a human clarification answer; {@code resume} will submit it to Analysis again. */
    private static int runAnswer(String[] args) throws Exception {
        try {
            HumanDecisionArgs a = HumanDecisionArgs.parseAnswer(args);
            ClarificationRecords.writeResolvedAnswer(a.workspace, a.storyId, a.value, a.actor);
            System.out.println("clarification=recorded");
            System.out.println("next=resume (Analysis will re-evaluate the answer before Planning)");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    /** Records the user's approval of the generated plan; {@code resume} then starts Development. */
    private static int runApprovePlan(String[] args) throws Exception {
        try {
            HumanDecisionArgs a = HumanDecisionArgs.parseApproval(args);
            ApprovalRecords.approvePlan(a.workspace, a.storyId, a.actor, a.value);
            System.out.println("plan_approval=recorded");
            System.out.println("next=resume (unattended Development -> Verify -> Review -> local commit)");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    /** Records the real customer's post-delivery acceptance; it never pushes or writes learning. */
    private static int runAccept(String[] args) throws Exception {
        try {
            HumanDecisionArgs a = HumanDecisionArgs.parseAcceptance(args, "acceptance note");
            HumanAcceptanceRecords.recordAccepted(
                    a.workspace, a.storyId, a.actor, a.value, HumanAcceptanceRecords.Kind.HUMAN);
            System.out.println("human_acceptance=ACCEPTED");
            System.out.println("next=optionally record an operator-owned lifecycle noop or learning item");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    /** Records rejection as evidence; it does not discard the local delivery or hide its probes. */
    private static int runReject(String[] args) throws Exception {
        try {
            HumanDecisionArgs a = HumanDecisionArgs.parseAcceptance(args, "rejection reason");
            HumanAcceptanceRecords.recordRejected(a.workspace, a.storyId, a.actor, a.value);
            System.out.println("human_acceptance=REJECTED");
            System.out.println("next=create a follow-up Story; this delivery evidence remains immutable");
            return 0;
        } catch (StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        } catch (IllegalArgumentException e) {
            if ("help".equals(e.getMessage())) {
                return 0;
            }
            System.err.println("BAD ARGS: " + e.getMessage());
            printHelp();
            return 2;
        }
    }

    private static void printResult(ProductionRunResult result) {
        System.out.println("terminal=" + result.terminalStatus);
        System.out.println("exitCode=" + result.exitCode);
        System.out.println("commit=" + (result.commitShaOrNull == null ? "-" : result.commitShaOrNull));
        System.out.println("evidence=" + (result.evidenceRoot == null ? "-" : result.evidenceRoot));
        System.out.println("runDir=" + (result.runDirOrNull == null ? "-" : result.runDirOrNull));
        if (result.succeeded()) {
            System.out.println("Human acceptance is awaiting — AI4SE does not forge S5.");
        } else if (!Strings.isBlank(result.detailOrNull)) {
            System.out.println("detail=" + result.detailOrNull);
        }
    }

    /**
     * Terminal form of interrupt/resume. It handles only a concrete Analysis BLOCKED question;
     * it never auto-answers, auto-approves a Plan, or turns a policy/test stop into a retry loop.
     */
    private static ProductionRunResult resolveAnalysisClarificationsInteractively(
            ProductionRunRequest request,
            ProcessInvoker invoker,
            com.ai4se.execution.api.ModelCliAdapter adapter,
            ProductionRunResult initial) throws Exception {
        ProductionRunResult result = initial;
        BufferedReader terminal = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (int turns = 0; turns < 3 && ClarificationRecords.hasPending(request.workspace, request.storyId); turns++) {
            Path questions = request.workspace.resolve(".story").resolve(request.storyId)
                    .resolve("analysis").resolve(ClarificationRecords.QUESTIONS_FILE);
            if (!java.nio.file.Files.isRegularFile(questions)) {
                return result;
            }
            System.out.println("\nAI4SE clarification interrupt (" + (turns + 1) + "/3):");
            System.out.println(new String(java.nio.file.Files.readAllBytes(questions), StandardCharsets.UTF_8));
            System.out.print("Answer (one line; empty input leaves the run stopped): ");
            String answer = terminal.readLine();
            if (Strings.isBlank(answer)) {
                System.out.println("No answer received; run remains stopped. Use answer + resume later.");
                return result;
            }
            ClarificationRecords.writeResolvedAnswer(
                    request.workspace, request.storyId, answer, "interactive-cli");
            result = ProductionPathway.resume(request, invoker, adapter);
            if (!ClarificationRecords.hasPending(request.workspace, request.storyId)) {
                return result;
            }
        }
        if (ClarificationRecords.hasPending(request.workspace, request.storyId)) {
            System.out.println("Interactive clarification budget reached; run remains stopped for a later answer + resume.");
        }
        return result;
    }

    private static ProductionRunRequest toRequest(RunArgs parsed) {
        return ProductionRunRequest.builder(parsed.workspace, parsed.storyId)
                .seedRequirement(parsed.requirement)
                .writeScopes(parsed.writeScopes)
                .adapterTimeout(parsed.adapterTimeout)
                .maxDevelopmentRounds(parsed.maxDevRounds)
                .roleModels(parsed.roleModels)
                .build();
    }

    private static com.ai4se.execution.api.ModelCliAdapter adapterFor(
            RunArgs parsed, ProcessInvoker invoker) {
        return ProductionAdapterRegistry.create(parsed.adapter, invoker, parsed.model);
    }

    static void printHelp() {
        System.out.println("AI4SE Runtime — Story production CLI");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar ai4se-runtime.jar run \\");
        System.out.println("    --workspace <git-repo> \\");
        System.out.println("    --story <story-id> \\");
        System.out.println("    --requirement <seed.md> \\");
        System.out.println("    --write-scope <rel-path-or-dir> [--write-scope ...] \\");
        System.out.println("    [--adapter cursor|codex|claude] [--max-dev-rounds N] [--timeout-minutes N] [--model <id>] \\");
        System.out.println("    [--model-analysis <id>] [--model-planning <id>] \\");
        System.out.println("    [--model-development <id>] [--model-review <id>]");
        System.out.println();
        System.out.println("  java -jar ai4se-runtime.jar status --workspace <dir> --story <id>");
        System.out.println("  java -jar ai4se-runtime.jar onboard --workspace <dir> --runtime-root <ai4se-runtime>");
        System.out.println("  java -jar ai4se-runtime.jar discover --workspace <dir> --scope repository|module:<id> \\");
        System.out.println("    [--candidate <id>] [--adapter cursor|codex|claude] [--model <id>] [--timeout-minutes N]");
        System.out.println("  java -jar ai4se-runtime.jar approve-knowledge --workspace <dir> --candidate <id> [--actor <name>]");
        System.out.println("  java -jar ai4se-runtime.jar knowledge status --workspace <dir>");
        System.out.println("  java -jar ai4se-runtime.jar intake --workspace <dir> --story <id> \\");
        System.out.println("    (--text <request> | --request-file <request.md>) [--attachment <file> ...]");
        System.out.println("  java -jar ai4se-runtime.jar specify --workspace <dir> --story <id> \\");
        System.out.println("    [--adapter cursor|codex|claude] [--model <id>] [--timeout-minutes N]");
        System.out.println("  java -jar ai4se-runtime.jar answer-spec --workspace <dir> --story <id> \\");
        System.out.println("    --answer <text> [--actor <name>]");
        System.out.println("  java -jar ai4se-runtime.jar freeze-spec --workspace <dir> --story <id>");
        System.out.println("  java -jar ai4se-runtime.jar freeze-probes --workspace <dir> --story <id>");
        System.out.println("  java -jar ai4se-runtime.jar queue add --workspace <dir> --story <id>");
        System.out.println("  java -jar ai4se-runtime.jar queue status --workspace <dir>");
        System.out.println("  java -jar ai4se-runtime.jar answer --workspace <dir> --story <id> \\");
        System.out.println("    --answer <text> [--actor <name>]");
        System.out.println("  java -jar ai4se-runtime.jar approve-plan --workspace <dir> --story <id> \\");
        System.out.println("    [--note <text>] [--actor <name>]");
        System.out.println("  java -jar ai4se-runtime.jar accept --workspace <dir> --story <id> \\");
        System.out.println("    --note <customer acceptance note> [--actor <name>]");
        System.out.println("  java -jar ai4se-runtime.jar reject --workspace <dir> --story <id> \\");
        System.out.println("    --note <customer rejection reason> [--actor <name>]");
        System.out.println("  java -jar ai4se-runtime.jar resume --workspace <dir> --story <id> \\");
        System.out.println("    [--write-scope ...]   # optional if stored in run/state.properties");
        System.out.println("  java -jar ai4se-runtime.jar scorecard --workspace <dir> --story <id> \\");
        System.out.println("    --arm B --pair-id <id> --baseline-commit <sha> --model-id <id> \\");
        System.out.println("    [--input-tokens N] [--output-tokens N] [--tool-calls N] \\");
        System.out.println("    [--human-interventions N] [--missed-acceptance N] \\");
        System.out.println("    [--diff-verdict accept|minor_fix|reject] [--wall-time-sec N] [--notes text]");
        System.out.println("    # arm A is not accepted here — use the baseline/manual collector path");
        System.out.println();
        System.out.println("  java -jar ai4se-runtime.jar legacy-fixture \\");
        System.out.println("    --workspace <dir> --input <dir>");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - Production uses only registered cursor-cli, codex-cli, or claude-cli adapters.");
        System.out.println("  - onboard is deterministic facts; discover creates source-cited candidate knowledge only; approve-knowledge is an explicit human promotion.");
        System.out.println("  - intake freezes raw request/media first; it cannot be treated as a developable requirement.");
        System.out.println("  - specify creates a candidate requirement or questions; freeze-spec is the human decision to make it runnable.");
        System.out.println("  - freeze-probes validates the reviewed Plan candidate against every AC before Development can start.");
        System.out.println("  - queue is serial selection only: it skips cards awaiting answers and never auto-approves or runs concurrent writes.");
        System.out.println("  - Ends at AWAITING_HUMAN_ACCEPTANCE after local commit (never push).");
        System.out.println("  - Machine exit codes: 0/20/21/30/31/40/41/50 (see ProductionTerminal).");
        System.out.println("  - Resume continues from last stage_completed boundary (single Story).");
        System.out.println("  - answer records the human response; the next Analysis turn must re-evaluate it.");
        System.out.println("  - --interactive turns a concrete Analysis clarification into at most three terminal interrupt/resume turns; it never auto-approves a Plan.");
        System.out.println("  - approve-plan is the final human gate before unattended implementation.");
        System.out.println("  - accept/reject is a separate post-delivery customer decision; neither command pushes code.");
        System.out.println("  - scorecard is a read-only run-metrics view used by the evidence collector.");
    }

    private static boolean isHelp(String a) {
        String s = a == null ? "" : a.trim().toLowerCase(Locale.ROOT);
        return "--help".equals(s) || "-h".equals(s) || "help".equals(s);
    }

    private static String[] slice(String[] args, int from) {
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }

    static final class StatusArgs {
        final Path workspace;
        final String storyId;

        private StatusArgs(Path workspace, String storyId) {
            this.workspace = workspace;
            this.storyId = storyId;
        }

        static StatusArgs parse(String[] args) {
            Path workspace = null;
            String storyId = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("--workspace required");
            }
            if (Strings.isBlank(storyId)) {
                throw new IllegalArgumentException("--story required");
            }
            return new StatusArgs(workspace.toAbsolutePath().normalize(), storyId.trim());
        }
    }

    static final class OnboardArgs {
        final Path workspace;
        final Path runtimeRoot;

        private OnboardArgs(Path workspace, Path runtimeRoot) {
            this.workspace = workspace;
            this.runtimeRoot = runtimeRoot;
        }

        static OnboardArgs parse(String[] args) {
            Path workspace = null;
            Path runtimeRoot = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--runtime-root".equals(a) && i + 1 < args.length) {
                    runtimeRoot = Paths.get(args[++i]);
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("--workspace required");
            }
            if (runtimeRoot == null) {
                throw new IllegalArgumentException("--runtime-root required (contains scripts/onboard-repo.sh)");
            }
            return new OnboardArgs(
                    workspace.toAbsolutePath().normalize(), runtimeRoot.toAbsolutePath().normalize());
        }
    }

    static final class IntakeArgs {
        final Path workspace;
        final String storyId;
        final String text;
        final Path requestFile;
        final List<Path> attachments;

        private IntakeArgs(
                Path workspace, String storyId, String text, Path requestFile, List<Path> attachments) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.text = text;
            this.requestFile = requestFile;
            this.attachments = attachments;
        }

        static IntakeArgs parse(String[] args) {
            Path workspace = null;
            String storyId = null;
            String text = null;
            Path requestFile = null;
            List<Path> attachments = new ArrayList<Path>();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if ("--text".equals(a) && i + 1 < args.length) {
                    text = args[++i];
                } else if ("--request-file".equals(a) && i + 1 < args.length) {
                    requestFile = Paths.get(args[++i]);
                } else if ("--attachment".equals(a) && i + 1 < args.length) {
                    attachments.add(Paths.get(args[++i]));
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("--workspace required");
            }
            if (Strings.isBlank(storyId)) {
                throw new IllegalArgumentException("--story required");
            }
            if ((Strings.isBlank(text) && requestFile == null)
                    || (!Strings.isBlank(text) && requestFile != null)) {
                throw new IllegalArgumentException("exactly one of --text or --request-file required");
            }
            if (requestFile != null && !java.nio.file.Files.isRegularFile(requestFile)) {
                throw new IllegalArgumentException("--request-file not found: " + requestFile);
            }
            return new IntakeArgs(
                    workspace.toAbsolutePath().normalize(), storyId.trim(), text, requestFile,
                    java.util.Collections.unmodifiableList(new ArrayList<Path>(attachments)));
        }
    }

    static final class SpecificationArgs {
        final Path workspace;
        final String storyId;
        final String adapter;
        final String model;
        final Duration timeout;

        private SpecificationArgs(Path workspace, String storyId, String adapter, String model, Duration timeout) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.adapter = adapter;
            this.model = model;
            this.timeout = timeout;
        }

        static SpecificationArgs parse(String[] args) {
            Path workspace = null;
            String storyId = null;
            String adapter = "cursor";
            String model = null;
            int timeoutMinutes = 10;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if ("--adapter".equals(a) && i + 1 < args.length) {
                    adapter = ProductionAdapterRegistry.normalize(args[++i]);
                } else if ("--model".equals(a) && i + 1 < args.length) {
                    model = args[++i];
                } else if ("--timeout-minutes".equals(a) && i + 1 < args.length) {
                    timeoutMinutes = Integer.parseInt(args[++i]);
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null || Strings.isBlank(storyId)) {
                throw new IllegalArgumentException("--workspace and --story required");
            }
            if (timeoutMinutes < 1) {
                throw new IllegalArgumentException("--timeout-minutes must be >= 1");
            }
            return new SpecificationArgs(workspace.toAbsolutePath().normalize(), storyId.trim(), adapter,
                    model, Duration.ofMinutes(timeoutMinutes));
        }
    }

    static final class DiscoveryArgs {
        final Path workspace;
        final String scope;
        final String candidateId;
        final String adapter;
        final String model;
        final Duration timeout;

        private DiscoveryArgs(
                Path workspace,
                String scope,
                String candidateId,
                String adapter,
                String model,
                Duration timeout) {
            this.workspace = workspace;
            this.scope = scope;
            this.candidateId = candidateId;
            this.adapter = adapter;
            this.model = model;
            this.timeout = timeout;
        }

        static DiscoveryArgs parse(String[] args) {
            Path workspace = null;
            String scope = null;
            String candidate = null;
            String adapter = "cursor";
            String model = null;
            int timeoutMinutes = 15;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--scope".equals(a) && i + 1 < args.length) {
                    scope = args[++i];
                } else if ("--candidate".equals(a) && i + 1 < args.length) {
                    candidate = args[++i];
                } else if ("--adapter".equals(a) && i + 1 < args.length) {
                    adapter = ProductionAdapterRegistry.normalize(args[++i]);
                } else if ("--model".equals(a) && i + 1 < args.length) {
                    model = args[++i];
                } else if ("--timeout-minutes".equals(a) && i + 1 < args.length) {
                    timeoutMinutes = Integer.parseInt(args[++i]);
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null || Strings.isBlank(scope)) {
                throw new IllegalArgumentException("--workspace and --scope required");
            }
            String cleanScope = scope.trim();
            if (!("repository".equals(cleanScope) || cleanScope.matches("module:[a-zA-Z0-9._-]+"))) {
                throw new IllegalArgumentException("--scope must be repository or module:<id>");
            }
            if (candidate != null) {
                candidate = com.ai4se.context.discovery.DiscoveryPackageBuilder.normalizeCandidateId(candidate);
            }
            if (timeoutMinutes < 1 || timeoutMinutes > 30) {
                throw new IllegalArgumentException("--timeout-minutes must be between 1 and 30");
            }
            return new DiscoveryArgs(workspace.toAbsolutePath().normalize(), cleanScope, candidate,
                    adapter, model, Duration.ofMinutes(timeoutMinutes));
        }
    }

    static final class KnowledgeApprovalArgs {
        final Path workspace;
        final String candidateId;
        final String actor;

        private KnowledgeApprovalArgs(Path workspace, String candidateId, String actor) {
            this.workspace = workspace;
            this.candidateId = candidateId;
            this.actor = actor;
        }

        static KnowledgeApprovalArgs parse(String[] args) {
            Path workspace = null;
            String candidate = null;
            String actor = "operator-cli";
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--candidate".equals(a) && i + 1 < args.length) {
                    candidate = args[++i];
                } else if ("--actor".equals(a) && i + 1 < args.length) {
                    actor = args[++i];
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null || Strings.isBlank(candidate) || Strings.isBlank(actor)) {
                throw new IllegalArgumentException("--workspace, --candidate and nonblank --actor required");
            }
            return new KnowledgeApprovalArgs(
                    workspace.toAbsolutePath().normalize(),
                    com.ai4se.context.discovery.DiscoveryPackageBuilder.normalizeCandidateId(candidate),
                    actor.trim());
        }
    }

    static final class KnowledgeStatusArgs {
        final Path workspace;

        private KnowledgeStatusArgs(Path workspace) {
            this.workspace = workspace;
        }

        static KnowledgeStatusArgs parse(String[] args) {
            if (args == null || args.length == 0 || !"status".equalsIgnoreCase(args[0])) {
                throw new IllegalArgumentException("knowledge requires action: status");
            }
            Path workspace = null;
            for (int i = 1; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("--workspace required");
            }
            return new KnowledgeStatusArgs(workspace.toAbsolutePath().normalize());
        }
    }

    static final class QueueArgs {
        final String action;
        final Path workspace;
        final String storyId;

        private QueueArgs(String action, Path workspace, String storyId) {
            this.action = action;
            this.workspace = workspace;
            this.storyId = storyId;
        }

        static QueueArgs parse(String[] args) {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("queue action add|status required");
            }
            String action = args[0].trim().toLowerCase(Locale.ROOT);
            if (!"add".equals(action) && !"status".equals(action)) {
                throw new IllegalArgumentException("queue action must be add|status");
            }
            Path workspace = null;
            String storyId = null;
            for (int i = 1; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("--workspace required");
            }
            if ("add".equals(action) && Strings.isBlank(storyId)) {
                throw new IllegalArgumentException("queue add requires --story");
            }
            if ("status".equals(action) && !Strings.isBlank(storyId)) {
                throw new IllegalArgumentException("queue status does not accept --story");
            }
            return new QueueArgs(action, workspace.toAbsolutePath().normalize(),
                    Strings.isBlank(storyId) ? null : storyId.trim());
        }
    }

    static final class HumanDecisionArgs {
        final Path workspace;
        final String storyId;
        final String value;
        final String actor;

        private HumanDecisionArgs(Path workspace, String storyId, String value, String actor) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.value = value;
            this.actor = actor;
        }

        static HumanDecisionArgs parseAnswer(String[] args) {
            return parse(args, "--answer", null, "operator-cli", "human answer");
        }

        static HumanDecisionArgs parseApproval(String[] args) {
            return parse(args, "--note", "Approved through AI4SE CLI", "operator-cli", "approval note");
        }

        static HumanDecisionArgs parseAcceptance(String[] args, String label) {
            return parse(args, "--note", null, "operator-cli", label);
        }

        private static HumanDecisionArgs parse(
                String[] args, String valueFlag, String defaultValue, String defaultActor, String label) {
            Path workspace = null;
            String storyId = null;
            String value = defaultValue;
            String actor = defaultActor;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if (valueFlag.equals(a) && i + 1 < args.length) {
                    value = args[++i];
                } else if ("--actor".equals(a) && i + 1 < args.length) {
                    actor = args[++i];
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("--workspace required");
            }
            if (Strings.isBlank(storyId)) {
                throw new IllegalArgumentException("--story required");
            }
            if (Strings.isBlank(value)) {
                throw new IllegalArgumentException(label + " required");
            }
            if (Strings.isBlank(actor)) {
                throw new IllegalArgumentException("--actor must not be blank");
            }
            return new HumanDecisionArgs(
                    workspace.toAbsolutePath().normalize(), storyId.trim(), value.trim(), actor.trim());
        }
    }

    static final class ScorecardArgs {
        final Path workspace;
        final String storyId;
        final String arm;
        final String pairId;
        final String baselineCommit;
        final String modelId;
        final String inputTokens;
        final String outputTokens;
        final String toolCalls;
        final String humanInterventions;
        final String missedAcceptance;
        final String diffVerdict;
        final String wallTimeSec;
        final String notes;

        private ScorecardArgs(
                Path workspace,
                String storyId,
                String arm,
                String pairId,
                String baselineCommit,
                String modelId,
                String inputTokens,
                String outputTokens,
                String toolCalls,
                String humanInterventions,
                String missedAcceptance,
                String diffVerdict,
                String wallTimeSec,
                String notes) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.arm = arm;
            this.pairId = pairId;
            this.baselineCommit = baselineCommit;
            this.modelId = modelId;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.toolCalls = toolCalls;
            this.humanInterventions = humanInterventions;
            this.missedAcceptance = missedAcceptance;
            this.diffVerdict = diffVerdict;
            this.wallTimeSec = wallTimeSec;
            this.notes = notes;
        }

        static ScorecardArgs parse(String[] args) {
            Path workspace = null;
            String storyId = null;
            String arm = "B";
            String pairId = "";
            String baselineCommit = "";
            String modelId = "";
            String inputTokens = "";
            String outputTokens = "";
            String toolCalls = "";
            String humanInterventions = "";
            String missedAcceptance = "";
            String diffVerdict = "";
            String wallTimeSec = "";
            String notes = "";
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if ("--arm".equals(a) && i + 1 < args.length) {
                    arm = args[++i].trim().toUpperCase(Locale.ROOT);
                } else if ("--pair-id".equals(a) && i + 1 < args.length) {
                    pairId = args[++i];
                } else if ("--baseline-commit".equals(a) && i + 1 < args.length) {
                    baselineCommit = args[++i];
                } else if ("--model-id".equals(a) && i + 1 < args.length) {
                    modelId = args[++i];
                } else if ("--input-tokens".equals(a) && i + 1 < args.length) {
                    inputTokens = args[++i];
                } else if ("--output-tokens".equals(a) && i + 1 < args.length) {
                    outputTokens = args[++i];
                } else if ("--tool-calls".equals(a) && i + 1 < args.length) {
                    toolCalls = args[++i];
                } else if ("--human-interventions".equals(a) && i + 1 < args.length) {
                    humanInterventions = args[++i];
                } else if ("--missed-acceptance".equals(a) && i + 1 < args.length) {
                    missedAcceptance = args[++i];
                } else if ("--diff-verdict".equals(a) && i + 1 < args.length) {
                    diffVerdict = args[++i];
                } else if ("--wall-time-sec".equals(a) && i + 1 < args.length) {
                    wallTimeSec = args[++i];
                } else if ("--notes".equals(a) && i + 1 < args.length) {
                    notes = args[++i];
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("--workspace required");
            }
            if (Strings.isBlank(storyId)) {
                throw new IllegalArgumentException("--story required");
            }
            if ("A".equals(arm)) {
                throw new IllegalArgumentException(
                        "scorecard rejects --arm A; use the baseline/manual collector for arm A");
            }
            if (!"B".equals(arm)) {
                throw new IllegalArgumentException("--arm must be B for this ledger collector");
            }
            if (Strings.isBlank(pairId)) {
                throw new IllegalArgumentException("--pair-id required for arm B");
            }
            if (Strings.isBlank(baselineCommit)) {
                throw new IllegalArgumentException("--baseline-commit required for arm B");
            }
            if (Strings.isBlank(modelId)) {
                throw new IllegalArgumentException("--model-id required for arm B");
            }
            inputTokens = requireNonNegOrBlank(inputTokens, "--input-tokens");
            outputTokens = requireNonNegOrBlank(outputTokens, "--output-tokens");
            toolCalls = requireNonNegOrBlank(toolCalls, "--tool-calls");
            humanInterventions = requireNonNegOrBlank(humanInterventions, "--human-interventions");
            missedAcceptance = requireNonNegOrBlank(missedAcceptance, "--missed-acceptance");
            wallTimeSec = requireNonNegOrBlank(wallTimeSec, "--wall-time-sec");
            diffVerdict = requireDiffVerdictOrBlank(diffVerdict);
            return new ScorecardArgs(
                    workspace.toAbsolutePath().normalize(),
                    storyId.trim(),
                    arm,
                    pairId.trim(),
                    baselineCommit.trim(),
                    modelId.trim(),
                    inputTokens,
                    outputTokens,
                    toolCalls,
                    humanInterventions,
                    missedAcceptance,
                    diffVerdict,
                    wallTimeSec,
                    notes);
        }

        private static String requireNonNegOrBlank(String raw, String flag) {
            if (Strings.isBlank(raw)) {
                return "";
            }
            String t = raw.trim();
            if ("na".equalsIgnoreCase(t)) {
                return "na";
            }
            try {
                long n = Long.parseLong(t);
                if (n < 0L) {
                    throw new IllegalArgumentException(flag + " must be non-negative or na");
                }
                return Long.toString(n);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(flag + " must be a non-negative integer or na");
            }
        }

        private static String requireDiffVerdictOrBlank(String raw) {
            if (Strings.isBlank(raw)) {
                return "";
            }
            String t = raw.trim().toLowerCase(Locale.ROOT);
            if ("accept".equals(t) || "minor_fix".equals(t) || "reject".equals(t)) {
                return t;
            }
            throw new IllegalArgumentException(
                    "--diff-verdict must be accept|minor_fix|reject");
        }
    }

    static final class RunArgs {
        final Path workspace;
        final String storyId;
        final Path requirement;
        final List<String> writeScopes;
        final int maxDevRounds;
        final Duration adapterTimeout;
        final String model;
        final String adapter;
        final RoleModelConfig roleModels;
        final Path answersFile;
        final boolean approvePlan;
        final boolean interactive;
        final String approvalNote;

        private RunArgs(
                Path workspace,
                String storyId,
                Path requirement,
                List<String> writeScopes,
                int maxDevRounds,
                Duration adapterTimeout,
                String model,
                String adapter,
                RoleModelConfig roleModels,
                Path answersFile,
                boolean approvePlan,
                boolean interactive,
                String approvalNote) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.requirement = requirement;
            this.writeScopes = writeScopes;
            this.maxDevRounds = maxDevRounds;
            this.adapterTimeout = adapterTimeout;
            this.model = model;
            this.adapter = adapter;
            this.roleModels = roleModels;
            this.answersFile = answersFile;
            this.approvePlan = approvePlan;
            this.interactive = interactive;
            this.approvalNote = approvalNote;
        }

        RunArgs withWriteScopes(List<String> scopes) {
            return new RunArgs(
                    workspace, storyId, requirement, scopes, maxDevRounds, adapterTimeout, model, adapter, roleModels,
                    answersFile, approvePlan, interactive, approvalNote);
        }

        RunArgs withMaxDevRounds(int rounds) {
            return new RunArgs(
                    workspace, storyId, requirement, writeScopes, rounds, adapterTimeout, model, adapter, roleModels,
                    answersFile, approvePlan, interactive, approvalNote);
        }

        /**
         * @param requireWriteScope when false (resume), write-scope may be restored from run state
         */
        static RunArgs parse(String[] args, boolean requireWriteScope) {
            Path workspace = null;
            String storyId = null;
            Path requirement = null;
            List<String> writeScopes = new ArrayList<String>();
            int maxDevRounds = 3;
            int timeoutMinutes = 15;
            String model = null;
            String adapter = "cursor";
            RoleModelConfig.Builder models = RoleModelConfig.builder();
            Path answersFile = null;
            boolean approvePlan = false;
            boolean interactive = false;
            String approvalNote = "Approved through AI4SE CLI resume";
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if (("--requirement".equals(a) || "--seed".equals(a)) && i + 1 < args.length) {
                    requirement = Paths.get(args[++i]);
                } else if ("--write-scope".equals(a) && i + 1 < args.length) {
                    writeScopes.add(args[++i]);
                } else if ("--max-dev-rounds".equals(a) && i + 1 < args.length) {
                    maxDevRounds = Integer.parseInt(args[++i]);
                } else if ("--timeout-minutes".equals(a) && i + 1 < args.length) {
                    timeoutMinutes = Integer.parseInt(args[++i]);
                } else if ("--model".equals(a) && i + 1 < args.length) {
                    model = args[++i];
                    models.defaultModel(model);
                } else if ("--adapter".equals(a) && i + 1 < args.length) {
                    adapter = ProductionAdapterRegistry.normalize(args[++i]);
                } else if ("--model-analysis".equals(a) && i + 1 < args.length) {
                    models.role("analysis", args[++i]);
                } else if ("--model-planning".equals(a) && i + 1 < args.length) {
                    models.role("planning", args[++i]);
                } else if ("--model-development".equals(a) && i + 1 < args.length) {
                    models.role("development", args[++i]);
                } else if ("--model-review".equals(a) && i + 1 < args.length) {
                    models.role("review", args[++i]);
                } else if ("--answers".equals(a) && i + 1 < args.length) {
                    answersFile = Paths.get(args[++i]);
                } else if ("--approve-plan".equals(a)) {
                    approvePlan = true;
                } else if ("--approval-note".equals(a) && i + 1 < args.length) {
                    approvalNote = args[++i];
                } else if ("--interactive".equals(a)) {
                    interactive = true;
                } else if (isHelp(a)) {
                    printHelp();
                    throw new IllegalArgumentException("help");
                } else if (a != null && a.startsWith("--")) {
                    String lower = a.toLowerCase(Locale.ROOT);
                    if ("--suite".equals(lower)
                            || "--wave".equals(lower)
                            || "--fixture".equals(lower)
                            || "--review-fixture".equals(lower)
                            || "--seeded".equals(lower)
                            || "--hybrid".equals(lower)
                            || "--dev-mutation".equals(lower)
                            || "--script".equals(lower)
                            || "--v4-fail-mode".equals(lower)
                            || "--allowed".equals(lower)
                            || "--verify-command".equals(lower)) {
                        throw new IllegalArgumentException(
                                "Unsupported production flag (fixture/legacy): " + a);
                    }
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + a);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("--workspace required");
            }
            if (Strings.isBlank(storyId)) {
                throw new IllegalArgumentException("--story required");
            }
            if (requireWriteScope && writeScopes.isEmpty()) {
                throw new IllegalArgumentException("at least one --write-scope required");
            }
            if (maxDevRounds < 1) {
                throw new IllegalArgumentException("--max-dev-rounds must be >= 1");
            }
            return new RunArgs(
                    workspace.toAbsolutePath().normalize(),
                    storyId.trim(),
                    requirement == null ? null : requirement.toAbsolutePath().normalize(),
                    writeScopes,
                    maxDevRounds,
                    Duration.ofMinutes(timeoutMinutes),
                    model,
                    adapter,
                    models.build(),
                    answersFile == null ? null : answersFile.toAbsolutePath().normalize(),
                    approvePlan,
                    interactive,
                    approvalNote);
        }
    }
}
