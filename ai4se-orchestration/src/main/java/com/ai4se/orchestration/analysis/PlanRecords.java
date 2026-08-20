package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.MarkdownLists;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formal Plan with Allowed Files. Refuses when Gap is BLOCKED or Discovery missing.
 * Allowed lines are schema-validated products ({@link AllowedPathSchema}).
 */
public final class PlanRecords {

    public static final String PLAN_FILE = "plan.md";
    private static final List<String> IMPACT_AREAS = Arrays.asList(
            "api", "data", "authorization", "ui", "observability");
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
        sb.append("\n## Change Map\n\n- (fixture formal plan; no change map detail)\n");
        sb.append("\n## Test Strategy\n\n- (fixture formal plan; no test strategy detail)\n");
        sb.append("\n## Impact Assessment\n\n");
        for (String area : IMPACT_AREAS) {
            sb.append("- ").append(area).append(": NOT_APPLICABLE — fixture formal plan\n");
        }
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
        Map<String, String> impacts = parseImpactAssessment(section(text, "impact assessment"));
        requireImpactDetailFiles(dir, impacts);
        Files.write(dir.resolve("impact-assessment.md"),
                renderImpactAssessment(impacts).getBytes(StandardCharsets.UTF_8));
        StringBuilder properties = new StringBuilder();
        properties.append("story_id=").append(storyId).append('\n');
        properties.append("change_map_sha256=").append(
                com.ai4se.context.packagebuild.ModelInputEnvelope.sha256(changeMap.getBytes(StandardCharsets.UTF_8)))
                .append('\n');
        properties.append("test_strategy_sha256=").append(
                com.ai4se.context.packagebuild.ModelInputEnvelope.sha256(testStrategy.getBytes(StandardCharsets.UTF_8)))
                .append('\n');
        properties.append("impact_assessment_sha256=").append(
                com.ai4se.context.packagebuild.ModelInputEnvelope.sha256(
                        renderImpactAssessment(impacts).getBytes(StandardCharsets.UTF_8)))
                .append('\n');
        Files.write(dir.resolve("change-map.properties"),
                properties.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseImpactAssessment(String body) {
        if (Strings.isBlank(body)) {
            throw new StageGateException(
                    "Plan must include ## Impact Assessment for api/data/authorization/ui/observability");
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String line : body.split("\\R")) {
            String t = line.trim();
            // The Planning contract requires one declaration per area, not a Markdown
            // list decoration.  Accept both "- api: PRESENT" and "api: PRESENT";
            // otherwise a valid human/model Plan is incorrectly recorded as policy failure.
            String raw = t.startsWith("-") ? t.substring(1).trim() : t;
            int colon = raw.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = raw.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = raw.substring(colon + 1).trim().toUpperCase(Locale.ROOT);
            int whitespace = value.indexOf(' ');
            if (whitespace > 0) {
                value = value.substring(0, whitespace);
            }
            if (IMPACT_AREAS.contains(key)) {
                values.put(key, value);
            }
        }
        for (String area : IMPACT_AREAS) {
            String value = values.get(area);
            if (!"PRESENT".equals(value) && !"NOT_APPLICABLE".equals(value)) {
                throw new StageGateException(
                        "Impact Assessment must declare " + area + ": PRESENT|NOT_APPLICABLE");
            }
        }
        return values;
    }

    private static void requireImpactDetailFiles(Path dir, Map<String, String> impacts) {
        requireDetailFileWhenPresent(dir, impacts, "api", "api-contract.md");
        requireDetailFileWhenPresent(dir, impacts, "data", "data-change.md");
    }

    private static void requireDetailFileWhenPresent(
            Path dir, Map<String, String> impacts, String area, String file) {
        if ("PRESENT".equals(impacts.get(area)) && !Files.isRegularFile(dir.resolve(file))) {
            throw new StageGateException(
                    "Impact Assessment marks " + area + " PRESENT; Planning must write " + file);
        }
    }

    private static String renderImpactAssessment(Map<String, String> impacts) {
        StringBuilder out = new StringBuilder("# Impact Assessment\n\n");
        for (String area : IMPACT_AREAS) {
            out.append("- ").append(area).append(": ").append(impacts.get(area)).append('\n');
        }
        return out.toString();
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
