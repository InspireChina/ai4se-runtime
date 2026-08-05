package com.ai4se.context.compress;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.packagebuild.PackageRefuseException;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CompressionRetentionTest {

    @TempDir
    Path temp;

    @Test
    void analysisPackageWritesCompressAuditWithoutChat() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws);
        OnboardRepoScript.run(ws);
        Path seed = temp.resolve("seed.md");
        Files.write(seed, (""
                + "## raw\nr\n\n## goal\ng\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- ac-ok\n")
                .getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(ws, "s1", seed);
        AnalysisPackageBuilder.build(ws, "s1");

        assertTrue(Files.isDirectory(ws.resolve(".story/s1/compress")));
        assertTrue(CompressionRetention.listBoundaries(ws, "s1").size() >= 1);
        String audit = new String(
                Files.readAllBytes(CompressionRetention.listBoundaries(ws, "s1").get(0)),
                StandardCharsets.UTF_8);
        assertTrue(audit.contains("acceptance"));
        assertTrue(audit.contains("chat_transcript") || audit.contains("discarded"));
        assertTrue(audit.contains("## discarded"));
    }

    @Test
    void rejectChatInPriority1() {
        assertThrows(PackageRefuseException.class, () ->
                CompressionRetention.rejectChatInPackage(""
                        + "# Manifest\n\n## priority1\n\n- slices/chat_transcript.md\n"));
    }

    @Test
    void allowChatMentionOnlyUnderForbiddenFiltered() {
        CompressionRetention.rejectChatInPackage(""
                + "# Manifest\n\n## priority1\n\n- slices/acceptance.md\n\n"
                + "## forbidden_filtered\n\n- chat_transcript\n");
    }

    @Test
    void droppedAcceptanceFailsRetentionCheck() {
        assertThrows(PackageRefuseException.class, () ->
                CompressionRetention.requireRetainedInManifest(
                        "Verification", "# Manifest\n\n## priority1\n\n- slices/diff-ref.md\n", false));
    }
}
