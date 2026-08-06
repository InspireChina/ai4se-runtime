package com.ai4se.orchestration.review;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** W8 thin Review — does not replace Verification. */
public final class ReviewRecords {

    public static final String FILE = "review-result.md";
    public static final String SOURCE_FIXTURE = "fixture";
    public static final String SOURCE_ADAPTER = "adapter";
    public static final String SOURCE_HUMAN = "human";

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
        if (Strings.isBlank(decision)) {
            throw new StageGateException("Review decision required (通过/附条件/驳回)");
        }
        String d = decision.trim();
        if (!d.contains("通过") && !d.toLowerCase().contains("pass")
                && !d.contains("附条件") && !d.contains("驳回")
                && !d.toLowerCase().contains("reject") && !d.toLowerCase().contains("conditional")) {
            throw new StageGateException("Review decision must be 通过/附条件/驳回 (or pass/reject/conditional)");
        }
        String source = Strings.isBlank(reviewSource) ? SOURCE_FIXTURE : reviewSource.trim();
        Path dir = reviewDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Review Result\n\n"
                + "- decision: " + d + "\n"
                + "- residual_risk: "
                + (Strings.isBlank(residualRisk) ? "(none noted)" : residualRisk.trim()) + "\n"
                + "- review_source: " + source + "\n"
                + "- note: Review does not re-run full Verification\n";
        Files.write(dir.resolve(FILE), body.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasResult(Path workspace, String storyId) {
        return Files.isRegularFile(reviewDir(workspace, storyId).resolve(FILE));
    }

    public static void requirePresent(Path workspace, String storyId) {
        if (!hasResult(workspace, storyId)) {
            throw new StageGateException("Missing Review Result before Delivery");
        }
    }

    public static boolean isRejected(Path workspace, String storyId) throws IOException {
        Path path = reviewDir(workspace, storyId).resolve(FILE);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return text.contains("驳回") || text.toLowerCase().contains("reject");
    }
}
