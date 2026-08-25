package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.MarkdownLists;
import com.ai4se.runtime.common.util.Strings;
import com.ai4se.context.story.StoryRequirementReader;
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
        writeImpactContract(workspace, storyId, dir, impacts, section(text, "behavioral scenarios"));
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

    /**
     * Turns a reviewable scenario matrix into a durable impact contract.  It deliberately does
     * not claim to be a whole-program call graph: every scenario must name a real current source
     * evidence path and an already executable verification route.  Missing or unknown evidence
     * is a planning stop, not a model assertion that there is no indirect impact.
     */
    private static void writeImpactContract(
            Path workspace, String storyId, Path planningDir, Map<String, String> impacts, String scenarioBody)
            throws IOException {
        boolean anyPresent = false;
        for (String area : IMPACT_AREAS) {
            anyPresent = anyPresent || "PRESENT".equals(impacts.get(area));
        }
        List<String> scenarios = new ArrayList<String>();
        for (String raw : scenarioBody.split("\\R")) {
            String line = raw.trim();
            if (line.matches("^-\\s+.+")) {
                scenarios.add(line.replaceFirst("^-\\s+", "").trim());
            }
        }
        if (anyPresent && scenarios.isEmpty()) {
            throw new StageGateException(
                    "Impact Assessment contains PRESENT; Plan must include ## Behavioral Scenarios with evidence and verification");
        }
        Path impactDir = planningDir.resolve("impact");
        Files.createDirectories(impactDir);
        StringBuilder scenarioOut = new StringBuilder("# Behavioral Scenarios\n\n");
        StringBuilder regression = new StringBuilder("# Regression Selection\n\n");
        StringBuilder unknowns = new StringBuilder("# Impact Unknowns\n\n");
        int unknownCount = 0;
        for (String scenario : scenarios) {
            if (!scenario.contains("id:") || !scenario.contains("evidence:")
                    || !scenario.contains("verification:")) {
                throw new StageGateException(
                        "Each Behavioral Scenario requires id:, evidence:, and verification: " + scenario);
            }
            String evidence = field(scenario, "evidence:");
            String verification = field(scenario, "verification:");
            if (Strings.isBlank(evidence) || "UNKNOWN".equalsIgnoreCase(evidence)) {
                throw new StageGateException(
                        "Behavioral Scenario evidence must be a current source path, not UNKNOWN: " + scenario);
            }
            Path source = workspace.resolve(evidence.replace('\\', '/')).normalize();
            if (!source.startsWith(workspace.normalize()) || !Files.isRegularFile(source)) {
                throw new StageGateException(
                        "Behavioral Scenario evidence path missing or escapes workspace: " + evidence);
            }
            if (Strings.isBlank(verification) || "UNKNOWN".equalsIgnoreCase(verification)) {
                throw new StageGateException(
                        "Behavioral Scenario verification must reference an executable entry/probe: " + scenario);
            }
            requireExecutableVerificationReference(workspace, storyId, verification);
            scenarioOut.append("- ").append(scenario).append('\n');
            regression.append("- ").append(verification).append(" # ").append(field(scenario, "id:")).append('\n');
            if (scenario.toUpperCase(Locale.ROOT).contains("UNKNOWN")) {
                unknowns.append("- ").append(scenario).append('\n');
                unknownCount++;
            }
        }
        if (scenarios.isEmpty()) {
            scenarioOut.append("- none: every impact area is NOT_APPLICABLE\n");
            regression.append("- none\n");
            unknowns.append("- none\n");
        } else if (unknownCount == 0) {
            unknowns.append("- none\n");
        }
        Files.write(impactDir.resolve("behavioral-scenarios.md"),
                scenarioOut.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(impactDir.resolve("regression-selection.md"),
                regression.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(impactDir.resolve("unknowns.md"), unknowns.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder index = new StringBuilder("impact_contract_version=1\n")
                .append("scenario_count=").append(scenarios.size()).append('\n')
                .append("unknown_count=").append(unknownCount).append('\n');
        for (String area : IMPACT_AREAS) {
            index.append(area).append('=').append(impacts.get(area)).append('\n');
        }
        Files.write(impactDir.resolve("impact-index.properties"),
                index.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void requireExecutableVerificationReference(
            Path workspace, String storyId, String reference) throws IOException {
        if ("ENTRY_TEST".equals(reference)) {
            return; // VerificationControl executes every usable entries.yaml test command.
        }
        java.util.regex.Matcher probe = java.util.regex.Pattern
                .compile("AC_PROBE:AC([1-9][0-9]*)")
                .matcher(reference);
        if (probe.matches()) {
            int acceptanceIndex = Integer.parseInt(probe.group(1));
            int acceptanceCount = StoryRequirementReader.read(workspace, storyId).acceptance().size();
            if (acceptanceIndex <= acceptanceCount) {
                return; // freeze-probes later requires one frozen executable probe per AC.
            }
        }
        throw new StageGateException(
                "Behavioral Scenario verification must be ENTRY_TEST or AC_PROBE:AC<n> within frozen Acceptance: "
                        + reference);
    }

    private static String field(String scenario, String key) {
        int start = scenario.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        int end = scenario.indexOf('|', start);
        return (end < 0 ? scenario.substring(start) : scenario.substring(start, end)).trim();
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
