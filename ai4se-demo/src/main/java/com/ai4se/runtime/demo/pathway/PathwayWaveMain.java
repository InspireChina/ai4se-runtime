package com.ai4se.runtime.demo.pathway;

import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.packagebuild.PackageRefuseException;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.context.workspace.WorkspaceSlotException;
import com.ai4se.context.workspace.WorkspaceSlotVerifier;
import com.ai4se.orchestration.workflow.IllegalWorkflowTransitionException;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Pathway Wave CLI (debug aid). Acceptance is {@code mvn test}, not re-running this by hand.
 * Unmanned V3/V4: prefer {@link com.ai4se.orchestration.pathway.PathwayRunner}.
 * Does <b>not</b> open Cursor/Claude CLI (W4).
 */
public final class PathwayWaveMain {

    private PathwayWaveMain() {
    }

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        Path workspace = parsed.workspace.toAbsolutePath().normalize();
        if (!Files.isDirectory(workspace)) {
            fail("workspace not found: " + workspace);
        }

        if (parsed.openStory) {
            if (parsed.storyId == null) {
                fail("--open-story requires --story <id>");
            }
            Path seed = parsed.seed == null ? null : parsed.seed.toAbsolutePath().normalize();
            Path dest = StoryOpener.open(workspace, parsed.storyId, seed);
            System.out.println("Story opened: " + dest);
            return;
        }

        String wave = parsed.wave == null ? "w2" : parsed.wave.toLowerCase();
        if ("w1".equals(wave)) {
            requireSlots(workspace);
            System.out.println("W1 OK — slots present; workspace=" + workspace);
            return;
        }
        if ("w2".equals(wave)) {
            requireSlots(workspace);
            if (parsed.storyId == null) {
                fail("W2 requires --story <id>");
            }
            try {
                ContextPackageResult pkg = AnalysisPackageBuilder.build(workspace, parsed.storyId);
                System.out.println("W2 OK — Analysis package built");
                System.out.println("  role     = " + pkg.role());
                System.out.println("  story    = " + pkg.storyId());
                System.out.println("  manifest = " + pkg.manifestPath());
                System.out.println("  P1       = " + pkg.priority1());
            } catch (PackageRefuseException refuse) {
                System.err.println("W2 REFUSE — " + refuse.getMessage());
                System.exit(2);
            }
            return;
        }
        if ("w3".equals(wave)) {
            requireSlots(workspace);
            if (parsed.storyId == null) {
                fail("W3 requires --story <id>");
            }
            try {
                if (!Files.isRegularFile(StoryWorkflowMachine.statePath(workspace, parsed.storyId))) {
                    StoryWorkflowMachine.start(workspace, parsed.storyId);
                }
                StoryWorkflowState before = StoryWorkflowMachine.load(workspace, parsed.storyId);
                if (before.stage() == WorkflowStage.ANALYSIS && before.isRunnable()) {
                    StoryWorkflowState after = StoryWorkflowMachine.advance(workspace, parsed.storyId);
                    System.out.println("W3 OK — advanced " + before.stage() + " → " + after.stage());
                    System.out.println("  state = " + StoryWorkflowMachine.statePath(workspace, parsed.storyId));
                } else {
                    System.out.println("W3 OK — current stage=" + before.stage()
                            + " status=" + before.status());
                    System.out.println("  state = " + StoryWorkflowMachine.statePath(workspace, parsed.storyId));
                }
            } catch (IllegalWorkflowTransitionException e) {
                System.err.println("W3 FAIL — " + e.getMessage());
                System.exit(2);
            }
            return;
        }
        fail("Unknown wave: " + wave + " (supported: w1, w2, w3)");
    }

    private static void requireSlots(Path workspace) {
        try {
            WorkspaceSlotVerifier.requireValid(workspace);
        } catch (WorkspaceSlotException e) {
            fail("W1 FAIL — " + e.getMessage());
        }
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }

    static final class Args {
        final Path workspace;
        final String storyId;
        final String wave;
        final boolean openStory;
        final Path seed;

        Args(Path workspace, String storyId, String wave, boolean openStory, Path seed) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.wave = wave;
            this.openStory = openStory;
            this.seed = seed;
        }

        static Args parse(String[] args) {
            Path workspace = null;
            String storyId = null;
            String wave = null;
            boolean openStory = false;
            Path seed = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if ("--wave".equals(a) && i + 1 < args.length) {
                    wave = args[++i];
                } else if ("--open-story".equals(a)) {
                    openStory = true;
                } else if ("--seed".equals(a) && i + 1 < args.length) {
                    seed = Paths.get(args[++i]);
                } else if ("--help".equals(a) || "-h".equals(a)) {
                    printHelp();
                    System.exit(0);
                } else {
                    fail("Unknown arg: " + a);
                }
            }
            if (workspace == null) {
                fail("Required: --workspace <dir>");
            }
            return new Args(workspace, storyId, wave, openStory, seed);
        }

        private static void printHelp() {
            System.out.println("PathwayWaveMain — debug aid; prefer mvn -pl ai4se-orchestration -am test");
            System.out.println("  --workspace <dir>  --wave w1|w2|w3  --story <id>");
        }
    }
}
