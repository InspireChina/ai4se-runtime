package com.ai4se.orchestration.review;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W8 thin Review — does not replace Verification.
 * Machine truth lives in {@link #PROPERTIES_FILE}; Markdown is human detail.
 * {@code review_source} is always stamped by Control, never trusted from the model alone.
 */
public final class ReviewRecords {

    public static final String FILE = "review-result.md";
    public static final String PROPERTIES_FILE = "review-result.properties";
    public static final String SOURCE_FIXTURE = "fixture";
    public static final String SOURCE_ADAPTER = "adapter";
    public static final String SOURCE_HUMAN = "human";

    private static final Pattern DECISION_LINE = Pattern.compile(
            "(?im)^\\s*-?\\s*decision\\s*[:=]\\s*(.+?)\\s*$");
    private static final Pattern RESIDUAL_LINE = Pattern.compile(
            "(?im)^\\s*-?\\s*residual_risk\\s*[:=]\\s*(.+?)\\s*$");
    private static final Pattern HEADING_DECISION = Pattern.compile(
            "(?im)^##\\s*decision\\s*$");

    private ReviewRecords() {
    }

    public static Path reviewDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("review");
    }

    public static void write(
            Path workspace, String storyId, String decision, String residualRisk) throws IOException {
        write(workspace, storyId, decision, residualRisk, SOURCE_FIXTURE);
    }

    public static void write(
            Path workspace,
            String storyId,
            String decision,
            String residualRisk,
            String reviewSource) throws IOException {
        VerificationControl.requirePassBeforeReview(workspace, storyId);
        ReviewDecision normalized = ReviewDecision.parse(decision);
        String source = Strings.isBlank(reviewSource) ? SOURCE_FIXTURE : reviewSource.trim();
        Path dir = reviewDir(workspace, storyId);
        Files.createDirectories(dir);
        writeProperties(dir, normalized, residualRisk, source);
        String body = ""
                + "# Review Result\n\n"
                + "- decision: " + normalized.name() + "\n"
                + "- residual_risk: "
                + (Strings.isBlank(residualRisk) ? "(none noted)" : residualRisk.trim()) + "\n"
                + "- review_source: " + source + "\n"
                + "- note: Review does not re-run full Verification\n";
        Files.write(dir.resolve(FILE), body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Control-owned machine sidecar. Overwrites properties; does not erase free-form Markdown
     * detail the adapter may have written under {@link #FILE}.
     */
    public static void writeMachineSidecar(
            Path workspace,
            String storyId,
            ReviewDecision decision,
            String residualRisk,
            String reviewSource) throws IOException {
        if (decision == null) {
            throw new StageGateException("Review decision required");
        }
        String source = Strings.isBlank(reviewSource) ? SOURCE_FIXTURE : reviewSource.trim();
        Path dir = reviewDir(workspace, storyId);
        Files.createDirectories(dir);
        writeProperties(dir, decision, residualRisk, source);
        if (!Files.isRegularFile(dir.resolve(FILE))) {
            String body = ""
                    + "# Review Result\n\n"
                    + "- decision: " + decision.name() + "\n"
                    + "- residual_risk: "
                    + (Strings.isBlank(residualRisk) ? "(none noted)" : residualRisk.trim()) + "\n"
                    + "- review_source: " + source + "\n"
                    + "- note: Review does not re-run full Verification\n";
            Files.write(dir.resolve(FILE), body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeProperties(
            Path dir, ReviewDecision decision, String residualRisk, String source) throws IOException {
        String body = ""
                + "decision=" + decision.name() + "\n"
                + "review_source=" + source + "\n"
                + "residual_risk="
                + (Strings.isBlank(residualRisk) ? "" : residualRisk.trim().replace('\n', ' '))
                + "\n"
                + "normalized_at_utc=" + Instant.now().toString() + "\n";
        Files.write(dir.resolve(PROPERTIES_FILE), body.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasResult(Path workspace, String storyId) {
        Path dir = reviewDir(workspace, storyId);
        return Files.isRegularFile(dir.resolve(PROPERTIES_FILE))
                || Files.isRegularFile(dir.resolve(FILE));
    }

    public static void requirePresent(Path workspace, String storyId) {
        if (!hasResult(workspace, storyId)) {
            throw new StageGateException("Missing Review Result before Delivery");
        }
    }

    public static ReviewDecision requireDecision(Path workspace, String storyId) throws IOException {
        requirePresent(workspace, storyId);
        return readDecision(workspace, storyId);
    }

    public static ReviewDecision readDecision(Path workspace, String storyId) throws IOException {
        Path props = reviewDir(workspace, storyId).resolve(PROPERTIES_FILE);
        if (Files.isRegularFile(props)) {
            Map<String, String> map = readPropertiesFile(props);
            String d = map.get("decision");
            if (!Strings.isBlank(d)) {
                return ReviewDecision.parse(d);
            }
        }
        Path md = reviewDir(workspace, storyId).resolve(FILE);
        if (Files.isRegularFile(md)) {
            String text = new String(Files.readAllBytes(md), StandardCharsets.UTF_8);
            String extracted = extractDecisionText(text);
            if (!Strings.isBlank(extracted)) {
                return ReviewDecision.parse(extracted);
            }
        }
        throw new StageGateException("FAILED_ADAPTER: Review result missing decision");
    }

    public static boolean allowsAutomaticDelivery(Path workspace, String storyId) throws IOException {
        return readDecision(workspace, storyId).allowsAutomaticDelivery();
    }

    /** @deprecated use {@link #readDecision}; kept for call sites that only blocked REJECT. */
    @Deprecated
    public static boolean isRejected(Path workspace, String storyId) throws IOException {
        if (!hasResult(workspace, storyId)) {
            return false;
        }
        try {
            return readDecision(workspace, storyId) == ReviewDecision.REJECT;
        } catch (StageGateException e) {
            return false;
        }
    }

    /**
     * Extract a decision token from free-form Review Markdown (legacy adapter output).
     * Prefers {@code decision:} lines; then {@code ## decision} heading body; then keywords.
     */
    public static String extractDecisionText(String text) {
        if (Strings.isBlank(text)) {
            return "";
        }
        Matcher line = DECISION_LINE.matcher(text);
        if (line.find()) {
            return stripMarkdownEmphasis(line.group(1).trim());
        }
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (HEADING_DECISION.matcher(lines[i]).matches()) {
                for (int j = i + 1; j < lines.length; j++) {
                    String body = stripMarkdownEmphasis(lines[j].trim());
                    if (body.isEmpty() || body.startsWith("#")) {
                        if (body.startsWith("#")) {
                            break;
                        }
                        continue;
                    }
                    return body;
                }
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (text.contains("附条件") || lower.contains("conditional")) {
            return "CONDITIONAL";
        }
        if (text.contains("驳回") || lower.contains("reject")) {
            return "REJECT";
        }
        if (text.contains("通过") || lower.contains("pass")) {
            return "PASS";
        }
        return "";
    }

    public static String extractResidualText(String text) {
        if (Strings.isBlank(text)) {
            return "";
        }
        Matcher m = RESIDUAL_LINE.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static String stripMarkdownEmphasis(String s) {
        String t = s.trim();
        while (t.startsWith("*") || t.startsWith("_")) {
            t = t.substring(1);
        }
        while (t.endsWith("*") || t.endsWith("_")) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim();
    }

    static Map<String, String> readPropertiesFile(Path path) throws IOException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            map.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
        }
        return map;
    }
}
