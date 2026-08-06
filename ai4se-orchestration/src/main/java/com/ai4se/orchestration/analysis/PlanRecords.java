package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Formal Plan with Allowed Files. Refuses when Gap is BLOCKED or Discovery missing.
 * Allowed lines are schema-validated products ({@link AllowedPathSchema}).
 */
public final class PlanRecords {

    public static final String PLAN_FILE = "plan.md";

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
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> allowed = new ArrayList<String>();
        boolean inAllowed = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("## ")) {
                inAllowed = t.toLowerCase().contains("allowed");
                continue;
            }
            if (inAllowed && (t.startsWith("- ") || t.startsWith("* "))) {
                String file = AllowedPathSchema.requireBareRelativePath(t.substring(2).trim());
                if (!file.isEmpty()) {
                    allowed.add(file);
                }
            }
        }
        if (allowed.isEmpty()) {
            throw new StageGateException("Plan has no Allowed Files");
        }
        return Collections.unmodifiableList(allowed);
    }

    public static void requireFormalPlanWithAllowed(Path workspace, String storyId) throws IOException {
        if (!hasFormalPlan(workspace, storyId)) {
            throw new StageGateException("Missing formal Plan before Development");
        }
        readAllowedFiles(workspace, storyId);
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
