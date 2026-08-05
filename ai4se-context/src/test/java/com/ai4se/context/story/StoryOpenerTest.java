package com.ai4se.context.story;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StoryOpenerTest {

    @TempDir
    Path temp;

    @Test
    void refusesWhenNotOnboarded() {
        assertThrows(Exception.class, () -> StoryOpener.open(temp, "s1", null));
    }

    @Test
    void opensWithDefaultSeed() throws Exception {
        onboard();
        Path dest = StoryOpener.open(temp, "story-a", null);
        assertTrue(Files.isDirectory(dest.resolve("packages")));
        assertTrue(Files.isRegularFile(dest.resolve("requirement.md")));
        String text = new String(Files.readAllBytes(dest.resolve("requirement.md")), StandardCharsets.UTF_8);
        assertTrue(text.contains("## acceptance"));
    }

    @Test
    void opensWithExternalSeed() throws Exception {
        onboard();
        Path seed = temp.resolve("seed.md");
        Files.write(seed, ("# S\n\n## raw\nx\n\n## goal\ny\n\n## in_scope\n- a\n\n"
                + "## out_of_scope\n- b\n\n## acceptance\n- concrete AC\n").getBytes(StandardCharsets.UTF_8));
        StoryOpener.open(temp, "story-b", seed);
        String text = new String(
                Files.readAllBytes(temp.resolve(".story/story-b/requirement.md")),
                StandardCharsets.UTF_8);
        assertTrue(text.contains("concrete AC"));
    }

    @Test
    void rejectsInvalidStoryId() throws Exception {
        onboard();
        assertThrows(IllegalArgumentException.class, () -> StoryOpener.open(temp, "../evil", null));
        assertThrows(IllegalArgumentException.class, () -> StoryOpener.open(temp, "", null));
    }

    private void onboard() throws Exception {
        Files.createDirectories(temp.resolve(".ai4se"));
        Files.createDirectories(temp.resolve(".story"));
    }
}
