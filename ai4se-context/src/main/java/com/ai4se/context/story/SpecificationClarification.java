package com.ai4se.context.story;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stable location for the human decisions made while turning raw input into a frozen Story.
 *
 * <p>The record is deliberately reused as a small, explicit P1 slice by later roles.  It is not
 * a chat transcript: it contains the question, source evidence, and the operator's answer that
 * constrained the frozen requirement.</p>
 */
public final class SpecificationClarification {

    private SpecificationClarification() {
    }

    public static Path resolvedPath(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId)
                .resolve("specification").resolve("clarification.resolved.md");
    }

    /** Copies the resolved decision record when this Story had a pre-freeze clarification. */
    public static long copyToSlice(Path workspace, String storyId, Path target) throws IOException {
        Path source = resolvedPath(workspace, storyId);
        if (!Files.isRegularFile(source)) {
            return 0L;
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return Files.size(target);
    }
}
