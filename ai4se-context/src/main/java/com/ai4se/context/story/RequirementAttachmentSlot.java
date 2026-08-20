package com.ai4se.context.story;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requirement attachment slot under {@code .story/<id>/requirement-attachments/}.
 * Problem class: demand input is text-only with no governed media/prototype channel.
 *
 * <p>Declared attachments (## attachments in requirement.md) must exist before Analysis pretends
 * they were reviewed.
 */
public final class RequirementAttachmentSlot {

    public static final String DIR = "requirement-attachments";
    private static final Pattern ATTACH_LINE = Pattern.compile("^\\s*[-*]\\s+(.+?)\\s*$");

    private RequirementAttachmentSlot() {
    }

    public static Path dir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(DIR);
    }

    public static List<String> listPresent(Path workspace, String storyId) throws IOException {
        Path d = dir(workspace, storyId);
        if (!Files.isDirectory(d)) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(d)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    names.add(p.getFileName().toString());
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * If requirement declares ## attachments, each listed file must exist in the slot
     * (or as a relative path under the story root). Missing → refuse.
     */
    public static void requireDeclaredPresent(Path workspace, String storyId) throws IOException {
        Path req = StoryRequirementReader.requirementPath(workspace, storyId);
        if (!Files.isRegularFile(req)) {
            return;
        }
        String text = new String(Files.readAllBytes(req), StandardCharsets.UTF_8);
        List<String> declared = parseDeclared(text);
        if (declared.isEmpty()) {
            return;
        }
        List<String> missing = new ArrayList<String>();
        for (String name : declared) {
            Path inSlot = dir(workspace, storyId).resolve(name);
            Path underStory = workspace.resolve(".story").resolve(storyId).resolve(name);
            if (!Files.isRegularFile(inSlot) && !Files.isRegularFile(underStory)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IOException(
                    "Requirement declares attachments but files missing under "
                            + DIR + "/: " + missing
                            + " — must not pretend unread media was reviewed");
        }
    }

    public static String renderIndexMarkdown(Path workspace, String storyId) throws IOException {
        List<String> present = listPresent(workspace, storyId);
        StringBuilder sb = new StringBuilder();
        sb.append("# Requirement attachments\n\n");
        sb.append("- slot: .story/").append(storyId).append('/').append(DIR).append('\n');
        if (present.isEmpty()) {
            sb.append("- files: (none)\n");
            return sb.toString();
        }
        sb.append("- files:\n");
        for (String n : present) {
            sb.append("  - ").append(n).append('\n');
        }
        sb.append('\n');
        sb.append("Adapter must treat listed files as in-scope input; do not invent unread content.\n");
        return sb.toString();
    }

    /** Parses declared attachment names for intake/specification validation. */
    public static List<String> parseDeclared(String requirementText) {
        if (Strings.isBlank(requirementText)) {
            return Collections.emptyList();
        }
        String[] lines = requirementText.split("\\R", -1);
        boolean in = false;
        List<String> out = new ArrayList<String>();
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("## ")) {
                String h = t.substring(3).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                in = "attachments".equals(h) || "attachment".equals(h) || "附件".equals(t.substring(3).trim());
                continue;
            }
            if (!in) {
                continue;
            }
            Matcher m = ATTACH_LINE.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                if (name.startsWith("`") && name.endsWith("`") && name.length() > 1) {
                    name = name.substring(1, name.length() - 1);
                }
                if (!Strings.isBlank(name) && !name.startsWith("(")) {
                    out.add(name);
                }
            }
        }
        return out;
    }
}
