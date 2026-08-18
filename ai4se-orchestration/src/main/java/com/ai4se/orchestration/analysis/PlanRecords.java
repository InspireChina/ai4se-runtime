package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.MarkdownLists;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formal Plan with Allowed Files. Refuses when Gap is BLOCKED or Discovery missing.
 * Allowed lines are schema-validated products ({@link AllowedPathSchema}).
 */
public final class PlanRecords {

    public static final String PLAN_FILE = "plan.md";
    private static final Pattern H2 = Pattern.compile("(?mi)^##\\s+(.+?)\\s*$");

    private PlanRecords() {
    }

    public static Path planningDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("planning");
    }

    public static void writeFormalPlan(
            Path workspace,
            String storyId,
            String designSummary,
            List<String> allowedFiles) throws IOException {
        DiscoveryRecords.requireReportOrSkip(workspace, storyId);
        GapRecords.requireNotBlocked(workspace, storyId);
        List<String> files = normalizeAllowed(allowedFiles);
        if (files.isEmpty()) {
            throw new StageGateException("Formal Plan requires non-empty Allowed Files");
        }
        Path dir = planningDir(workspace, storyId);
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        sb.append("# Plan\n\n");
        sb.append("## Design\n\n");
        sb.append(Strings.isBlank(designSummary) ? "(design)" : designSummary.trim()).append("\n\n");
        sb.append("## Allowed Files\n\n");
        for (String f : files) {
            sb.append("- ").append(f).append('\n');
        }
        sb.append('\n');
        Files.write(dir.resolve(PLAN_FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasFormalPlan(Path workspace, String storyId) {
        return Files.isRegularFile(planningDir(workspace, storyId).resolve(PLAN_FILE));
    }

    public static List<String> readAllowedFiles(Path workspace, String storyId) throws IOException {
        Path path = planningDir(workspace, storyId).resolve(PLAN_FILE);
        if (!Files.isRegularFile(path)) {
            throw new StageGateException("Missing plan.md for story " + storyId);
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        List<String> allowed = MarkdownLists.extractSection(
                text, h -> h.contains("allowed"), AllowedPathSchema::requireBareRelativePath);
        if (allowed.isEmpty()) {
            throw new StageGateException("Plan has no Allowed Files");
        }
        return allowed;
    }

    public static void requireFormalPlanWithAllowed(Path workspace, String storyId) throws IOException {
        if (!hasFormalPlan(workspace, storyId)) {
            throw new StageGateException("Missing formal Plan before Development");
        }
        readAllowedFiles(workspace, storyId);
    }

    /**
     * Production Plan contract: a plan is executable only when it explains the changed surface
     * and how each Acceptance will be tested. The derived files are deterministic snapshots for
     * later packages; the model does not get to edit them separately.
     */
    public static void requireExecutionArtifacts(Path workspace, String storyId) throws IOException {
        requireFormalPlanWithAllowed(workspace, storyId);
        String text = new String(
                Files.readAllBytes(planningDir(workspace, storyId).resolve(PLAN_FILE)),
                StandardCharsets.UTF_8);
        String changeMap = section(text, "change map");
        String testStrategy = section(text, "test strategy");
        if (Strings.isBlank(changeMap) || Strings.isBlank(testStrategy)
                || "(none)".equalsIgnoreCase(changeMap.trim())
                || "(none)".equalsIgnoreCase(testStrategy.trim())) {
            throw new StageGateException(
                    "Plan must include non-empty ## Change Map and ## Test Strategy before approval");
        }
        Path dir = planningDir(workspace, storyId);
        Files.write(dir.resolve("change-map.md"),
                ("# Change Map\n\n" + changeMap.trim() + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("test-strategy.md"),
                ("# Test Strategy\n\n" + testStrategy.trim() + "\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder properties = new StringBuilder();
        properties.append("story_id=").append(storyId).append('\n');
        properties.append("change_map_sha256=").append(
                com.ai4se.context.packagebuild.ModelInputEnvelope.sha256(changeMap.getBytes(StandardCharsets.UTF_8)))
                .append('\n');
        properties.append("test_strategy_sha256=").append(
                com.ai4se.context.packagebuild.ModelInputEnvelope.sha256(testStrategy.getBytes(StandardCharsets.UTF_8)))
                .append('\n');
        Files.write(dir.resolve("change-map.properties"),
                properties.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String section(String text, String wanted) {
        Matcher matcher = H2.matcher(text == null ? "" : text);
        int start = -1;
        int end = text == null ? 0 : text.length();
        while (matcher.find()) {
            String heading = matcher.group(1).trim().toLowerCase();
            if (heading.contains(wanted.toLowerCase())) {
                start = matcher.end();
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        Matcher next = H2.matcher(text);
        next.region(start, text.length());
        if (next.find()) {
            end = next.start();
        }
        return text.substring(start, end).trim();
    }

    private static List<String> normalizeAllowed(List<String> allowedFiles) {
        List<String> out = new ArrayList<String>();
        if (allowedFiles == null) {
            return out;
        }
        for (String f : allowedFiles) {
            if (!Strings.isBlank(f)) {
                out.add(AllowedPathSchema.requireBareRelativePath(f));
            }
        }
        return out;
    }
}
