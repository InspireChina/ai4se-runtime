package com.ai4se.runtime.demo.cli;

import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.evaluation.ProductionRunScorecard;
import com.ai4se.orchestration.production.ProductionPathway;
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
            CursorCliAdapter cursor = cursorFor(parsed);
            System.out.println("AI4SE production run");
            System.out.println("workspace=" + request.workspace.toAbsolutePath().normalize());
            System.out.println("story=" + request.storyId);
            System.out.println("writeScope=" + request.writeScope);
            System.out.println("maxDevelopmentRounds=" + request.maxDevelopmentRounds);
            ProductionRunResult result = ProductionPathway.run(request, invoker, cursor);
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
            CursorCliAdapter cursor = cursorFor(parsed);
            System.out.println("AI4SE production resume");
            System.out.println("workspace=" + request.workspace.toAbsolutePath().normalize());
            System.out.println("story=" + request.storyId);
            ProductionRunResult result = ProductionPathway.resume(request, invoker, cursor);
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
            ProductionRunScorecard.Metrics m = ProductionRunScorecard.collect(a.workspace, a.storyId);
            System.out.print(m.toHumanSummary());
            System.out.println(ProductionRunScorecard.CSV_HEADER);
            System.out.println(ProductionRunScorecard.toCsvLine(
                    m, a.arm, a.humanInterventions, a.missedAcceptance,
                    a.diffVerdict, a.wallTimeSec, a.notes));
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

    private static CursorCliAdapter cursorFor(RunArgs parsed) {
        return Strings.isBlank(parsed.model)
                ? new CursorCliAdapter()
                : CursorCliAdapter.withModel(parsed.model);
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
        System.out.println("    [--max-dev-rounds N] [--timeout-minutes N] [--model <id>] \\");
        System.out.println("    [--model-analysis <id>] [--model-planning <id>] \\");
        System.out.println("    [--model-development <id>] [--model-review <id>]");
        System.out.println();
        System.out.println("  java -jar ai4se-runtime.jar status --workspace <dir> --story <id>");
        System.out.println("  java -jar ai4se-runtime.jar resume --workspace <dir> --story <id> \\");
        System.out.println("    [--write-scope ...]   # optional if stored in run/state.properties");
        System.out.println("  java -jar ai4se-runtime.jar scorecard --workspace <dir> --story <id> \\");
        System.out.println("    [--arm A|B] [--human-interventions N] [--missed-acceptance N] \\");
        System.out.println("    [--diff-verdict accept|minor_fix|reject] [--wall-time-sec N] [--notes text]");
        System.out.println();
        System.out.println("  java -jar ai4se-runtime.jar legacy-fixture \\");
        System.out.println("    --workspace <dir> --input <dir>");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - Production uses real CursorCliAdapter for Analysis/Plan/Dev/Review.");
        System.out.println("  - Ends at AWAITING_HUMAN_ACCEPTANCE after local commit (never push).");
        System.out.println("  - Machine exit codes: 0/20/21/30/31/40/41/50 (see ProductionTerminal).");
        System.out.println("  - Resume continues from last stage_completed boundary (single Story).");
        System.out.println("  - scorecard is read-only PR4 metrics (see docs/90-status/m1-pr4-real-story-ab-playbook.md).");
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
        final String humanInterventions;
        final String missedAcceptance;
        final String diffVerdict;
        final String wallTimeSec;
        final String notes;

        private ScorecardArgs(
                Path workspace,
                String storyId,
                String arm,
                String humanInterventions,
                String missedAcceptance,
                String diffVerdict,
                String wallTimeSec,
                String notes) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.arm = arm;
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
            if (!"A".equals(arm) && !"B".equals(arm)) {
                throw new IllegalArgumentException("--arm must be A or B");
            }
            return new ScorecardArgs(
                    workspace.toAbsolutePath().normalize(),
                    storyId.trim(),
                    arm,
                    humanInterventions,
                    missedAcceptance,
                    diffVerdict,
                    wallTimeSec,
                    notes);
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
        final RoleModelConfig roleModels;

        private RunArgs(
                Path workspace,
                String storyId,
                Path requirement,
                List<String> writeScopes,
                int maxDevRounds,
                Duration adapterTimeout,
                String model,
                RoleModelConfig roleModels) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.requirement = requirement;
            this.writeScopes = writeScopes;
            this.maxDevRounds = maxDevRounds;
            this.adapterTimeout = adapterTimeout;
            this.model = model;
            this.roleModels = roleModels;
        }

        RunArgs withWriteScopes(List<String> scopes) {
            return new RunArgs(
                    workspace, storyId, requirement, scopes, maxDevRounds, adapterTimeout, model, roleModels);
        }

        RunArgs withMaxDevRounds(int rounds) {
            return new RunArgs(
                    workspace, storyId, requirement, writeScopes, rounds, adapterTimeout, model, roleModels);
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
                            || "--adapter".equals(lower)
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
                    models.build());
        }
    }
}
