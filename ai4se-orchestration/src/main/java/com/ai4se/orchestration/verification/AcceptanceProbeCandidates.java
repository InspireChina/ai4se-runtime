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
     * A selected Maven test with {@code failIfNoTests=false} can exit 0 when the Development
     * phase never created the planned test.  Such a probe cannot prove an AC, so reject it while
     * it is still a reviewed candidate rather than discovering a false green after Delivery.
     */
    private static void rejectNoTestBypass(Path root) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    rejectNoTestBypass(child);
                } else if (Files.isRegularFile(child)) {
                    String text = new String(Files.readAllBytes(child), java.nio.charset.StandardCharsets.UTF_8);
                    if (text.contains("-DfailIfNoTests=false")
                            || text.contains("-Dsurefire.failIfNoSpecifiedTests=false")) {
                        throw new StageGateException(
                                "Acceptance probe candidate permits missing selected tests: "
                                        + root.relativize(child));
                    }
                }
            }
        }
    }
}
