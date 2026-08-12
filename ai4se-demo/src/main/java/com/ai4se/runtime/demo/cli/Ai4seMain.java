package com.ai4se.runtime.demo.cli;

import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.production.ProductionPathway;
import com.ai4se.orchestration.production.ProductionRunRequest;
import com.ai4se.orchestration.production.ProductionRunResult;
import com.ai4se.runtime.common.util.Strings;
import com.ai4se.runtime.demo.input.ProductionRuntimeMain;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
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
        if (!"run".equals(cmd)) {
            System.err.println("Unknown command: " + args[0]);
            printHelp();
            return 2;
        }
        try {
            RunArgs parsed = RunArgs.parse(slice(args, 1));
            ProductionRunRequest request = ProductionRunRequest.builder(parsed.workspace, parsed.storyId)
                    .seedRequirement(parsed.requirement)
                    .writeScopes(parsed.writeScopes)
                    .adapterTimeout(parsed.adapterTimeout)
                    .maxDevelopmentRounds(parsed.maxDevRounds)
                    .roleModels(parsed.roleModels)
                    .build();
            ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
            CursorCliAdapter cursor = Strings.isBlank(parsed.model)
                    ? new CursorCliAdapter()
                    : CursorCliAdapter.withModel(parsed.model);
            System.out.println("AI4SE production run");
            System.out.println("workspace=" + request.workspace.toAbsolutePath().normalize());
            System.out.println("story=" + request.storyId);
            System.out.println("writeScope=" + request.writeScope);
            System.out.println("maxDevelopmentRounds=" + request.maxDevelopmentRounds
                    + " (stored for PR2; PR1 uses single V3 pass)");
            ProductionRunResult result = ProductionPathway.run(request, invoker, cursor);
            System.out.println("terminal=" + result.terminalStatus);
            System.out.println("commit=" + (result.commitShaOrNull == null ? "-" : result.commitShaOrNull));
            System.out.println("evidence=" + result.evidenceRoot);
            System.out.println("Human acceptance is awaiting — AI4SE does not forge S5.");
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
        System.out.println("  java -jar ai4se-runtime.jar legacy-fixture \\");
        System.out.println("    --workspace <dir> --input <dir>");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - Production uses real CursorCliAdapter for Analysis/Plan/Dev/Review.");
        System.out.println("  - Ends at AWAITING_HUMAN_ACCEPTANCE after local commit (never push).");
        System.out.println("  - Formal run CLI only: workspace/story/requirement/write-scope/model/timeout.");
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

        static RunArgs parse(String[] args) {
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
            if (writeScopes.isEmpty()) {
                throw new IllegalArgumentException("at least one --write-scope required");
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
