package com.ai4se.context.story;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads `.story/<id>/requirement.md` sectioned markdown. */
public final class StoryRequirementReader {

    private static final Pattern HEADING = Pattern.compile("^##\\s+(.+?)\\s*$");

    private StoryRequirementReader() {
    }

    public static Path requirementPath(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("requirement.md");
    }

    public static StoryRequirement read(Path workspace, String storyId) throws IOException {
        Path path = requirementPath(workspace, storyId);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Story requirement missing: " + path);
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Map<String, String> sections = parseSections(text);
        List<String> acceptance = parseAcceptanceLines(sections.get("acceptance"));
        return new StoryRequirement(
                storyId,
                sections.get("raw"),
                sections.get("goal"),
                sections.get("in_scope"),
                sections.get("out_of_scope"),
                acceptance);
    }

    public static Map<String, String> parseSections(String text) {
        Map<String, String> sections = new LinkedHashMap<String, String>();
        String current = null;
        StringBuilder body = new StringBuilder();
        String[] lines = text.split("\\R", -1);
        for (String line : lines) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                if (current != null) {
                    sections.put(current, body.toString().trim());
                }
                current = normalizeHeading(m.group(1));
                body = new StringBuilder();
            } else if (current != null) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(line);
            }
        }
        if (current != null) {
            sections.put(current, body.toString().trim());
        }
        return sections;
    }

    public static String normalizeHeading(String heading) {
        String h = heading.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if ("in-scope".equals(h) || "inscope".equals(h)) {
            return "in_scope";
        }
        if ("out-of-scope".equals(h) || "outofscope".equals(h) || "out_of_scope".equals(h)) {
            return "out_of_scope";
        }
        // Limited Acceptance aliases → canonical "acceptance" (missing still rejected by gate).
        if ("acceptance".equals(h)
                || "acceptance_criteria".equals(h)
                || "acceptance_criterion".equals(h)) {
            return "acceptance";
        }
        return h;
    }

    public static List<String> parseAcceptanceLines(String section) {
        List<String> items = new ArrayList<String>();
        if (Strings.isBlank(section)) {
            return items;
        }
        String[] lines = section.split("\\R");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("- ") || t.startsWith("* ")) {
                t = t.substring(2).trim();
            } else if (t.matches("^\\d+[.)]\\s+.*")) {
                t = t.replaceFirst("^\\d+[.)]\\s+", "").trim();
            }
            if (!t.isEmpty()) {
                items.add(t);
            }
        }
        return items;
    }
}
