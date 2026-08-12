package com.ai4se.orchestration.production;

import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.pathway.PathwayRunner.AssumablePolicy;
import com.ai4se.orchestration.pathway.PathwayRunner.DeliveryMode;
import com.ai4se.orchestration.pathway.PathwayRunner.LifecycleMode;
import com.ai4se.orchestration.pathway.PathwayRunner.PathwayResult;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.verification.VerificationEntries;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Strict production facade — sole assembler for customer Story runs.
 *
 * <p>Always injects the same real {@link CursorCliAdapter} for Analysis / Planning / Development /
 * Review. Refuses Functional adapters, DevMutation, review fixtures, and seeded V4 knobs.
 */
public final class ProductionPathway {

    private static final Pattern ACCEPTANCE_BULLET =
            Pattern.compile("(?m)^\\s*[-*]\\s+\\S+");

    private ProductionPathway() {
    }

    public static ProductionRunResult run(ProductionRunRequest request, ProcessInvoker invoker)
            throws IOException {
        return run(request, invoker, new CursorCliAdapter());
    }

    /**
     * Test / override hook: {@code cursor} must still be a {@link CursorCliAdapter} instance
     * (never Functional).
     */
    public static ProductionRunResult run(
            ProductionRunRequest request, ProcessInvoker invoker, CursorCliAdapter cursor)
            throws IOException {
        if (request == null) {
            throw new StageGateException("ProductionRunRequest required");
        }
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required");
        }
        if (cursor == null) {
            throw new StageGateException("CursorCliAdapter required");
        }
        PathwayRunner.Config config = buildStrictConfig(request, cursor);
        validateWorkspaceGates(config.workspace, request, invoker);
        PathwayResult pathway = PathwayRunner.run(config, invoker);
        return ProductionRunResult.fromPathway(pathway, request.maxDevelopmentRounds);
    }

    /** Visible for strict-config unit tests — does not run the pathway. */
    public static PathwayRunner.Config buildStrictConfig(
            ProductionRunRequest request, CursorCliAdapter cursor) {
        if (request == null) {
            throw new StageGateException("ProductionRunRequest required");
        }
        if (cursor == null) {
            throw new StageGateException("CursorCliAdapter required");
        }
        requireCursorOnly(cursor, "analysis/planning/development/review");

        Path workspace = request.workspace.toAbsolutePath().normalize();
        List<String> writeScope = OperatorWriteScope.normalizeAndValidate(request.writeScope);

        PathwayRunner.Config.Builder b = PathwayRunner.Config.builder(workspace, request.storyId)
                .script(Script.V3)
                .suite("B")
                .analysisAdapter(cursor)
                .planAdapter(cursor)
                .devAdapter(cursor)
                .reviewAdapter(cursor)
                .assumablePolicy(AssumablePolicy.REQUIRE_ACK)
                .deliveryMode(DeliveryMode.LOCAL_COMMIT)
                .lifecycleMode(LifecycleMode.SKIP)
                .approvalMode(PathwayRunner.ApprovalMode.LOW_RISK_AUTO)
                .planHumanOwned(true)
                .planApprover("operator-write-scope")
                .approvalNote("production: Plan Allowed ⊆ operator writeScope")
                .verifyFromEntriesOnly()
                .commitMessage("ai4se(production): " + request.storyId)
                .adapterTimeout(request.adapterTimeout)
                .roleModels(request.roleModels);

        for (String scope : writeScope) {
            b.allowedFile(scope);
        }
        if (request.seedRequirement != null) {
            b.seedPath(request.seedRequirement.toAbsolutePath().normalize());
        }

        PathwayRunner.Config config = b.build();
        assertStrict(config);
        return config;
    }

    static void assertStrict(PathwayRunner.Config config) {
        if (config.devMutation != null) {
            throw new StageGateException("ProductionPathway refuses DevMutation");
        }
        if (config.allowReviewFixture) {
            throw new StageGateException("ProductionPathway refuses allowReviewFixture");
        }
        if (config.lifecycleMode != LifecycleMode.SKIP) {
            throw new StageGateException("ProductionPathway requires LifecycleMode.SKIP");
        }
        if (config.deliveryMode != DeliveryMode.LOCAL_COMMIT) {
            throw new StageGateException("ProductionPathway requires LOCAL_COMMIT");
        }
        if (config.assumablePolicy != AssumablePolicy.REQUIRE_ACK) {
            throw new StageGateException("ProductionPathway requires AssumablePolicy.REQUIRE_ACK");
        }
        requireAllRoleAdapters(config);
        requireCursorOnly(config.analysisAdapter, "analysis");
        requireCursorOnly(config.planAdapter, "planning");
        requireCursorOnly(config.devAdapter, "development");
        requireCursorOnly(config.reviewAdapter, "review");
    }

    static void requireAllRoleAdapters(PathwayRunner.Config config) {
        if (config.analysisAdapter == null
                || config.planAdapter == null
                || config.devAdapter == null
                || config.reviewAdapter == null) {
            throw new StageGateException(
                    "ProductionPathway requires Analysis, Planning, Development, and Review adapters");
        }
    }

    static void requireCursorOnly(ModelCliAdapter adapter, String role) {
        if (adapter == null) {
            throw new StageGateException("ProductionPathway missing adapter for " + role);
        }
        if (adapter instanceof FunctionalModelCliAdapter) {
            throw new StageGateException(
                    "ProductionPathway refuses FunctionalModelCliAdapter for " + role);
        }
        if (!(adapter instanceof CursorCliAdapter)) {
            throw new StageGateException(
                    "ProductionPathway requires CursorCliAdapter for " + role
                            + ", got " + adapter.getClass().getName());
        }
    }

    static void validateWorkspaceGates(
            Path workspace, ProductionRunRequest request, ProcessInvoker invoker) throws IOException {
        if (!Files.isDirectory(workspace)) {
            throw new StageGateException("workspace not found: " + workspace);
        }
        requireGitHead(workspace, invoker);

        List<String> dirty = WorkspaceGit.productionCleanGateDirtyPaths(workspace, invoker);
        if (!dirty.isEmpty()) {
            throw new StageGateException(
                    "Refuse dirty worktree — commit or stash changes first "
                            + "(including .ai4se/.story control files; only build output ignored): "
                            + dirty);
        }

        Path entries = workspace.resolve(".ai4se/repository/entries.yaml");
        if (!Files.isRegularFile(entries)) {
            throw new StageGateException("Missing .ai4se/repository/entries.yaml");
        }
        List<String> tests = VerificationEntries.readUsableTestCommands(workspace);
        if (tests.isEmpty()) {
            throw new StageGateException("No usable test entries in entries.yaml");
        }

        Path storyReq = workspace.resolve(".story").resolve(request.storyId).resolve("requirement.md");
        if (Files.isRegularFile(storyReq)) {
            requireJudgableAcceptance(storyReq);
        } else if (request.seedRequirement != null) {
            if (!Files.isRegularFile(request.seedRequirement)) {
                throw new StageGateException(
                        "seedRequirement not found: " + request.seedRequirement);
            }
            requireJudgableAcceptance(request.seedRequirement);
        } else {
            throw new StageGateException(
                    "Story not open and seedRequirement missing: " + request.storyId);
        }
    }

    private static void requireGitHead(Path workspace, ProcessInvoker invoker) throws IOException {
        try {
            WorkspaceGit.headSha(workspace, invoker);
        } catch (StageGateException e) {
            throw new StageGateException(
                    "workspace must be a Git repository with at least one commit: " + e.getMessage());
        }
    }

    static void requireJudgableAcceptance(Path requirementMd) throws IOException {
        String text = new String(Files.readAllBytes(requirementMd), StandardCharsets.UTF_8);
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("## acceptance");
        if (idx < 0) {
            throw new StageGateException(
                    "requirement must include ## acceptance with judgable criteria: "
                            + requirementMd);
        }
        String section = text.substring(idx);
        int next = section.indexOf('\n', 1);
        String body = next < 0 ? "" : section.substring(next + 1);
        int nextH2 = body.indexOf("\n## ");
        if (nextH2 >= 0) {
            body = body.substring(0, nextH2);
        }
        if (!ACCEPTANCE_BULLET.matcher(body).find()) {
            throw new StageGateException(
                    "requirement ## acceptance must contain at least one concrete bullet: "
                            + requirementMd);
        }
        String trimmedBody = body.trim().toLowerCase(Locale.ROOT);
        if (trimmedBody.contains("可检验的通过条件") && !trimmedBody.contains("- ")
                && !trimmedBody.contains("* ")) {
            throw new StageGateException("requirement acceptance is still a template placeholder");
        }
        // Soft refuse common seed template when it is the only bullet content.
        if (trimmedBody.replaceAll("\\s+", "").contains("-（可检验的通过条件）")
                || trimmedBody.replaceAll("\\s+", "").contains("-(可检验的通过条件)")) {
            throw new StageGateException("requirement acceptance is still a template placeholder");
        }
    }
}
