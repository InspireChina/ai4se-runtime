package com.ai4se.context.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StoryIntakeTest {

    @TempDir
    Path temp;

    @Test
    void capturesRawTextAndAttachmentManifestWithoutCreatingRequirement() throws Exception {
        onboard();
        Path image = temp.resolve("wireframe.png");
        Files.write(image, new byte[] {1, 2, 3});

        StoryIntake.IntakeResult result = StoryIntake.capture(
                temp, "order-list-1", "在订单页增加批量操作", Collections.singletonList(image));

        assertTrue(Files.isRegularFile(result.rawRequest()));
        assertTrue(Files.isRegularFile(result.attachmentsManifest()));
        assertEquals(1, result.attachmentCount());
        String manifest = new String(Files.readAllBytes(result.attachmentsManifest()), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("attachment.1.name=wireframe.png"), manifest);
        assertTrue(manifest.contains("attachment.1.sha256="), manifest);
        assertTrue(Files.isRegularFile(temp.resolve(".story/order-list-1/requirement-attachments/wireframe.png")));
        assertTrue(!Files.exists(temp.resolve(".story/order-list-1/requirement.md")));
    }

    @Test
    void refusesToOverwriteRawInput() throws Exception {
        onboard();
        StoryIntake.capture(temp, "s1", "first", Collections.<Path>emptyList());
        assertThrows(Exception.class,
                () -> StoryIntake.capture(temp, "s1", "second", Collections.<Path>emptyList()));
    }

    private void onboard() throws Exception {
        Files.createDirectories(temp.resolve(".ai4se"));
        Files.createDirectories(temp.resolve(".story"));
    }
}
