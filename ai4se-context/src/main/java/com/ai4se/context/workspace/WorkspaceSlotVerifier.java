package com.ai4se.context.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * W1 必验：槽位存在、入口诚实（命令或显式 unknown）、Facts 无改码建议。
 * 供 CLI 与单测共用——不要靠人手复跑同一条命令当验收。
 */
public final class WorkspaceSlotVerifier {

    private WorkspaceSlotVerifier() {
    }

    public static void requireValid(Path workspace) throws WorkspaceSlotException {
        List<String> problems = problems(workspace);
        if (!problems.isEmpty()) {
            throw new WorkspaceSlotException(join(problems));
        }
    }

    public static List<String> problems(Path workspace) {
        List<String> problems = new ArrayList<String>();
        if (workspace == null || !Files.isDirectory(workspace)) {
            problems.add("workspace missing or not a directory");
            return problems;
        }
        Path ai4se = workspace.resolve(".ai4se");
        Path story = workspace.resolve(".story");
        Path entries = ai4se.resolve("repository/entries.yaml");
        Path index = ai4se.resolve("index/knowledge.yaml");
        Path baseline = ai4se.resolve("repository/baseline.md");

        if (!Files.isDirectory(ai4se)) {
            problems.add("missing .ai4se/");
        }
        if (!Files.isDirectory(story)) {
            problems.add("missing .story/");
        }
        if (!Files.isRegularFile(entries)) {
            problems.add("missing .ai4se/repository/entries.yaml");
        }
        if (!Files.isRegularFile(index)) {
            problems.add("missing .ai4se/index/knowledge.yaml");
        }
        if (!Files.isRegularFile(baseline)) {
            problems.add("missing .ai4se/repository/baseline.md");
        }
        if (!problems.isEmpty() && !Files.isRegularFile(entries)) {
            return problems;
        }

        try {
            if (Files.isRegularFile(entries)) {
                String entriesText = new String(Files.readAllBytes(entries), StandardCharsets.UTF_8);
                problems.addAll(validateEntries(entriesText));
            }
            if (Files.isRegularFile(baseline)) {
                String baselineText = new String(Files.readAllBytes(baseline), StandardCharsets.UTF_8);
                problems.addAll(validateNoSuggestions(baselineText, "baseline.md"));
                problems.addAll(validateBaselineNotStub(baselineText));
            }
        } catch (IOException e) {
            problems.add("io error: " + e.getMessage());
        }
        return problems;
    }

    static List<String> validateEntries(String entriesText) {
        List<String> problems = new ArrayList<String>();
        problems.addAll(validateNoSuggestions(entriesText, "entries.yaml"));
        boolean hasBuild = entriesText.contains("build:");
        boolean hasTest = entriesText.contains("test:");
        boolean unknown = entriesText.contains("unknown");
        boolean hasCommand = entriesText.contains("mvn")
                || entriesText.contains("npm")
                || entriesText.contains("gradlew")
                || entriesText.contains("gradle ");
        if (!hasBuild || !hasTest) {
            problems.add("entries.yaml must declare build/ and test/");
        }
        if (!unknown && !hasCommand
                && (entriesText.contains("build: []") || entriesText.contains("test: []"))) {
            problems.add("empty build/test [] without explicit unknown");
        }
        return problems;
    }

    /**
     * Onboard may create a stub with {@code (fill)} placeholders. That is not an honest baseline —
     * handbook S1: empty/stub slots must not claim onboard complete for pathway.
     */
    static List<String> validateBaselineNotStub(String baselineText) {
        List<String> problems = new ArrayList<String>();
        if (baselineText == null || baselineText.trim().isEmpty()) {
            problems.add("baseline.md is empty — fill Facts before pathway");
            return problems;
        }
        // Template from onboard-repo.sh uses literal "(fill"
        if (baselineText.contains("(fill)")) {
            problems.add(
                    "baseline.md still has (fill) placeholders — digitize Facts before claiming S1/pathway");
        }
        return problems;
    }

    static List<String> validateNoSuggestions(String text, String label) {
        List<String> problems = new ArrayList<String>();
        String[] banned = new String[] {
                "应实现", "建议改", "推荐修改", "建议实现", "应该改", "请实现"
        };
        for (String token : banned) {
            if (text.contains(token)) {
                problems.add(label + " contains change suggestion: " + token);
            }
        }
        return problems;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static List<String> empty() {
        return Collections.emptyList();
    }
}
