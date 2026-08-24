package com.ai4se.orchestration.specification;

import com.ai4se.context.packagebuild.AcceptanceGate;
import com.ai4se.context.packagebuild.ModelInputEnvelope;
import com.ai4se.context.story.RequirementAttachmentSlot;
import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Records and validates the human-facing specification step before production Analysis. */
public final class SpecificationRecords {

    public static final String DIR = "specification";
    public static final String RESULT_FILE = "specification.result.properties";
    public static final String CANDIDATE_FILE = "candidate-requirement.md";
    public static final String QUESTIONS_FILE = "clarification.questions.md";
    public static final String FROZEN_FILE = "frozen-inputs.properties";
    private static final Pattern REPOSITORY_PATH = Pattern.compile(
            "(?m)(?:[A-Za-z0-9_.-]+/)+[A-Za-z0-9_.-]+(?:\\.[A-Za-z0-9_.-]+)?");

    private SpecificationRecords() {
    }

    public static Path dir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(DIR);
    }

    public static Outcome requireOutcome(Path workspace, String storyId) throws IOException {
        Path result = dir(workspace, storyId).resolve(RESULT_FILE);
        if (!Files.isRegularFile(result)) {
            throw new StageGateException("Specification must write " + RESULT_FILE);
        }
        String text = new String(Files.readAllBytes(result), StandardCharsets.UTF_8);
        String decision = property(text, "decision");
        if ("CANDIDATE".equals(decision)) {
            Path candidate = dir(workspace, storyId).resolve(CANDIDATE_FILE);
            if (!Files.isRegularFile(candidate)) {
                throw new StageGateException("Specification CANDIDATE requires " + CANDIDATE_FILE);
            }
            return Outcome.CANDIDATE;
        }
        if ("CLARIFICATION_REQUIRED".equals(decision)) {
            Path questions = dir(workspace, storyId).resolve(QUESTIONS_FILE);
            if (!Files.isRegularFile(questions)) {
                throw new StageGateException(
                        "Specification CLARIFICATION_REQUIRED requires concrete " + QUESTIONS_FILE);
            }
            requireEvidenceQuestions(new String(Files.readAllBytes(questions), StandardCharsets.UTF_8));
            return Outcome.CLARIFICATION_REQUIRED;
        }
        throw new StageGateException(
                "Specification decision must be CANDIDATE|CLARIFICATION_REQUIRED, was " + decision);
    }

    /** Human action: freeze a syntactically usable candidate; it does not start production. */
    public static Path freezeCandidate(Path workspace, String storyId) throws IOException {
        if (requireOutcome(workspace, storyId) != Outcome.CANDIDATE) {
            throw new StageGateException("Cannot freeze a specification that still requires clarification");
        }
        Path candidate = dir(workspace, storyId).resolve(CANDIDATE_FILE);
        String text = new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
        requireDevelopable(text, workspace, storyId);
        if (Files.isRegularFile(dir(workspace, storyId).resolve("clarification.resolved.md"))
                && Strings.isBlank(StoryRequirementReader.parseSections(text).get("decisions"))) {
            throw new StageGateException(
                    "candidate requirement must retain answered choices in non-empty ## decisions");
        }
        Path requirement = StoryRequirementReader.requirementPath(workspace, storyId);
        if (Files.exists(requirement)) {
            throw new StageGateException("requirement.md already exists — frozen specifications are not overwritten");
        }
        Files.copy(candidate, requirement, StandardCopyOption.COPY_ATTRIBUTES);
        Path frozen = dir(workspace, storyId).resolve(FROZEN_FILE);
        String body = "status=REQUIREMENT_FROZEN\n"
                + "candidate_sha256=" + ModelInputEnvelope.sha256(Files.readAllBytes(candidate)) + "\n"
                + "requirement_sha256=" + ModelInputEnvelope.sha256(Files.readAllBytes(requirement)) + "\n"
                + "frozen_at=" + Instant.now().toString() + "\n"
                + "next=PRODUCTION_ANALYSIS\n";
        Files.write(frozen, body.getBytes(StandardCharsets.UTF_8));
        return requirement;
    }

    /** Stores the answer as explicit P1 for the next Specification turn; it does not freeze. */
    public static Path writeClarificationAnswer(
            Path workspace, String storyId, String answer, String actor) throws IOException {
        if (requireOutcome(workspace, storyId) != Outcome.CLARIFICATION_REQUIRED) {
            throw new StageGateException("Specification has no pending clarification to answer");
        }
        if (Strings.isBlank(answer) || Strings.isBlank(actor)) {
            throw new StageGateException("Specification clarification answer and actor are required");
        }
        Path questions = dir(workspace, storyId).resolve(QUESTIONS_FILE);
        String body = "# Specification Clarification Resolved\n\n"
                + "- actor: " + actor.trim() + "\n"
                + "- at: " + Instant.now().toString() + "\n\n"
                + "## Questions\n\n"
                + new String(Files.readAllBytes(questions), StandardCharsets.UTF_8).trim() + "\n\n"
                + "## Answer\n\n" + answer.trim() + "\n";
        Path resolved = dir(workspace, storyId).resolve("clarification.resolved.md");
        Files.write(resolved, body.getBytes(StandardCharsets.UTF_8));
        return resolved;
    }

    private static void requireDevelopable(String text, Path workspace, String storyId) throws IOException {
        Map<String, String> sections = StoryRequirementReader.parseSections(text);
        requireSection(sections, "raw");
        requireSection(sections, "goal");
        requireSection(sections, "in_scope");
        requireSection(sections, "out_of_scope");
        List<String> acceptance = StoryRequirementReader.parseAcceptanceLines(sections.get("acceptance"));
        StoryRequirement requirement = new StoryRequirement(storyId, sections.get("raw"), sections.get("goal"),
                sections.get("in_scope"), sections.get("out_of_scope"), acceptance);
        if (!AcceptanceGate.validate(requirement).isEmpty()) {
            throw new StageGateException("candidate requirement has no usable Acceptance criteria");
        }
        List<String> present = RequirementAttachmentSlot.listPresent(workspace, storyId);
        List<String> declared = RequirementAttachmentSlot.parseDeclared(text);
        if (!present.isEmpty() && !declared.containsAll(present)) {
            throw new StageGateException("candidate requirement must declare every captured attachment: " + present);
        }
    }

    private static void requireEvidenceQuestions(String text) {
        if (Strings.isBlank(text) || !text.matches("(?s).*##\\s*Q\\d+.*")) {
            throw new StageGateException(
                    "Specification CLARIFICATION_REQUIRED requires one or more ## Q<n> questions");
        }
        String[] questions = text.split("(?m)(?=^##\\s*Q\\d+)");
        int count = 0;
        for (String question : questions) {
            if (!question.matches("(?s)^##\\s*Q\\d+.*")) {
                continue;
            }
            count++;
            String lower = question.toLowerCase(java.util.Locale.ROOT);
            boolean hasEvidenceHeading = lower.contains("代码证据") || lower.contains("evidence")
                    || lower.contains("source evidence");
            if (!hasEvidenceHeading || !REPOSITORY_PATH.matcher(question).find()) {
                throw new StageGateException(
                        "Specification question must include code evidence with a repository-relative path");
            }
        }
        if (count == 0) {
            throw new StageGateException(
                    "Specification CLARIFICATION_REQUIRED requires one or more ## Q<n> questions");
        }
    }

    private static void requireSection(Map<String, String> sections, String key) {
        if (Strings.isBlank(sections.get(key))) {
            throw new StageGateException("candidate requirement missing non-empty ## " + key);
        }
    }

    private static String property(String text, String key) {
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith(key + "=")) {
                return t.substring(key.length() + 1).trim().toUpperCase(java.util.Locale.ROOT);
            }
        }
        return "";
    }

    public enum Outcome {
        CANDIDATE,
        CLARIFICATION_REQUIRED
    }
}
