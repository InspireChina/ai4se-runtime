package com.ai4se.runtime.demo.input;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import com.ai4se.runtime.demo.delivery.DeliveryStageResult;
import com.ai4se.runtime.demo.delivery.SerialDeliveryRunner;
import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import com.ai4se.runtime.engine.api.RuntimeResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Production CLI — Input files → Loader → Runtime.submit pipeline.
 * <pre>
 * java -jar ai4se-runtime.jar --workspace sample-workspace --input sample-input
 * </pre>
 * Not Runtime Kernel. No StageRunner / Workflow / AI Worker.
 */
public final class ProductionRuntimeMain {

    private ProductionRuntimeMain() {
    }

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        Path workspaceSource = parsed.workspace.toPath().toAbsolutePath().normalize();
        Path inputDir = parsed.input.toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(workspaceSource)) {
            throw new IOException("workspace not found: " + workspaceSource);
        }
        if (!Files.isDirectory(inputDir)) {
            throw new IOException("input not found: " + inputDir);
        }

        DeliveryScenario scenario = ProductionInputLoader.load(inputDir);
        File workDir = prepareWorkCopy(workspaceSource, parsed.workDir);
        System.out.println("=== AI4SE Production Input Delivery ===");
        System.out.println("input     = " + inputDir);
        System.out.println("workspace = " + workDir.getAbsolutePath());
        System.out.println("bundle    = " + scenario.id() + " [" + scenario.typeLabel() + "]");
        System.out.println("requirement:");
        System.out.println(scenario.requirement());

        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, scenario);
        boolean ok = stages.size() == 6;
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            System.out.println("--- " + stage.getStage() + " ---");
            System.out.println("  success=" + r.isSuccess()
                    + " worker=" + r.getWorkerId()
                    + " goal=" + r.getGoalType()
                    + " durationMs=" + r.getDurationMs()
                    + " checkpoint="
                    + (r.getCheckpointId().isPresent() ? r.getCheckpointId().get().value() : "-"));
            if (!r.isSuccess()) {
                System.out.println("  failure=" + r.getMessage());
                ok = false;
            }
        }
        if (!ok) {
            System.err.println("Production delivery FAILED");
            System.exit(1);
        }
        System.out.println("Production delivery OK — input files drove Runtime (Kernel unchanged).");
    }

    private static File prepareWorkCopy(Path workspaceSource, File explicitWorkDir) throws IOException {
        File target;
        if (explicitWorkDir != null) {
            target = explicitWorkDir.getAbsoluteFile();
        } else {
            File moduleGuess = workspaceSource.getParent() != null
                    ? workspaceSource.getParent().toFile()
                    : new File(".").getAbsoluteFile();
            target = new File(moduleGuess, "target/production-input-work");
        }
        if (target.exists()) {
            deleteRecursive(target.toPath());
        }
        Files.createDirectories(target.toPath());
        copyRecursive(workspaceSource, target.toPath());
        return target.getCanonicalFile();
    }

    private static void copyRecursive(final Path source, final Path target) throws IOException {
        Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(dir).toString());
                Files.createDirectories(dest);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(file).toString());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    static final class Args {
        final File workspace;
        final File input;
        final File workDir;

        Args(File workspace, File input, File workDir) {
            this.workspace = workspace;
            this.input = input;
            this.workDir = workDir;
        }

        static Args parse(String[] args) throws IOException {
            File workspace = null;
            File input = null;
            File workDir = null;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    String a = args[i];
                    if ("--workspace".equals(a) && i + 1 < args.length) {
                        workspace = new File(args[++i]);
                    } else if ("--input".equals(a) && i + 1 < args.length) {
                        input = new File(args[++i]);
                    } else if ("--workdir".equals(a) && i + 1 < args.length) {
                        workDir = new File(args[++i]);
                    } else if ("--help".equals(a) || "-h".equals(a)) {
                        printHelp();
                        System.exit(0);
                    } else {
                        throw new IOException("unknown arg: " + a);
                    }
                }
            }
            if (workspace == null || input == null) {
                // Defaults relative to ai4se-demo when run from module / repo
                File module = resolveDemoModule();
                if (workspace == null) {
                    workspace = new File(module, "sample-workspace");
                }
                if (input == null) {
                    input = new File(module, "sample-input");
                }
            }
            if (!workspace.isDirectory() || !input.isDirectory()) {
                printHelp();
                throw new IOException("require existing --workspace and --input directories");
            }
            return new Args(workspace, input, workDir);
        }

        private static File resolveDemoModule() throws IOException {
            try {
                return WorkspaceBootstrap.resolveDemoModuleRoot();
            } catch (IOException ex) {
                File cwd = new File("").getCanonicalFile();
                if (new File(cwd, "sample-input").isDirectory()) {
                    return cwd;
                }
                throw ex;
            }
        }

        private static void printHelp() {
            System.out.println("Usage:");
            System.out.println("  java -jar ai4se-runtime.jar --workspace <dir> --input <dir> [--workdir <dir>]");
            System.out.println("Defaults (from ai4se-demo): sample-workspace + sample-input");
        }
    }
}
