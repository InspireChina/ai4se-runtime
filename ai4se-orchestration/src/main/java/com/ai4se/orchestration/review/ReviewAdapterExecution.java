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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Review hang-in: Review Package → Adapter once → Control-normalized ReviewRecords.
 * Adapter must not hint next stage / retry. Control always stamps {@code review_source}.
 * Unparseable Review output is {@code FAILED_ADAPTER}, not a silent PASS.
 */
public final class ReviewAdapterExecution {

    public static final String AUDIT_DIR = "execution";
    public static final String AUDIT_FILE = "adapter-review.md";

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
        AdapterResult result = PackageAdapterSubmission.submit(
                adapter, workspace, pkg, timeout == null ? Duration.ofMinutes(10) : timeout, env, roleModels);

        writeAudit(workspace, storyId, adapter.name(), pkg.packageDir(), result);

        if (result.hasNextStageHint()) {
            throw new StageGateException(
                    "Adapter must not decide next stage/retry — got control hints in details");
        }
        if (!result.success()) {
            throw new StageGateException(
                    "FAILED_ADAPTER: Review Adapter failed (no Adapter retry; Control owns recovery): "
                            + (Strings.isBlank(result.message())
                            ? ("exit=" + result.exitCode())
                            : result.message()));
        }

        normalizeAdapterReview(workspace, storyId, result);
        ReviewRecords.requirePresent(workspace, storyId);
        return result;
    }

    /**
     * Prefer machine sidecar the adapter may have written; else parse Markdown / stdout;
     * Control always rewrites the properties sidecar with {@link ReviewRecords#SOURCE_ADAPTER}.
     */
    static void normalizeAdapterReview(Path workspace, String storyId, AdapterResult result)
            throws IOException {
        Path dir = ReviewRecords.reviewDir(workspace, storyId);
        Path propsPath = dir.resolve(ReviewRecords.PROPERTIES_FILE);
        Path mdPath = dir.resolve(ReviewRecords.FILE);

        String decisionRaw = null;
        String residual = "";

        if (Files.isRegularFile(propsPath)) {
            Map<String, String> props = ReviewRecords.readPropertiesFile(propsPath);
            decisionRaw = props.get("decision");
            residual = props.get("residual_risk") == null ? "" : props.get("residual_risk");
            if (!Strings.isBlank(decisionRaw)) {
                ReviewDecision decision = ReviewDecision.parseStrict(decisionRaw);
                ReviewRecords.writeMachineSidecar(
                        workspace, storyId, decision, residual, ReviewRecords.SOURCE_ADAPTER);
                return;
            }
        }

        if (Files.isRegularFile(mdPath)) {
            String text = new String(Files.readAllBytes(mdPath), StandardCharsets.UTF_8);
            decisionRaw = ReviewRecords.extractDecisionText(text);
            if (Strings.isBlank(residual)) {
                residual = ReviewRecords.extractResidualText(text);
            }
        }

        if (Strings.isBlank(decisionRaw)) {
            ParsedReview parsed = parseFromAdapterText(result);
            if (parsed != null) {
                decisionRaw = parsed.decision;
                if (Strings.isBlank(residual)) {
                    residual = parsed.residualRisk;
                }
            }
        }

        if (Strings.isBlank(decisionRaw)) {
            throw new StageGateException(
                    "FAILED_ADAPTER: Review Adapter must write review-result.properties "
                            + "(decision=PASS|CONDITIONAL|REJECT) or parseable review-result.md "
                            + "with decision: / ## decision");
        }

        ReviewDecision decision;
        try {
            // Markdown / stdout tokens — not sidecar strict (already handled above).
            decision = ReviewDecision.parseMarkdownDecision(decisionRaw);
        } catch (StageGateException e) {
            String msg = e.getMessage() == null ? "invalid Review decision" : e.getMessage();
            if (msg.startsWith("FAILED_ADAPTER:")) {
                throw e;
            }
            throw new StageGateException("FAILED_ADAPTER: " + msg);
        }

        ReviewRecords.writeMachineSidecar(
                workspace, storyId, decision, residual, ReviewRecords.SOURCE_ADAPTER);
    }

    /**
     * Only explicit {@code decision:} / {@code ## decision} in adapter stdout/stderr/message.
     * No bare-keyword inference over Verification PASS prose.
     */
    static ParsedReview parseFromAdapterText(AdapterResult result) {
        String blob = ""
                + (result.message() == null ? "" : result.message()) + "\n"
                + (result.stdout() == null ? "" : result.stdout()) + "\n"
                + (result.stderr() == null ? "" : result.stderr());
        String fromText = ReviewRecords.extractDecisionText(blob);
        if (Strings.isBlank(fromText)) {
            return null;
        }
        return new ParsedReview(fromText, extractResidual(blob));
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
                + "- model: " + result.details().get("model") + "\n"
                + "- role: " + ReviewPackageBuilder.ROLE + "\n"
                + "- package: " + packageDir + "\n"
                + "- success: " + result.success() + "\n"
                + "- exit_code: " + result.exitCode() + "\n"
                + "- message: " + result.message() + "\n"
                + "- control_hints: " + result.hasNextStageHint() + "\n"
                + "- submitted_once: true\n"
                + "- requires_review_result: true\n"
                + "- machine_sidecar: " + ReviewRecords.PROPERTIES_FILE + "\n";
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
