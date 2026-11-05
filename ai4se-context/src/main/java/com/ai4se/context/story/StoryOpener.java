package com.ai4se.context.story;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Opens `.story/<id>/` with a seed requirement (W2 · S2). */
public final class StoryOpener {

    private StoryOpener() {
    }

    public static Path open(Path workspace, String storyId, Path seedOrNull) throws IOException {
        Path ai4se = workspace.resolve(".ai4se");
        Path storyRoot = workspace.resolve(".story");
        if (!Files.isDirectory(ai4se) || !Files.isDirectory(storyRoot)) {
            throw new IOException("Workspace not onboarded (missing .ai4se/ or .story/): " + workspace);
        }
        if (Strings.isBlank(storyId) || !storyId.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
            throw new IllegalArgumentException("Invalid story id: " + storyId);
        }
        Path dest = storyRoot.resolve(storyId);
        Files.createDirectories(dest.resolve("packages"));
        Path requirement = dest.resolve("requirement.md");
        if (seedOrNull != null) {
            Files.copy(seedOrNull, requirement, StandardCopyOption.REPLACE_EXISTING);
        } else if (!Files.exists(requirement)) {
            Files.write(requirement, defaultSeed().getBytes(StandardCharsets.UTF_8));
        }
        return dest;
    }

    private static String defaultSeed() {
        return ""
                + "# Story Seed\n\n"
                + "## raw\n\n（原始自然语言需求）\n\n"
                + "## goal\n\n（一句话目标）\n\n"
                + "## in_scope\n\n- （包含什么）\n\n"
                + "## out_of_scope\n\n- （明确不含什么）\n\n"
                + "## acceptance\n\n- （可检验的通过条件）\n";
    }
}
