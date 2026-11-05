package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Human ack that ASSUMABLE Gap assumptions were read before Planning.
 * Problem class: silent ASSUMABLE pass-through without governance.
 */
public final class AssumableAckRecords {

    public static final String FILE = "assumable.ack.md";

    private AssumableAckRecords() {
    }

    public static Path ackFile(Path workspace, String storyId) {
        return DiscoveryRecords.analysisDir(workspace, storyId).resolve(FILE);
    }

    public static boolean hasAck(Path workspace, String storyId) {
        return Files.isRegularFile(ackFile(workspace, storyId));
    }

    public static void write(
            Path workspace, String storyId, String by, String note) throws IOException {
        if (Strings.isBlank(by)) {
            throw new StageGateException("ASSUMABLE ack requires by=");
        }
        Path dir = DiscoveryRecords.analysisDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Assumable Gap Acknowledgement\n\n"
                + "- acknowledged: true\n"
                + "- by: " + by.trim() + "\n"
                + "- note: " + (note == null ? "" : note.trim()) + "\n"
                + "- at: " + Instant.now() + "\n"
                + "- gate: ASSUMABLE assumptions reviewed before Planning\n";
        Files.write(ackFile(workspace, storyId), body.getBytes(StandardCharsets.UTF_8));
    }

    public static void requireAck(Path workspace, String storyId) {
        if (!hasAck(workspace, storyId)) {
            throw new StageGateException(
                    "ASSUMABLE requires human ack — write analysis/assumable.ack.md before Planning");
        }
    }
}
