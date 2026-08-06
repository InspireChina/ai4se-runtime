package com.ai4se.orchestration.review;

import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.cursor.PackageAdapterSubmission;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Review hang-in: Review Package → Adapter once → structured ReviewRecords (source=adapter).
 * Adapter must not hint next stage / retry.
 */
public final class ReviewAdapterExecution {

    public static final String AUDIT_DIR = "execution";
    public static final String AUDIT_FILE = "adapter-review.md";

    private static final Pattern DECISION = Pattern.compile(
            "(?im)^\\s*-?\\s*decision\\s*[:=]\\s*(.+?)\\s*$");
    private static final Pattern RESIDUAL = Pattern.compile(
            "(?im)^\\s*-?\\s*residual_risk\\s*[:=]\\s*(.+?)\\s*$");

    private ReviewAdapterExecution() {
    }

    public static AdapterResult submitReviewPackage(
            Path workspace,
            String storyId,
            ModelCliAdapter adapter,
            Duration timeout) throws IOException {
        return submitReviewPackage(workspace, storyId, adapter, timeout, null);
    }

    public static AdapterResult submitReviewPackage(
            Path workspace,
            String storyId,
            ModelCliAdapter adapter,
            Duration timeout,
            com.ai4se.execution.model.RoleModelConfig roleModels) throws IOException {
        if (adapter == null) {
            throw new StageGateException("Review Adapter required");
        }
        ContextPackageResult pkg = ReviewPackageBuilder.build(workspace, storyId);

        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("AI4SE_STORY_ID", storyId);
        env.put("AI4SE_ROLE", ReviewPackageBuilder.ROLE);
        // Acceptance-model alias: if only acceptance is configured, Review still receives it via resolve().
        AdapterResult result = PackageAdapterSubmission.submit(
                adapter, workspace, pkg, timeout == null ? Duration.ofMinutes(10) : timeout, env, roleModels);

        writeAudit(workspace, storyId, adapter.name(), pkg.packageDir(), result);

        if (result.hasNextStageHint()) {
            throw new StageGateException(
                    "Adapter must not decide next stage/retry — got control hints in details");
        }
        if (!result.success()) {
            throw new StageGateException(
                    "Review Adapter failed (no Adapter retry; Control owns recovery): "
                            + (Strings.isBlank(result.message())
                            ? ("exit=" + result.exitCode())
                            : result.message()));
        }

        if (!ReviewRecords.hasResult(workspace, storyId)) {
            ParsedReview parsed = parseFromAdapterText(result);
            if (parsed == null || Strings.isBlank(parsed.decision)) {
                throw new StageGateException(
                        "Review Adapter must write review-result.md or emit decision:/residual_risk:");
            }
            ReviewRecords.write(
                    workspace, storyId, parsed.decision, parsed.residualRisk, ReviewRecords.SOURCE_ADAPTER);
        } else {
            ensureAdapterSource(workspace, storyId);
        }
        ReviewRecords.requirePresent(workspace, storyId);
        return result;
    }

    private static void ensureAdapterSource(Path workspace, String storyId) throws IOException {
        Path path = ReviewRecords.reviewDir(workspace, storyId).resolve(ReviewRecords.FILE);
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (!text.contains("review_source: " + ReviewRecords.SOURCE_ADAPTER)
                && !text.contains("review_source: " + ReviewRecords.SOURCE_HUMAN)) {
            // Adapter wrote body without source — stamp adapter honestly.
            String decision = extractField(text, "decision");
            String residual = extractField(text, "residual_risk");
            if (Strings.isBlank(decision)) {
                throw new StageGateException("Review result missing decision");
            }
            ReviewRecords.write(
                    workspace, storyId, decision, residual, ReviewRecords.SOURCE_ADAPTER);
        }
    }

    private static String extractField(String text, String key) {
        Pattern p = Pattern.compile("(?im)^\\s*-?\\s*" + key + "\\s*[:=]\\s*(.+?)\\s*$");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    static ParsedReview parseFromAdapterText(AdapterResult result) {
        String blob = ""
                + (result.message() == null ? "" : result.message()) + "\n"
                + (result.stdout() == null ? "" : result.stdout()) + "\n"
                + (result.stderr() == null ? "" : result.stderr());
        Matcher d = DECISION.matcher(blob);
        if (!d.find()) {
            // bare keywords
            String lower = blob.toLowerCase(Locale.ROOT);
            if (blob.contains("驳回") || lower.contains("reject")) {
                return new ParsedReview("驳回", extractResidual(blob));
            }
            if (blob.contains("附条件") || lower.contains("conditional")) {
                return new ParsedReview("附条件", extractResidual(blob));
            }
            if (blob.contains("通过") || lower.contains("pass")) {
                return new ParsedReview("通过", extractResidual(blob));
            }
            return null;
        }
        return new ParsedReview(d.group(1).trim(), extractResidual(blob));
    }

    private static String extractResidual(String blob) {
        Matcher r = RESIDUAL.matcher(blob);
        return r.find() ? r.group(1).trim() : "";
    }

    private static void writeAudit(
            Path workspace,
            String storyId,
            String adapterName,
            Path packageDir,
            AdapterResult result) throws IOException {
        Path dir = workspace.resolve(".story").resolve(storyId).resolve(AUDIT_DIR);
        Files.createDirectories(dir);
        Path path = dir.resolve(AUDIT_FILE);
        String body = ""
                + "# Adapter Review submission\n\n"
                + "- adapter: " + adapterName + "\n"
                + "- role: " + ReviewPackageBuilder.ROLE + "\n"
                + "- package: " + packageDir + "\n"
                + "- success: " + result.success() + "\n"
                + "- exit_code: " + result.exitCode() + "\n"
                + "- message: " + result.message() + "\n"
                + "- control_hints: " + result.hasNextStageHint() + "\n"
                + "- submitted_once: true\n"
                + "- requires_review_result: true\n";
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
    }

    static final class ParsedReview {
        final String decision;
        final String residualRisk;

        ParsedReview(String decision, String residualRisk) {
            this.decision = decision;
            this.residualRisk = residualRisk;
        }
    }
}
