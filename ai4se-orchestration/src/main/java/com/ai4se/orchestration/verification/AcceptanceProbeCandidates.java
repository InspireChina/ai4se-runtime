package com.ai4se.orchestration.verification;

import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Freezes reviewed Planning-produced probe candidates into the immutable verification location. */
public final class AcceptanceProbeCandidates {

    public static final String CANDIDATE_DIR = "probe-candidate";

    private AcceptanceProbeCandidates() {
    }

    public static Path candidateRoot(Path workspace, String storyId) {
        return PlanRecords.planningDir(workspace, storyId).resolve(CANDIDATE_DIR);
    }

    public static Path freeze(Path workspace, String storyId) throws IOException {
        PlanRecords.requireExecutionArtifacts(workspace, storyId);
        Path source = candidateRoot(workspace, storyId);
        if (!Files.isRegularFile(source.resolve("probes.properties"))) {
            throw new StageGateException(
                    "Planning must provide reviewed probe-candidate/probes.properties before Development");
        }
        Path destination = workspace.resolve(".ai4se/acceptance-probes").resolve(storyId);
        if (Files.exists(destination)) {
            throw new StageGateException("Frozen acceptance probe destination already exists: " + destination);
        }
        Files.createDirectories(destination);
        try {
            copyTree(source, destination);
            rejectNoTestBypass(destination);
            rejectMavenReactorSelectedTest(destination);
            int count = StoryRequirementReader.read(workspace, storyId).acceptance().size();
            AcceptanceProbeSet.requirePresentAndFrozen(workspace, storyId, count);
            return destination;
        } catch (IOException | RuntimeException e) {
            deleteTree(destination);
            throw e;
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(source)) {
            for (Path child : children) {
                Path target = destination.resolve(child.getFileName().toString()).normalize();
                if (!target.startsWith(destination)) {
                    throw new StageGateException("Unsafe probe candidate path: " + child);
                }
                if (Files.isDirectory(child)) {
                    Files.createDirectories(target);
                    copyTree(child, target);
                } else if (Files.isRegularFile(child)) {
                    Files.copy(child, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    deleteTree(child);
                } else {
                    Files.deleteIfExists(child);
                }
            }
        }
        Files.deleteIfExists(root);
    }

    /**
     * Reactor builds commonly need {@code failIfNoTests=false} for dependency modules that have
     * no tests.  It is safe only when the selected target test is separately required with
     * {@code surefire.failIfNoSpecifiedTests=true}; otherwise a missing Story test can be green.
     */
    private static void rejectNoTestBypass(Path root) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    rejectNoTestBypass(child);
                } else if (Files.isRegularFile(child)) {
                    String text = new String(Files.readAllBytes(child), java.nio.charset.StandardCharsets.UTF_8);
                    boolean selectsMavenTest = text.contains("mvn") && text.contains("-Dtest=");
                    boolean ignoresEmptyModules = text.contains("-DfailIfNoTests=false");
                    boolean requiresSelectedTest = text.contains("-Dsurefire.failIfNoSpecifiedTests=true");
                    if (text.contains("-Dsurefire.failIfNoSpecifiedTests=false")
                            || (ignoresEmptyModules && (!selectsMavenTest || !requiresSelectedTest))) {
                        throw new StageGateException(
                                "Acceptance probe candidate permits missing selected tests: "
                                        + root.relativize(child));
                    }
                }
            }
        }
    }

    /**
     * A baseline build may legitimately use {@code -am}; a precise Surefire selector may not.
     * In a multi-module reactor the selector is propagated to dependency modules and often fails
     * there before the Story's target test runs.  Onboarding's baseline build is the dependency
     * compilation boundary; each AC probe must select only its target module.
     */
    private static void rejectMavenReactorSelectedTest(Path root) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    rejectMavenReactorSelectedTest(child);
                } else if (Files.isRegularFile(child)) {
                    String text = new String(Files.readAllBytes(child), java.nio.charset.StandardCharsets.UTF_8);
                    boolean exactMavenTest = text.contains("mvn") && text.contains("-Dtest=");
                    boolean alsoMake = text.contains(" -am ") || text.contains(" -am\n");
                    if (exactMavenTest && alsoMake) {
                        throw new StageGateException(
                                "Acceptance probe candidate combines Maven -am with exact -Dtest: "
                                        + root.relativize(child));
                    }
                }
            }
        }
    }
}
