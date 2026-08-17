package com.ai4se.runtime.demo.cli;

import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
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
import java.time.Duration;
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
        if ("resume".equals(cmd)) {
            return runResume(slice(args, 1));
        }
        if ("scorecard".equals(cmd)) {
            return runScorecard(slice(args, 1));
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
        System.out.println("  - Ends at AWAITING_HUMAN_ACCEPTANCE after local commit (never push).");
        System.out.println("  - Machine exit codes: 0/20/21/30/31/40/41/50 (see ProductionTerminal).");
        System.out.println("  - Resume continues from last stage_completed boundary (single Story).");
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

        private RunArgs(
                Path workspace,
                String storyId,
                Path requirement,
                List<String> writeScopes,
                int maxDevRounds,
                Duration adapterTimeout,
                String model,
                String adapter,
                RoleModelConfig roleModels) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.requirement = requirement;
            this.writeScopes = writeScopes;
            this.maxDevRounds = maxDevRounds;
            this.adapterTimeout = adapterTimeout;
            this.model = model;
            this.adapter = adapter;
            this.roleModels = roleModels;
        }

        RunArgs withWriteScopes(List<String> scopes) {
            return new RunArgs(
                    workspace, storyId, requirement, scopes, maxDevRounds, adapterTimeout, model, adapter, roleModels);
        }

        RunArgs withMaxDevRounds(int rounds) {
            return new RunArgs(
                    workspace, storyId, requirement, writeScopes, rounds, adapterTimeout, model, adapter, roleModels);
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
                    models.build());
        }
    }
}
